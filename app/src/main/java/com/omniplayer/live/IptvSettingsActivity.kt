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



class IptvSettingsActivity : ComponentActivity() {
    private var acceptPickedPlaylist: ((String) -> Unit)? = null
    private val playlistPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val folder = java.io.File(filesDir, "iptv-sources").apply { mkdirs() }
            val target = java.io.File(folder, "playlist-${System.currentTimeMillis()}.m3u")
            contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
            acceptPickedPlaylist?.invoke(target.toURI().toString())
        }.onFailure { android.widget.Toast.makeText(this, "无法导入播放列表：${it.localizedMessage}", android.widget.Toast.LENGTH_LONG).show() }
    }
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
                                    OutlinedButton(onClick = {
                                        acceptPickedPlaylist = { localUrl -> entries = entries.toMutableList().also { it[index] = it[index].copy(url = localUrl) } }
                                        playlistPicker.launch(arrayOf("application/x-mpegURL", "audio/x-mpegurl", "text/plain", "application/octet-stream"))
                                    }, modifier = Modifier.fillMaxWidth()) { Text("选择手机中的 M3U 文件") }
                                    if (entries.size > 1) TextButton(onClick = { entries = entries.toMutableList().also { it.removeAt(index) } }) { Text("删除此直播源") }
                                }
                            }
                        }
                    }
                    Button(onClick = {
                        val valid = entries.filter { it.name.isNotBlank() && (it.url.startsWith("https://") || it.url.startsWith("http://") || it.url.startsWith("file:")) }
                        if (valid.isEmpty()) message = "至少需要一个有效的 HTTP(S) 直播源"
                        else { IptvStore.saveSources(this@IptvSettingsActivity, valid); message = "已保存 ${valid.size} 个直播源" }
                    }, Modifier.fillMaxWidth().height(52.dp)) { Text("保存直播源") }
                    Text(message, Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
