package com.omniplayer

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

private val iptvTeal = Color(0xFF087F79)

@OptIn(ExperimentalMaterial3Api::class)
class IptvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(colorScheme = lightColorScheme(primary = iptvTeal)) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var sources by remember { mutableStateOf(IptvStore.sources(context)) }
                var selectedSource by remember { mutableStateOf(IptvStore.selectedSource(context)?.takeIf { saved -> sources.any { it.name == saved } } ?: sources.firstOrNull()?.name.orEmpty()) }
                var channels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
                var loading by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf("") }
                var group by remember { mutableStateOf("全部") }
                var favoritesOnly by remember { mutableStateOf(false) }
                var favorites by remember { mutableStateOf(IptvStore.favorites(context)) }
                var savedFavorites by remember { mutableStateOf(IptvStore.favoriteChannels(context)) }
                var managingFavorites by remember { mutableStateOf(false) }
                var selectedFavoriteIds by remember { mutableStateOf(emptySet<String>()) }
                var showHistory by remember { mutableStateOf(false) }
                var managingHistory by remember { mutableStateOf(false) }
                var selectedHistoryIds by remember { mutableStateOf(emptySet<String>()) }
                var historyRevision by remember { mutableIntStateOf(0) }
                var query by remember { mutableStateOf("") }
                var speeds by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                var playlistRequest by remember { mutableIntStateOf(0) }
                val lifecycleOwner = LocalLifecycleOwner.current

                fun loadPlaylist(source: IptvSource? = null) {
                    val target = source ?: sources.firstOrNull { it.name == selectedSource } ?: sources.firstOrNull() ?: return
                    loading = true; error = ""
                    channels = emptyList(); speeds = emptyMap()
                    val request = ++playlistRequest
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { fetchM3u(target.url) } }
                            .onSuccess { body ->
                                if (request != playlistRequest) return@onSuccess
                                val parsed = IptvStore.parseM3u(body, target.name)
                                IptvStore.migrateKnownFavorites(context, parsed)
                                channels = parsed; favorites = IptvStore.favorites(context); savedFavorites = IptvStore.favoriteChannels(context)
                                group = "全部"; if (channels.isEmpty()) error = "播放列表中没有可识别的频道"
                            }
                            .onFailure { if (request == playlistRequest) error = "直播源加载失败：${it.localizedMessage ?: "网络错误"}" }
                        if (request == playlistRequest) loading = false
                    }
                }

                LaunchedEffect(selectedSource, sources) {
                    val source = sources.firstOrNull { it.name == selectedSource } ?: sources.firstOrNull()
                    if (source != null) {
                        if (selectedSource != source.name) { selectedSource = source.name; IptvStore.selectSource(context, source.name) }
                        loadPlaylist(source)
                    }
                }
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
                        val latest = IptvStore.sources(context)
                        sources = latest
                        selectedSource = latest.firstOrNull { it.name == IptvStore.selectedSource(context) }?.name ?: latest.firstOrNull()?.name.orEmpty()
                        selectedSource.takeIf(String::isNotBlank)?.let { IptvStore.selectSource(context, it) }
                        favorites = IptvStore.favorites(context); savedFavorites = IptvStore.favoriteChannels(context)
                    } }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                BackHandler { finish() }

                val watchHistory = remember(historyRevision) { IptvStore.watchHistory(context) }
                val channelCatalog = when { showHistory -> watchHistory.map { it.channel }; favoritesOnly -> savedFavorites; else -> channels }
                val groups = remember(channelCatalog) { listOf("全部") + channelCatalog.map { it.group }.distinct().sorted() }
                val visible = remember(channelCatalog, group, query) {
                    channelCatalog.filter { (group == "全部" || it.group == group) && (query.isBlank() || it.name.contains(query, true)) }
                }

                Scaffold(containerColor = DisplaySettings.background(context), topBar = {
                    TopAppBar(
                        title = { Column { Text(if (showHistory) "电视观看历史" else "OmniPlayer · 电视直播", fontWeight = FontWeight.Bold); Text("${channelCatalog.size} 个频道", style = MaterialTheme.typography.labelSmall) } },
                        navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, "返回") } },
                        actions = {
                            IconButton(onClick = { showHistory = !showHistory; managingHistory = false; selectedHistoryIds = emptySet(); group = "全部" }) { Icon(Icons.Default.History, if (showHistory) "返回频道列表" else "观看历史") }
                            IconButton(onClick = { loadPlaylist() }, enabled = !loading) { Icon(Icons.Default.Refresh, "刷新直播源") }
                            IconButton(onClick = { startActivity(Intent(this@IptvActivity, IptvSettingsActivity::class.java)) }) { Icon(Icons.Default.Settings, "直播源设置") }
                        })
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var menu by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = !favoritesOnly && !showHistory) { Text(if (showHistory) "观看历史（跨直播源保存）" else "直播源：${if (favoritesOnly) "全部收藏" else selectedSource}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                DropdownMenu(menu, { menu = false }) { sources.forEach { src -> DropdownMenuItem(text = { Text(src.name) }, onClick = { IptvStore.selectSource(context, src.name); selectedSource = src.name; menu = false }) } }
                            }
                            if (!showHistory) FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text(if (favoritesOnly) "我的收藏" else "收藏") })
                        }
                        if (showHistory) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!managingHistory) OutlinedButton(onClick = { managingHistory = true; selectedHistoryIds = emptySet() }) { Text("管理历史") }
                            else {
                                TextButton(onClick = { selectedHistoryIds = if (selectedHistoryIds.size == watchHistory.size) emptySet() else watchHistory.map { it.channel.id }.toSet() }) { Text(if (selectedHistoryIds.size == watchHistory.size) "取消全选" else "全选") }
                                Button(onClick = { IptvStore.removeWatchHistory(context, selectedHistoryIds); historyRevision++; selectedHistoryIds = emptySet(); managingHistory = false }, enabled = selectedHistoryIds.isNotEmpty()) { Text("删除 (${selectedHistoryIds.size})") }
                                TextButton(onClick = { managingHistory = false; selectedHistoryIds = emptySet() }) { Text("完成") }
                            }
                        }
                        if (favoritesOnly && !showHistory) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!managingFavorites) {
                                OutlinedButton(onClick = { managingFavorites = true; selectedFavoriteIds = emptySet() }) { Text("管理收藏") }
                            } else {
                                TextButton(onClick = {
                                    selectedFavoriteIds = if (selectedFavoriteIds.size == savedFavorites.size) emptySet() else savedFavorites.map { it.id }.toSet()
                                }) { Text(if (selectedFavoriteIds.size == savedFavorites.size) "取消全选" else "全选") }
                                Button(onClick = {
                                    val deleting = savedFavorites.filter { it.id in selectedFavoriteIds }
                                    deleting.forEach { IptvStore.setFavorite(context, it, false) }
                                    favorites = IptvStore.favorites(context)
                                    savedFavorites = IptvStore.favoriteChannels(context)
                                    selectedFavoriteIds = emptySet()
                                    managingFavorites = false
                                }, enabled = selectedFavoriteIds.isNotEmpty()) { Text("删除 (${selectedFavoriteIds.size})") }
                                TextButton(onClick = { managingFavorites = false; selectedFavoriteIds = emptySet() }) { Text("完成") }
                            }
                        }
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (showHistory) "搜索观看记录" else "搜索频道") })
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            groups.forEach { item -> FilterChip(selected = group == item, onClick = { group = item }, label = { Text(item) }) }
                        }
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                        if (!loading && visible.isEmpty() && error.isBlank()) Text(if (showHistory) "还没有观看记录" else if (favoritesOnly) "还没有收藏频道" else "没有匹配的频道", modifier = Modifier.padding(20.dp))
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(visible, key = { it.id }) { channel ->
                                val saved = channel.id in favorites
                                LaunchedEffect(channel.id) {
                                    if (speeds[channel.id] == null) {
                                        speeds = speeds + (channel.id to "测速中…")
                                        val result = withContext(Dispatchers.IO) { measureConnection(channel.url) }
                                        speeds = speeds + (channel.id to result)
                                    }
                                }
                                Card(Modifier.fillMaxWidth().clickable {
                                    if (showHistory && managingHistory) {
                                        selectedHistoryIds = if (channel.id in selectedHistoryIds) selectedHistoryIds - channel.id else selectedHistoryIds + channel.id
                                    } else if (favoritesOnly && managingFavorites) {
                                        selectedFavoriteIds = if (channel.id in selectedFavoriteIds) selectedFavoriteIds - channel.id else selectedFavoriteIds + channel.id
                                    } else {
                                        IptvSession.channels = channelCatalog
                                        startActivity(Intent(this@IptvActivity, IptvPlayerActivity::class.java).putExtra("channel-name", channel.name).putExtra("channel-url", channel.url).putExtra("channel-id", channel.id).putExtra("channel-logo", channel.logo).putExtra("group", channel.group))
                                    }
                                }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (channel.logo.isNotBlank()) AsyncImage(channel.logo, channel.name, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                                        else Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE4F4F2)), contentAlignment = Alignment.Center) { Text(channel.name.take(1), color = iptvTeal, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                                        Column(Modifier.weight(1f)) {
                                            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                            Text(channel.group, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            val historyTime = watchHistory.firstOrNull { it.channel.id == channel.id }?.watchedAt
                                            Text(if (showHistory && historyTime != null) "观看于 ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(historyTime))}" else speeds[channel.id] ?: "连接时长：测速中", style = MaterialTheme.typography.labelSmall, color = if (speeds[channel.id]?.contains("失败") == true) Color(0xFFB3261E) else iptvTeal)
                                        }
                                        if (showHistory && managingHistory) {
                                            Checkbox(checked = channel.id in selectedHistoryIds, onCheckedChange = { checked -> selectedHistoryIds = if (checked) selectedHistoryIds + channel.id else selectedHistoryIds - channel.id })
                                        } else if (!showHistory) TextButton(onClick = {
                                            scope.launch {
                                                speeds = speeds + (channel.id to "测速中…")
                                                val result = withContext(Dispatchers.IO) { measureConnection(channel.url) }
                                                speeds = speeds + (channel.id to result)
                                            }
                                        }) { Text("测速") }
                                        if (favoritesOnly && managingFavorites && !showHistory) {
                                            Checkbox(checked = channel.id in selectedFavoriteIds, onCheckedChange = { checked ->
                                                selectedFavoriteIds = if (checked) selectedFavoriteIds + channel.id else selectedFavoriteIds - channel.id
                                            })
                                        } else if (!showHistory) IconButton(onClick = { IptvStore.setFavorite(context, channel, !saved); favorites = IptvStore.favorites(context); savedFavorites = IptvStore.favoriteChannels(context) }) {
                                            Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (saved) "从收藏中删除" else "收藏", tint = if (saved) Color(0xFFE53935) else Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fetchM3u(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000; connection.readTimeout = 30_000; connection.instanceFollowRedirects = true
    return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
        val out = StringBuilder(); val buffer = CharArray(16 * 1024); var total = 0
        while (true) { val n = reader.read(buffer); if (n < 0) break; total += n; require(total <= 80 * 1024 * 1024) { "播放列表超过 80 MB" }; out.append(buffer, 0, n) }
        out.toString()
    }.also { connection.disconnect() }
}

private fun measureConnection(url: String): String = runCatching {
    val start = System.nanoTime()
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 5_000; c.readTimeout = 5_000; c.requestMethod = "GET"; c.setRequestProperty("Range", "bytes=0-0")
    c.connect(); val code = c.responseCode; c.disconnect()
    val ms = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
    if (code in 200..399) "连接响应：${ms} ms" else "连接失败：HTTP $code"
}.getOrElse { "连接失败：${it.localizedMessage ?: "网络不可达"}" }

class IptvSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(colorScheme = lightColorScheme(primary = iptvTeal)) {
                var entries by remember { mutableStateOf(IptvStore.sources(this).toMutableList()) }
                var message by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, "返回") }
                        Text("电视直播设置", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { entries = (entries + IptvSource("新直播源", "https://")).toMutableList() }) { Text("添加") }
                    }
                    Text("配置多个 M3U / M3U8 播放列表。默认源可直接编辑；保存后直播页可切换来源。", style = MaterialTheme.typography.bodySmall)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        entries.forEachIndexed { index, source ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(source.name, { value -> entries = entries.toMutableList().also { it[index] = it[index].copy(name = value) } }, Modifier.fillMaxWidth(), label = { Text("直播源名称") }, singleLine = true)
                                    OutlinedTextField(source.url, { value -> entries = entries.toMutableList().also { it[index] = it[index].copy(url = value) } }, Modifier.fillMaxWidth(), label = { Text("M3U / M3U8 地址") })
                                    if (entries.size > 1) TextButton(onClick = { entries = entries.toMutableList().also { it.removeAt(index) } }) { Text("删除此直播源") }
                                }
                            }
                        }
                    }
                    Button(onClick = {
                        val valid = entries.filter { it.name.isNotBlank() && (it.url.startsWith("https://") || it.url.startsWith("http://")) }
                        if (valid.isEmpty()) message = "至少需要一个有效的 HTTP(S) 直播源"
                        else { IptvStore.saveSources(this@IptvSettingsActivity, valid); message = "已保存 ${valid.size} 个直播源" }
                    }, Modifier.fillMaxWidth().height(52.dp)) { Text("保存直播源") }
                    Text(message, Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

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
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        }
                    }, update = { it.player = player }, modifier = if (fullScreen) Modifier.fillMaxSize() else Modifier.padding(top = 90.dp).fillMaxWidth().height(240.dp).align(Alignment.TopCenter))
                    if (!fullScreen) Column(Modifier.align(Alignment.TopCenter).padding(top = 342.dp).fillMaxWidth().fillMaxHeight().background(DisplaySettings.background(this@IptvPlayerActivity))) {
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
                    if (!pipMode && (!fullScreen || fullScreenControls)) Row(Modifier.align(Alignment.TopCenter).then(if (fullScreen) Modifier else Modifier.statusBarsPadding().padding(top = 12.dp)).fillMaxWidth().padding(horizontal = 12.dp).shadow(8.dp, RoundedCornerShape(18.dp)).background(Color(0xE6202124), RoundedCornerShape(18.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        title = { Text("选择频道 · ${channelChoices.size}") },
                        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                channelGroups.forEach { item ->
                                    FilterChip(selected = channelGroup == item, onClick = { channelGroup = item }, label = { Text(item) })
                                }
                            }
                            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                                items(filteredChannelChoices, key = { it.id }) { item ->
                                    TextButton(onClick = { currentChannel = item; showChannels = false }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (item.id == currentChannel.id) "▶  ${item.name}" else item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
