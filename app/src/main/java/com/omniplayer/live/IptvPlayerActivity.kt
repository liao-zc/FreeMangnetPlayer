package com.omniplayer.live
import com.omniplayer.core.*
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView



internal fun measureConnection(url: String): String = runCatching {
    val start = System.nanoTime()
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 5_000; c.readTimeout = 5_000; c.requestMethod = "GET"; c.setRequestProperty("Range", "bytes=0-0")
    c.connect(); val code = c.responseCode; c.disconnect()
    val ms = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
    if (code in 200..399) "连接响应：${ms} ms" else "连接失败：HTTP $code"
}.getOrElse { "连接失败：${it.localizedMessage ?: "网络不可达"}" }

class IptvPlayerActivity : ComponentActivity() {
    private var pipMode by mutableStateOf(false)

    private fun applyFullScreen(enabled: Boolean) {
        val decor = window.decorView
        decor.systemUiVisibility = if (enabled) (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            ) else 0
        requestedOrientation = if (enabled) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !pipMode && requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            applyFullScreen(true)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode = isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = IptvChannel(
            intent.getStringExtra("channel-name").orEmpty(), intent.getStringExtra("channel-url").orEmpty(),
            intent.getStringExtra("group").orEmpty(), intent.getStringExtra("channel-logo").orEmpty(),
            intent.getStringExtra("channel-id").orEmpty()
        )
        setContent {
            AppTheme(colorScheme = darkColorScheme(primary = iptvTeal)) {
                var currentChannel by remember { mutableStateOf(IptvSession.channels.firstOrNull { it.id == initial.id } ?: initial) }
                var player by remember { mutableStateOf<ExoPlayer?>(null) }
                var favorite by remember(currentChannel.id) { mutableStateOf(currentChannel.id in IptvStore.favorites(this@IptvPlayerActivity)) }
                var fullScreen by remember { mutableStateOf(false) }
                var playbackError by remember { mutableStateOf("") }
                var retry by remember { mutableIntStateOf(0) }
                var buffering by remember { mutableStateOf(true) }
                var liveStream by remember { mutableStateOf(false) }
                var showChannels by remember { mutableStateOf(false) }
                var fullScreenControls by remember { mutableStateOf(false) }
                var channelGroup by remember { mutableStateOf("全部") }
                var receiveSpeedBps by remember { mutableLongStateOf(0L) }
                var historyRecordedFor by remember { mutableStateOf("") }
                val channelChoices = IptvSession.channels.ifEmpty { listOf(initial) }
                val channelGroups = remember(channelChoices) { listOf("全部") + channelChoices.map { it.group.ifBlank { "其他" } }.distinct().sorted() }
                val filteredChannelChoices = remember(channelChoices, channelGroup) {
                    channelChoices.filter { channelGroup == "全部" || it.group.ifBlank { "其他" } == channelGroup }
                }

                fun goBack() {
                    if (fullScreen) {
                        fullScreen = false
                        applyFullScreen(false)
                    } else finish()
                }

                BackHandler { goBack() }
                DisposableEffect(currentChannel.url, retry) {
                    val http = DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                    val exo = ExoPlayer.Builder(this@IptvPlayerActivity).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
                    val listener = object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            playbackError = error.localizedMessage ?: "频道流无法播放"
                            buffering = false
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                            liveStream = exo.isCurrentMediaItemLive
                            if (state == Player.STATE_READY) {
                                playbackError = ""
                                if (historyRecordedFor != currentChannel.id) {
                                    IptvStore.recordWatch(this@IptvPlayerActivity, currentChannel)
                                    historyRecordedFor = currentChannel.id
                                }
                            }
                            if (state == Player.STATE_ENDED) {
                                playbackError = "播放源已结束${exo.duration.takeIf { it > 0 }?.let { "（${it / 1000} 秒）" }.orEmpty()}。该地址可能是短片或失效的直播流，请重试或切换同台其他线路。"
                            }
                        }
                    }
                    val analyticsListener = object : AnalyticsListener {
                        override fun onBandwidthEstimate(
                            eventTime: AnalyticsListener.EventTime,
                            totalLoadTimeMs: Int,
                            totalBytesTransferred: Long,
                            bitrateEstimate: Long
                        ) {
                            if (totalLoadTimeMs > 0) {
                                receiveSpeedBps = totalBytesTransferred * 1000L / totalLoadTimeMs
                            }
                        }
                    }
                    exo.addListener(listener)
                    exo.addAnalyticsListener(analyticsListener)
                    player = exo
                    playbackError = ""; buffering = true; liveStream = false; receiveSpeedBps = 0L
                    val item = MediaItem.Builder().setUri(currentChannel.url).apply {
                        if (currentChannel.url.substringBefore('?').substringBefore('#').endsWith(".m3u8", true)) {
                            setMimeType(MimeTypes.APPLICATION_M3U8)
                            setLiveConfiguration(androidx.media3.common.MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(3_000).setMinOffsetMs(1_000).setMaxOffsetMs(12_000).build())
                        }
                    }.build()
                    exo.setMediaItem(item); exo.prepare(); exo.playWhenReady = true
                    onDispose { exo.removeListener(listener); exo.removeAnalyticsListener(analyticsListener); exo.release(); if (player === exo) player = null }
                }
                LaunchedEffect(fullScreen) { applyFullScreen(fullScreen) }
                Box(Modifier.fillMaxSize().background(if (fullScreen) Color.Black else DisplaySettings.background(this@IptvPlayerActivity)).then(
                    if (fullScreen) Modifier.pointerInput(fullScreen) { detectTapGestures(onTap = { fullScreenControls = !fullScreenControls }) } else Modifier
                )) {
                    AndroidView(factory = { context ->
                        PlayerView(context).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        }
                    }, update = { it.player = player }, modifier = if (fullScreen) Modifier.fillMaxSize() else Modifier.padding(top = 90.dp).fillMaxWidth().height(220.dp).align(Alignment.TopCenter))
                    if (!fullScreen) Column(Modifier.align(Alignment.TopCenter).padding(top = 370.dp).fillMaxWidth().fillMaxHeight().background(DisplaySettings.background(this@IptvPlayerActivity))) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("频道列表 · ${channelChoices.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("按分组快速切换", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            channelGroups.forEach { item -> FilterChip(selected = channelGroup == item, onClick = { channelGroup = item }, label = { Text(item, maxLines = 1) }) }
                        }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(filteredChannelChoices, key = { it.id }) { item ->
                                var latency by remember(item.id) { mutableStateOf("连接耗时：测速中") }
                                LaunchedEffect(item.id) { latency = withContext(Dispatchers.IO) { measureConnection(item.url) } }
                                Card(Modifier.fillMaxWidth().clickable { currentChannel = item }, colors = CardDefaults.cardColors(containerColor = if (item.id == currentChannel.id) Color(0xFFE0F2EF) else Color.White), shape = RoundedCornerShape(12.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (item.logo.isNotBlank()) AsyncImage(item.logo, item.name, Modifier.size(38.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Fit)
                                        else Box(Modifier.size(38.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFE4F4F2)), contentAlignment = Alignment.Center) { Text(item.name.take(1), color = iptvTeal, fontWeight = FontWeight.Bold) }
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                            Text("${item.group} · $latency", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = if (latency.contains("失败")) Color(0xFFB3261E) else iptvTeal)
                                        }
                                        if (item.id == currentChannel.id) Text("播放中", style = MaterialTheme.typography.labelSmall, color = iptvTeal)
                                    }
                                }
                            }
                        }
                    }
                    if (playbackError.isNotBlank()) Surface(Modifier.align(Alignment.Center).padding(28.dp), color = Color(0xEE202124), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("此频道暂时无法播放", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(playbackError, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { retry++ }) { Text("重试") }
                        }
                    } else if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    if (!pipMode && (!fullScreen || fullScreenControls)) Row(Modifier.align(if (fullScreen) Alignment.BottomCenter else Alignment.TopCenter).then(if (fullScreen) Modifier.navigationBarsPadding().padding(bottom = 12.dp) else Modifier.padding(top = 312.dp)).fillMaxWidth().padding(horizontal = 12.dp).shadow(8.dp, RoundedCornerShape(18.dp)).background(Color(0xE6202124), RoundedCornerShape(18.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::goBack, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text(currentChannel.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (liveStream) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("直播", color = Color(0xFF67E4B5), style = MaterialTheme.typography.labelSmall)
                                Text("·", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                                Text("${formatNetworkSpeed(receiveSpeedBps)}", color = when {
                                    receiveSpeedBps >= 1_000_000L -> Color(0xFF69E38C)
                                    receiveSpeedBps >= 250_000L -> Color(0xFFFFC857)
                                    else -> Color(0xFFFF6B6B)
                                }, style = MaterialTheme.typography.labelSmall)
                                TextButton(onClick = { player?.seekToDefaultPosition(); player?.play() }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                    Text("回到直播", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        IconButton(onClick = { showChannels = true }, modifier = Modifier.size(56.dp), enabled = channelChoices.size > 1) {
                            Icon(Icons.Default.List, "切换频道", tint = Color.White)
                        }
                        IconButton(onClick = { IptvStore.setFavorite(this@IptvPlayerActivity, currentChannel, !favorite); favorite = !favorite }, modifier = Modifier.size(56.dp)) {
                            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = if (favorite) Color.Red else Color.White)
                        }
                        IconButton(onClick = {
                            fullScreen = !fullScreen
                            fullScreenControls = false
                        }, modifier = Modifier.size(56.dp)) {
                            Icon(if (fullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (fullScreen) "退出全屏" else "全屏", tint = Color.White)
                        }
                        IconButton(onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= 26) {
                                val params = android.app.PictureInPictureParams.Builder()
                                    .setAspectRatio(android.util.Rational(16, 9))
                                    .build()
                                enterPictureInPictureMode(params)
                            }
                        }, modifier = Modifier.size(56.dp), enabled = android.os.Build.VERSION.SDK_INT >= 26) {
                            Icon(Icons.Default.PictureInPictureAlt, "悬浮播放", tint = Color.White)
                        }
                    }
                    if (showChannels) AlertDialog(
                        onDismissRequest = { showChannels = false },
                        containerColor = Color(0xFFF4F7F6),
                        title = { Text("选择频道 · ${channelChoices.size}", color = Color(0xFF14202B)) },
                        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                channelGroups.forEach { item ->
                                    FilterChip(selected = channelGroup == item, onClick = { channelGroup = item }, label = { Text(item) })
                                }
                            }
                            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                                items(filteredChannelChoices, key = { it.id }) { item ->
                                    TextButton(onClick = { currentChannel = item; showChannels = false }, modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                            Text(if (item.id == currentChannel.id) "▶  ${item.name}" else item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF14202B))
                                        }
                                    }
                                }
                            }
                        } },
                        confirmButton = { TextButton(onClick = { showChannels = false }) { Text("关闭") } }
                    )
                }
            }
        }
    }
}

private fun formatNetworkSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_000_000L -> "${"%.1f".format(bytesPerSecond / 1_000_000.0)} MB/s"
    bytesPerSecond >= 1_000L -> "${"%.0f".format(bytesPerSecond / 1_000.0)} KB/s"
    bytesPerSecond > 0L -> "$bytesPerSecond B/s"
    else -> "测速中"
}
