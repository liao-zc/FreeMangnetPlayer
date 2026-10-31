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

internal val iptvTeal = Color(0xFF087F79)

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
                        runCatching { withContext(Dispatchers.IO) { IptvPlaylistRepository.fetch(target.url) } }
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
