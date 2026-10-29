package com.freemangnetplayer

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PlayerActivity : ComponentActivity() {
    private var player by mutableStateOf<ExoPlayer?>(null)
    private var speed by mutableFloatStateOf(1f)
    private var requested by mutableStateOf(false)
    private var autoPlay = true
    private var playerMessage by mutableStateOf("")
    private var savedPosition = 0L
    private var retryAfter = 0L
    private var task: TorrentTask? = null
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        val source = intent.getStringExtra("source") ?: intent.data?.toString().orEmpty()
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "资源解析" }
        val result = runCatching { TorrentTasks.obtain(this, source, title, intent.getStringExtra("mode") == "download") }
        task = result.getOrNull()?.also { it.viewers++ }
        requested = intent.getStringExtra("mode") == "play"
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF087F79), background = Color(0xFFF3F8FA))) {
                val current = task
                var favorite by remember { mutableStateOf(LibraryStore.readFavorites(this).any { it.source == source }) }
                var fileDialog by remember { mutableStateOf(false) }
                var deleteDialog by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { finish() }, modifier = Modifier.height(52.dp)) { Text("返回搜索") }
                        OutlinedButton(onClick = { LibraryStore.addFavorite(this@PlayerActivity, title, source); favorite = true }, modifier = Modifier.height(52.dp)) { Text(if (favorite) "已收藏" else "收藏") }
                    }
                    if (current == null) {
                        Text("任务启动失败：${result.exceptionOrNull()?.localizedMessage}")
                    } else {
                        LaunchedEffect(current) {
                            while (true) {
                                if (requested && player == null && !current.cancelled && !current.paused && !current.queued && !current.moving && current.files.isNotEmpty() && System.currentTimeMillis() > retryAfter) {
                                    val file = current.files.firstOrNull { it.index == current.playingIndex && it.video }
                                        ?: current.files.filter { it.video }.sortedWith(compareByDescending<TaskFile> { it.done >= it.size }.thenByDescending { it.index in current.selected }.thenByDescending { it.size }).firstOrNull()
                                    if (file != null) {
                                        TorrentTasks.playFile(current, file.index)
                                        if (File(current.folder, file.path).exists() && file.done > 0) startPlayback(current, file)
                                    } else playerMessage = "该链接没有可识别的视频文件，可选择文件下载"
                                }
                                if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                                    val rows = JSONArray()
                                    current.files.forEach { rows.put(JSONObject().put("index", it.index).put("path", it.path).put("size", it.size).put("done", it.done).put("selected", it.index in current.selected)) }
                                    File(filesDir, "torrent-check.json").writeText(JSONObject().put("source", source).put("id", current.id)
                                        .put("metadata", current.files.isNotEmpty()).put("peers", current.peers).put("downloaded", current.done).put("total", current.total)
                                        .put("files", rows).put("playing", player?.isPlaying == true).put("position", player?.currentPosition ?: 0)
                                        .put("speed", speed).put("status", current.status).put("folder", current.folder.absolutePath).put("paused", current.paused)
                                        .put("queued", current.queued).put("cancelled", current.cancelled).put("currentFile", current.playingIndex).toString())
                                    val tasks = JSONArray()
                                    TorrentTasks.tasks.values.forEach { tasks.put(JSONObject().put("id", it.id).put("done", it.done).put("total", it.total).put("paused", it.paused).put("queued", it.queued).put("cancelled", it.cancelled).put("folder", it.folder.absolutePath)) }
                                    File(filesDir, "tasks-check.json").writeText(tasks.toString())
                                }
                                delay(500)
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (current.files.isEmpty()) "正在解析磁力资源" else "资源解析完成", style = MaterialTheme.typography.titleMedium)
                                Text(current.status)
                                if (current.files.isEmpty() && !current.paused && !current.cancelled) LinearProgressIndicator(Modifier.fillMaxWidth())
                                else LinearProgressIndicator(progress = { if (current.total == 0L) 0f else (current.done.toFloat() / current.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("${bytes(current.done)} / ${bytes(current.total)} · ${bytes(current.rate.toLong())}/秒 · ${current.peers} 节点", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (current.files.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("真实文件列表（${current.files.size} 项）", Modifier.weight(1f))
                                TextButton(onClick = { fileDialog = true }) { Text("选择文件") }
                            }
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 120.dp)) {
                                items(current.files) { file ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = file.index in current.selected, onCheckedChange = { checked -> TorrentTasks.select(current, if (checked) current.selected + file.index else current.selected - file.index) })
                                        Column(Modifier.weight(1f)) {
                                            Text(file.path, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                            Text("${bytes(file.done)} / ${bytes(file.size)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (file.video) TextButton(onClick = { switchVideo(current, file.index) }) { Text("播放此文件") }
                                    }
                                }
                            }
                        }
                        if (player != null) {
                            AndroidView(factory = { c -> PlayerView(c).apply { useController = true; player = this@PlayerActivity.player } }, update = { it.player = player }, modifier = Modifier.fillMaxWidth().weight(1f))
                        } else {
                            Card(Modifier.fillMaxWidth().weight(1f)) { Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (current.cancelled) "任务已取消" else if (requested) "正在等待可播放缓存…" else "选择视频后播放或下载")
                                Text(playerMessage, style = MaterialTheme.typography.bodySmall)
                            } }
                        }
                        if (player != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { player?.let { it.seekTo((it.currentPosition - 15000).coerceAtLeast(0)) } }, modifier = Modifier.weight(1f)) { Text("后退 15 秒") }
                                OutlinedButton(onClick = { player?.let { it.seekTo(it.currentPosition + 15000) } }, modifier = Modifier.weight(1f)) { Text("前进 15 秒") }
                                OutlinedButton(onClick = { val values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f); speed = values[(values.indexOf(speed) + 1) % values.size]; player?.setPlaybackSpeed(speed) }, modifier = Modifier.weight(1f)) { Text("${speed}×") }
                            }
                        }
                        if (current.files.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { requestPlayback(false) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("预览") }
                            OutlinedButton(onClick = {
                                if (current.selected.isEmpty()) playerMessage = "请至少勾选一个下载文件" else {
                                    releasePlayer(); TorrentTasks.download(current)
                                }
                            }, modifier = Modifier.weight(1f).height(48.dp)) { Text("下载") }
                            Button(onClick = { requestPlayback(true) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("▶ 播放") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { if (current.paused) TorrentTasks.resume(current) else TorrentTasks.pause(current) }, modifier = Modifier.weight(1f).height(48.dp), enabled = !current.cancelled) { Text(if (current.paused) "继续下载" else "暂停下载") }
                            OutlinedButton(onClick = { requested = false; releasePlayer(); TorrentTasks.cancel(current) }, modifier = Modifier.weight(1f).height(48.dp), enabled = !current.cancelled) { Text("取消下载") }
                            OutlinedButton(onClick = { deleteDialog = true }, modifier = Modifier.weight(1f).height(48.dp)) { Text("清空文件") }
                        }
                        Text("后台任务持续运行 · ${current.folder.absolutePath}", maxLines = 2, style = MaterialTheme.typography.labelSmall)
                        if (fileDialog) AlertDialog(onDismissRequest = { fileDialog = false }, title = { Text("选择下载 / 切换视频") }, text = {
                            Column {
                                Row { TextButton(onClick = { TorrentTasks.select(current, current.files.map { it.index }.toSet()) }) { Text("全选") }; TextButton(onClick = { TorrentTasks.select(current, emptySet()) }) { Text("全不选") } }
                                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                                    items(current.files) { file -> Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(file.index in current.selected, { checked -> TorrentTasks.select(current, if (checked) current.selected + file.index else current.selected - file.index) })
                                            Text(file.path, Modifier.weight(1f))
                                        }
                                        if (file.video) TextButton(onClick = { switchVideo(current, file.index); fileDialog = false }) { Text("切换播放：${file.path.substringAfterLast('/')}") }
                                    } }
                                }
                            }
                        }, confirmButton = { TextButton(onClick = { fileDialog = false }) { Text("完成") } })
                        if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("清空当前任务文件？") }, text = { Text("将停止任务并永久删除当前任务的下载 / 缓存文件。") },
                            confirmButton = { TextButton(onClick = { requested = false; releasePlayer(); TorrentTasks.cancel(current, true); deleteDialog = false; finish() }) { Text("确认清空") } }, dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("取消") } })
                    }
                }
            }
        }
    }
    private fun requestPlayback(play: Boolean) {
        autoPlay = play; requested = true
        if (player != null) { if (play) player?.play() else player?.pause() }
    }
    private fun switchVideo(task: TorrentTask, index: Int) {
        releasePlayer(); savedPosition = 0; retryAfter = 0
        TorrentTasks.playFile(task, index); requested = true; autoPlay = true
    }
    private fun startPlayback(task: TorrentTask, file: TaskFile) {
        val media = ExoPlayer.Builder(this)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(1500, 15000, 300, 750).build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(DataSource.Factory { TorrentFileDataSource { task.handle } })).build()
        media.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) { playerMessage = "正在播放：${file.path}"; LibraryStore.addPlayback(this@PlayerActivity, task.title, task.source) }
            }
            override fun onPlayerError(error: PlaybackException) {
                savedPosition = media.currentPosition
                playerMessage = "等待已校验缓存：${error.errorCodeName}"
                retryAfter = System.currentTimeMillis() + 3000
                player = null; media.release()
            }
        })
        media.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(task.folder, file.path))))
        media.seekTo(savedPosition); media.prepare(); media.setPlaybackSpeed(speed); media.playWhenReady = autoPlay
        player = media
    }
    private fun releasePlayer() { player?.release(); player = null }
    override fun onDestroy() {
        releasePlayer(); task?.let { it.viewers = (it.viewers - 1).coerceAtLeast(0); it.lastUsed = System.currentTimeMillis() }
        TorrentTasks.persist()
        super.onDestroy()
    }
    private fun bytes(value: Long): String = when {
        value >= 1024 * 1024 * 1024 -> "%.1f GB".format(value / (1024.0 * 1024 * 1024))
        value >= 1024 * 1024 -> "%.1f MB".format(value / (1024.0 * 1024))
        value >= 1024 -> "%.1f KB".format(value / 1024.0)
        else -> "$value B"
    }
}
