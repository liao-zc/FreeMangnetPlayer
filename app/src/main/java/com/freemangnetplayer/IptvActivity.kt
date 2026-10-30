package com.freemangnetplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
                var selectedSource by remember { mutableStateOf(sources.firstOrNull()?.name.orEmpty()) }
                var channels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
                var loading by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf("") }
                var group by remember { mutableStateOf("全部") }
                var favoritesOnly by remember { mutableStateOf(false) }
                var favorites by remember { mutableStateOf(IptvStore.favorites(context)) }
                var query by remember { mutableStateOf("") }
                var speeds by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                val lifecycleOwner = LocalLifecycleOwner.current

                fun loadPlaylist() {
                    val source = sources.firstOrNull { it.name == selectedSource } ?: sources.firstOrNull() ?: return
                    loading = true; error = ""
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { fetchM3u(source.url) } }
                            .onSuccess { body -> channels = IptvStore.parseM3u(body, source.name); group = "全部"; if (channels.isEmpty()) error = "播放列表中没有可识别的频道" }
                            .onFailure { error = "直播源加载失败：${it.localizedMessage ?: "网络错误"}" }
                        loading = false
                    }
                }

                LaunchedEffect(selectedSource) { loadPlaylist() }
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { sources = IptvStore.sources(context); favorites = IptvStore.favorites(context); if (channels.isEmpty()) loadPlaylist() } }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                BackHandler { finish() }

                val groups = remember(channels) { listOf("全部") + channels.map { it.group }.distinct().sorted() }
                val visible = remember(channels, group, favoritesOnly, favorites, query) {
                    channels.filter { (group == "全部" || it.group == group) && (!favoritesOnly || it.id in favorites) && (query.isBlank() || it.name.contains(query, true)) }
                }

                Scaffold(containerColor = Color(0xFFF3F8FA), topBar = {
                    TopAppBar(
                        title = { Column { Text("OmniPlayer · 电视直播", fontWeight = FontWeight.Bold); Text("${channels.size} 个频道", style = MaterialTheme.typography.labelSmall) } },
                        navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, "返回") } },
                        actions = {
                            IconButton(onClick = { loadPlaylist() }, enabled = !loading) { Icon(Icons.Default.Refresh, "刷新直播源") }
                            IconButton(onClick = { startActivity(Intent(this@IptvActivity, IptvSettingsActivity::class.java)) }) { Icon(Icons.Default.Settings, "直播源设置") }
                        })
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var menu by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("直播源：$selectedSource", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                DropdownMenu(menu, { menu = false }) { sources.forEach { src -> DropdownMenuItem(text = { Text(src.name) }, onClick = { selectedSource = src.name; menu = false }) } }
                            }
                            FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text(if (favoritesOnly) "我的收藏" else "收藏") })
                        }
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索频道") })
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            groups.forEach { item -> FilterChip(selected = group == item, onClick = { group = item }, label = { Text(item) }) }
                        }
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                        if (!loading && visible.isEmpty() && error.isBlank()) Text("没有匹配的频道", modifier = Modifier.padding(20.dp))
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
                                Card(Modifier.fillMaxWidth().clickable { startActivity(Intent(this@IptvActivity, IptvPlayerActivity::class.java).putExtra("channel-name", channel.name).putExtra("channel-url", channel.url).putExtra("channel-id", channel.id).putExtra("channel-logo", channel.logo).putExtra("group", channel.group)) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (channel.logo.isNotBlank()) AsyncImage(channel.logo, channel.name, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                                        else Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE4F4F2)), contentAlignment = Alignment.Center) { Text(channel.name.take(1), color = iptvTeal, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                                        Column(Modifier.weight(1f)) {
                                            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                            Text(channel.group, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            Text(speeds[channel.id] ?: "连接速度：点击测速", style = MaterialTheme.typography.labelSmall, color = if (speeds[channel.id]?.contains("失败") == true) Color(0xFFB3261E) else iptvTeal)
                                        }
                                        TextButton(onClick = {
                                            scope.launch {
                                                speeds = speeds + (channel.id to "测速中…")
                                                val result = withContext(Dispatchers.IO) { measureConnection(channel.url) }
                                                speeds = speeds + (channel.id to result)
                                            }
                                        }) { Text("测速") }
                                        IconButton(onClick = { IptvStore.setFavorite(context, channel, !saved); favorites = IptvStore.favorites(context) }) {
                                            Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (saved) "取消收藏" else "收藏", tint = if (saved) Color(0xFFE53935) else Color.Gray)
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
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
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
                        else { IptvStore.saveSources(this, valid); message = "已保存 ${valid.size} 个直播源" }
                    }, Modifier.fillMaxWidth().height(52.dp)) { Text("保存直播源") }
                    Text(message, Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

class IptvPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val channelName = intent.getStringExtra("channel-name").orEmpty()
        val url = intent.getStringExtra("channel-url").orEmpty()
        val channelId = intent.getStringExtra("channel-id").orEmpty()
        setContent {
            AppTheme(colorScheme = darkColorScheme(primary = iptvTeal)) {
                var player by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
                var favorite by remember { mutableStateOf(channelId in IptvStore.favorites(this@IptvPlayerActivity)) }
                var fullScreen by remember { mutableStateOf(false) }
                DisposableEffect(url) {
                    val exo = androidx.media3.exoplayer.ExoPlayer.Builder(this@IptvPlayerActivity).build()
                    exo.setMediaItem(androidx.media3.common.MediaItem.fromUri(url)); exo.prepare(); exo.playWhenReady = true; player = exo
                    onDispose { exo.release(); player = null }
                }
                BackHandler { finish() }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(factory = { context ->
                        androidx.media3.ui.PlayerView(context).apply { useController = true; setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_ALWAYS) }
                    }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
                    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0x66000000)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) }
                        Text(channelName, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { IptvStore.setFavorite(this@IptvPlayerActivity, IptvChannel(channelName, url, intent.getStringExtra("group").orEmpty(), intent.getStringExtra("channel-logo").orEmpty(), channelId), !favorite); favorite = !favorite }) {
                            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = if (favorite) Color.Red else Color.White)
                        }
                        TextButton(onClick = { fullScreen = !fullScreen; requestedOrientation = if (fullScreen) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }) { Text("全屏", color = Color.White) }
                        TextButton(onClick = { if (android.os.Build.VERSION.SDK_INT >= 26) enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build()) }) { Text("悬浮", color = Color.White) }
                    }
                }
            }
        }
    }
}
