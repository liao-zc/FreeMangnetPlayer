package com.omniplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.Manifest
import android.net.Uri
import android.widget.TextView
import android.view.View
import android.view.WindowManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
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
import org.libtorrent4j.TorrentHandle
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PlayerActivity : ComponentActivity() {
    private var player by mutableStateOf<Player?>(null)
    private var fullscreen by mutableStateOf(false)
    private var floating by mutableStateOf(false)
    private var pipRequested = false
    private var normalOrientation = android.content.res.Configuration.ORIENTATION_PORTRAIT
    private var speed by mutableFloatStateOf(1f)
    private var requested by mutableStateOf(false)
    private var autoPlay = true
    private var playerMessage by mutableStateOf("")
    private var savedPosition = 0L
    private var retryAfter = 0L
    private var task: TorrentTask? = null
    private var activeView: PlayerView? = null
    private var scrubbing = false
    private var resumeAfterScrub = false
    private var attached = false
    private var superseded = false
    private var lastHistoryAt=0L
    private var qualityHeight = 0
    companion object { private var activePlayer = java.lang.ref.WeakReference<PlayerActivity>(null) }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activePlayer.get()?.takeIf { it !== this }?.let { previous ->
            previous.superseded = true; previous.requested = false
            previous.releasePlayer()
            previous.task?.let { if (previous.attached) { it.viewers = (it.viewers - 1).coerceAtLeast(0); previous.attached = false } }
            previous.finish()
        }
        activePlayer = java.lang.ref.WeakReference(this)
        window.setBackgroundDrawableResource(android.R.color.black)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        val source = intent.getStringExtra("source") ?: intent.data?.toString().orEmpty()
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "资源解析" }
        val result = runCatching { TorrentTasks.obtain(this, source, title, intent.getStringExtra("mode") == "download") }
        task = result.getOrNull()
        task?.let { TorrentTasks.releaseOtherTemporaryTasks(it) }
        requested = savedInstanceState?.getBoolean("play-requested") ?: (intent.getStringExtra("mode") == "play")
        if (requested) task?.let { if (it.paused || it.cancelled) TorrentTasks.resume(it) }
        autoPlay = savedInstanceState?.getBoolean("autoplay") ?: true
        savedPosition = savedInstanceState?.getLong("position") ?: 0L
        speed = savedInstanceState?.getFloat("speed") ?: 1f
        fullscreen = savedInstanceState?.getBoolean("fullscreen") ?: false
        updateSystemBars()
        setContent {
            AppTheme(colorScheme = lightColorScheme(primary = Color(0xFF087F79), background = Color(0xFFF3F8FA))) {
                val current = task
                var playbackUiTick by remember(current) { mutableIntStateOf(0) }
                LaunchedEffect(current) { while (true) { playbackUiTick++; delay(500) } }
                val activeFile = remember(current, playbackUiTick) { current?.files?.firstOrNull { it.index == current.playingIndex } }
                var favorite by remember { mutableStateOf(LibraryStore.readFavorites(this).any { it.source == source }) }
                var fileDialog by remember { mutableStateOf(false) }
                var qualityDialog by remember { mutableStateOf(false) }
                var deleteDialog by remember { mutableStateOf(false) }
                var networkDialog by remember { mutableStateOf(false) }
                BackHandler(enabled = fullscreen && !floating) { changeFullscreen(false) }
                if (current != null) {
                        LaunchedEffect(current) {
                            while (true) {
                                if (attached && requested && player == null && !current.cancelled && !current.paused && !current.queued && !current.moving && current.destination == null && current.files.isNotEmpty() && System.currentTimeMillis() > retryAfter) {
                                    val file = current.files.firstOrNull { it.index == current.playingIndex && it.video && it.index !in current.deletedFiles }
                                        ?: current.files.filter { it.video && it.index !in current.deletedFiles }.sortedWith(compareByDescending<TaskFile> { it.index in current.selected }.thenByDescending { it.done >= it.size }.thenByDescending { it.size }).firstOrNull()
                                    if (file != null) {
                                        TorrentTasks.playFile(current, file.index)
                                        runCatching { startPlayback(current, file) }.onFailure { releasePlayer(); playerMessage = "播放器准备失败：${it.localizedMessage}"; retryAfter = System.currentTimeMillis() + 3000 }
                                    } else playerMessage = "该链接没有可识别的视频文件，可选择文件下载"
                                }
                                if (attached && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) { runCatching {
                                    val peerRows = JSONArray()
                                    runCatching {
                                        synchronized(current.nativeLock) { current.handle?.takeIf { it.isValid }?.peerInfo()?.take(16)?.forEach { peer ->
                                            peerRows.put(JSONObject().put("address", peer.ip()).put("client", peer.client())
                                                .put("progressPpm", peer.progressPpm()).put("downloaded", peer.totalDownload())
                                                .put("rate", peer.downSpeed()).put("flags", peer.flags()).put("source", peer.source().toInt()))
                                        } }
                                    }
                                    val rows = JSONArray()
                                    current.files.forEach { rows.put(JSONObject().put("index", it.index).put("path", it.path).put("size", it.size).put("done", it.done).put("selected", it.index in current.selected)) }
                                    val startupPieces = JSONArray()
                                    synchronized(current.nativeLock) {
                                        current.handle?.takeIf { it.isValid }?.let { handle ->
                                            runCatching {
                                                val pieces = handle.status(TorrentHandle.QUERY_PIECES).pieces()
                                                val first = current.streamRangeStart.coerceAtLeast(0)
                                                val inspect = (first..minOf(first + 7, current.streamRangeEnd)).toMutableSet()
                                                if (current.streamRangeEnd >= first) {
                                                    inspect.add(maxOf(first, current.streamRangeEnd - 1))
                                                    inspect.add(current.streamRangeEnd)
                                                }
                                                for (piece in inspect) {
                                                    startupPieces.put(JSONObject().put("index", piece).put("verified", piece < pieces.size() && pieces.getBit(piece)))
                                                }
                                            }
                                        }
                                    }
                                    File(filesDir, "torrent-check.json").writeText(JSONObject().put("source", current.source).put("id", current.id)
                                        .put("metadata", current.files.isNotEmpty()).put("peers", current.peers).put("downloaded", current.done).put("total", current.total)
                                        .put("stage", current.discoveryStage).put("metadataMilliseconds", if (current.metadataAt > 0) current.metadataAt - current.startedAt else -1)
                                        .put("peerMilliseconds", if (current.peerAt > 0) current.peerAt - current.startedAt else -1)
                                        .put("verifiedPieceMilliseconds", if (current.verifiedPieceAt > 0) current.verifiedPieceAt - current.startedAt else -1)
                                        .put("playbackMilliseconds", if (current.playbackAt > 0) current.playbackAt - current.startedAt else -1).put("rebufferCount", current.rebufferCount)
                                        .put("peerEvents", JSONArray(current.engine?.recentPeerEvents() ?: emptyList<String>())).put("selectionEvents", JSONArray(current.recentSelectionChanges())).put("activityInstance", System.identityHashCode(this@PlayerActivity)).put("diskBackend", "POSIX").put("peerDetails", peerRows).put("files", rows).put("decoder", if (player is VlcTorrentPlayer) "LibVLC" else "Media3").put("playing", player?.isPlaying == true).put("position", player?.currentPosition ?: 0)
                                        .put("renderedFrameAt", current.renderedFrameAt).put("playRequested", requested).put("duration", player?.duration ?: 0).put("buffered", player?.bufferedPosition ?: 0).put("playerError", playerMessage).put("retainedCache", current.retainedCache).put("speed", speed).put("status", current.status).put("folder", current.folder.absolutePath).put("paused", current.paused)
                                        .put("payloadRate", current.payloadRate).put("payloadBytes", current.payloadBytes)
                                        .put("rate", current.rate).put("dhtNodes", current.dhtNodes).put("seeds", current.seeds)
                                        .put("downloadLimit", current.downloadLimit).put("uploadLimit", current.uploadLimit)
                                        .put("outgoingUtp", current.outgoingUtp).put("fullscreen", fullscreen).put("floating", floating).put("transportFallbackUsed", current.transportFallbackUsed).put("metadataReplacements", current.engine?.metadataReplacements ?: 0).put("restoredTrackers", current.engine?.restoredTrackers ?: 0).put("networkMessage", current.networkMessage)
                                        .put("streaming", current.streaming).put("sequentialStartupActive", current.sequentialStartupActive).put("streamRangeStart", current.streamRangeStart).put("streamRangeEnd", current.streamRangeEnd).put("startupPieces", startupPieces).put("cacheFlushGeneration", current.engine?.cacheFlushGeneration ?: -1L)
                                        .put("playerClass", player?.javaClass?.simpleName.orEmpty()).put("playerDebug", (player as? VlcTorrentPlayer)?.debugInfo() ?: JSONObject())
                                        .put("videoHeight", player?.videoSize?.height ?: 0).put("qualityHeight", qualityHeight).put("storedQueue", JSONArray().also { a -> StoredVideos.queue(current).forEach { row -> a.put(JSONObject().put("task", row.first.id).put("index", row.second.index).put("done", row.second.done).put("size", row.second.size)) } }).put("queued", current.queued).put("cancelled", current.cancelled).put("currentFile", current.playingIndex).toString())
                                    val tasks = JSONArray()
                                    TorrentTasks.tasks.values.forEach { tasks.put(JSONObject().put("id", it.id).put("done", it.done).put("total", it.total).put("paused", it.paused).put("queued", it.queued).put("cancelled", it.cancelled).put("folder", it.folder.absolutePath)) }
                                    File(filesDir, "tasks-check.json").writeText(tasks.toString())
                                }.onFailure { android.util.Log.w("OmniPlayer", "Unable to update debug state", it) } }
                                refreshProgress()
                                delay(500)
                            }
                        }
                }
                if (fullscreen || floating) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        VideoSurface(Modifier.fillMaxSize(), onQuality = { qualityDialog = true }, onFiles = { fileDialog = true })
                        if ((player == null || current?.renderedFrameAt == 0L) && !floating) Column(Modifier.align(Alignment.Center).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (requested) CircularProgressIndicator(color = Color.White)
                            else TextButton(onClick = { requestPlayback(true) }) { Text("重试播放", color = Color.White) }
                            Text(current?.unplayableReason.orEmpty().ifBlank { playerMessage.ifBlank { "正在等待首个可读分片…" } }, color = if (current?.unplayableReason.orEmpty().isNotBlank()) Color(0xFFFF8A80) else Color.White)
                            activeFile?.let { file ->
                                val fraction = (file.done.toFloat() / file.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                                Text(file.path.substringAfterLast('/'), color = Color.White, maxLines = 1)
                                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                                Text("${bytes(file.done)} / ${bytes(file.size)} · ${current?.peers ?: 0} 个节点 · ${bytes(current?.payloadRate?.toLong() ?: 0L)}/秒", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                Text("时长与分辨率将在首个完整分片可读后显示", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { finish() }, modifier = Modifier.weight(1f).height(52.dp)) { Text("返回搜索") }
                        Button(onClick = {
                            startActivity(Intent(this@PlayerActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open-tab", "门户"))
                            finish()
                        }, modifier = Modifier.weight(1f).height(52.dp)) { Text("返回主页") }
                    }
                    val compact = LocalConfiguration.current.screenHeightDp < 720
                    val bodyScroll = rememberScrollState()
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(bodyScroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current == null) {
                        Text("任务启动失败：${result.exceptionOrNull()?.localizedMessage}")
                    } else {
                        if (player != null) {
                            VideoSurface(Modifier.fillMaxWidth().height(240.dp), onQuality = { qualityDialog = true }, onFiles = { fileDialog = true })
                        } else {
                            Card(Modifier.fillMaxWidth().height(240.dp)) { Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (current.cancelled) "任务已取消" else if (requested) "正在等待首个可读分片…" else "选择视频后播放或下载")
                                Text(playerMessage, style = MaterialTheme.typography.bodySmall)
                            } }
                        }
                        activeFile?.let { file ->
                            val fraction = (file.done.toFloat() / file.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(file.path.substringAfterLast('/'), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                                    Text("${bytes(file.done)} / ${bytes(file.size)}（${"%.1f".format(fraction * 100)}%） · ${current.peers} 个节点 · ${bytes(current.payloadRate.toLong())}/秒 · ${player?.duration?.takeIf { it > 0 }?.let(::formatTime) ?: "等待时长"} · ${player?.videoSize?.height?.takeIf { it > 0 }?.let { "${it}P" } ?: "等待分辨率"}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF444444))
                                }
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (current.files.isEmpty()) "正在解析磁力资源" else "资源解析完成", style = MaterialTheme.typography.titleMedium)
                                if (current.unplayableReason.isNotBlank()) Text(current.unplayableReason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                                Text(current.status)
                                if (current.files.isEmpty() && !current.paused && !current.cancelled) LinearProgressIndicator(Modifier.fillMaxWidth())
                                else LinearProgressIndicator(progress = { if (current.total == 0L) 0f else (current.done.toFloat() / current.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("${bytes(current.done)} / ${bytes(current.total)} · ${bytes(current.payloadRate.toLong())}/秒 · ${current.peers} 节点", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { TorrentTasks.retryDiscovery(current) }) { Text("重试解析 / 连接") }
                                TextButton(onClick = { networkDialog = true }) { Text("网络诊断 · 不限速 · DHT ${current.dhtNodes}") }
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
                                        IconButton(onClick = { copyFileMagnet(current, file) }) { Icon(Icons.Default.ContentCopy, "复制此文件磁力链接") }
                                        if (file.video) TextButton(onClick = { switchVideo(current, file.index) }) { Text("播放此文件") }
                                    }
                                }
                            }
                        }
                        if (current.files.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = {
                                val retained=TorrentTasks.retainCache(current)
                                if(retained && player==null && !requested) requestPlayback(false)
                                playerMessage = if (retained) "已保存为缓存，切换资源后继续保留" else "已达到最大缓存数量，请清理缓存或修改设置"
                                android.widget.Toast.makeText(this@PlayerActivity, playerMessage, android.widget.Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.weight(1f).height(48.dp)) { Text(if (current.retainedCache) "已缓存" else "缓存") }
                            OutlinedButton(onClick = {
                                if (current.selected.isEmpty()) playerMessage = "请至少勾选一个下载文件" else {
                                    releasePlayer(); TorrentTasks.download(current)
                                }
                            }, modifier = Modifier.weight(1f).height(48.dp)) { Text("下载") }
                            if (player == null) Button(onClick = { requestPlayback(true) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("▶ 播放") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { if (current.paused) TorrentTasks.resume(current) else TorrentTasks.pause(current) }, modifier = Modifier.weight(1f).height(48.dp), enabled = !current.cancelled) { Text(if (current.paused) "继续下载" else "暂停下载") }
                            OutlinedButton(onClick = { requested = false; releasePlayer(); TorrentTasks.cancel(current) }, modifier = Modifier.weight(1f).height(48.dp), enabled = !current.cancelled) { Text("取消下载") }
                            OutlinedButton(onClick = { deleteDialog = true }, modifier = Modifier.weight(1f).height(48.dp)) { Text("清空文件") }
                        }
                        Text("${if (current.cancelled) "任务已取消" else if (current.paused) "任务已暂停" else if (current.queued) "任务排队中" else if (current.complete) "文件已校验保存" else "后台任务持续运行"} · ${current.folder.absolutePath}", maxLines = 2, style = MaterialTheme.typography.labelSmall)
                        if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("清空当前任务文件？") }, text = { Text("将停止任务并永久删除当前任务的下载 / 缓存文件。") },
                            confirmButton = { TextButton(onClick = { requested = false; releasePlayer(); TorrentTasks.cancel(current, true); deleteDialog = false; finish() }) { Text("确认清空") } }, dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("取消") } })
                    }
                    }
                }
                }
                if (current != null) {
                        if (fileDialog) AlertDialog(onDismissRequest = { fileDialog = false }, title = { Text("选择下载 / 切换视频") }, text = {
                            Column {
                                Text("已缓存 / 下载的视频（含未完成）")
                                LazyColumn(Modifier.heightIn(max = 140.dp)) {
                                    items(StoredVideos.queue(current), key = { it.first.id + ":" + it.second.index }) { row ->
                                        TextButton(onClick = { openStored(row.first, row.second); fileDialog = false }) { Text(row.second.path.substringAfterLast('/')) }
                                    }
                                }
                                Text("当前链接文件")
                                Row { TextButton(onClick = { TorrentTasks.select(current, current.files.map { it.index }.toSet()) }) { Text("全选") }; TextButton(onClick = { TorrentTasks.select(current, emptySet()) }) { Text("全不选") } }
                                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                                    items(current.files.filter { it.index !in current.deletedFiles }) { file -> Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(file.index in current.selected, { checked -> TorrentTasks.select(current, if (checked) current.selected + file.index else current.selected - file.index) })
                                            Text(file.path, Modifier.weight(1f))
                                        }
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(file.path.substringAfterLast('/'), Modifier.weight(1f), maxLines = 1)
                                            IconButton(onClick = { copyFileMagnet(current, file) }) { Icon(Icons.Default.ContentCopy, "复制此文件磁力链接") }
                                            if (file.video) IconButton(onClick = { switchVideo(current, file.index); fileDialog = false }) { Icon(Icons.Default.PlayArrow, "切换播放：${file.path.substringAfterLast('/')}") }
                                        }
                                    } }
                                }
                            }
                        }, confirmButton = { TextButton(onClick = { fileDialog = false }) { Text("完成") } })
                    if (qualityDialog) AlertDialog(onDismissRequest = { qualityDialog = false }, title = { Text("播放画质") }, text = {
                        Column {
                            Text("默认原画质。720p / 480p 仅选择文件中已有的视频轨道；单画质磁力无法靠缩小画面减少下载量。")
                            val availableHeights=listOf(0,720,480).filter { h -> h==0 || (player as? ExoPlayer)?.currentTracks?.groups?.any { group -> group.type==C.TRACK_TYPE_VIDEO && (0 until group.length).any { group.isTrackSupported(it) && group.getTrackFormat(it).height in 1..h } }==true }
                            for (height in availableHeights) TextButton(onClick = { selectQuality(height); qualityDialog = false }) { Text(if (height == 0) "原画质" else "${height}p") }
                        }
                    }, confirmButton = { TextButton(onClick = { qualityDialog = false }) { Text("关闭") } })
                    if (networkDialog) AlertDialog(onDismissRequest = { networkDialog = false }, title = { Text("真实网络诊断") }, text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("应用下载限速：${current.downloadLimit}（0 为不限速）\n应用上传限速：${current.uploadLimit}（0 为不限速）")
                            Text("有效载荷速度：${bytes(current.payloadRate.toLong())}/秒\n含协议总速度：${bytes(current.rate.toLong())}/秒")
                            Text("连接阶段：${current.discoveryStage}")
                            Text("资源节点：${current.peers}，其中种子：${current.seeds}\n连接数：${current.connectionCount}，DHT 节点：${current.dhtNodes}")
                            Text("有效接收：${bytes(current.payloadBytes)}\nTCP / uTP 自动选择：优先 TCP，无有效传输后自动尝试出站 uTP；DHT、Tracker、局域网发现已启用")
                            Text(if (current.peers == 0) "当前没有资源连接。DHT 为 0 时请对照切换 Wi-Fi / 移动网络，检查 VPN 和 UDP 可达性；DHT 正常仍无节点时可能是资源无人做种。"
                                else if (current.payloadRate < 1024) "已建立连接但没有足够有效数据；请用同一链接、同一网络与其他 BT 客户端对照。节点存在不代表节点愿意发送数据。"
                                else "正在接收有效数据。边下边播需要下载速度持续高于视频码率。")
                            if (current.networkMessage.isNotBlank()) Text("最近的 Tracker / 监听事件：${current.networkMessage}")
                            Text(playerMessage)
                        }
                    }, confirmButton = { TextButton(onClick = { networkDialog = false }) { Text("关闭") } })
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("play-requested", requested)
        outState.putBoolean("autoplay", autoPlay)
        outState.putLong("position", player?.currentPosition ?: savedPosition)
        outState.putFloat("speed", speed)
        outState.putBoolean("fullscreen", fullscreen)
        super.onSaveInstanceState(outState)
    }
    private fun requestPlayback(play: Boolean) {
        autoPlay = play; requested = true
        task?.let { current ->
            if (play && (current.paused || current.cancelled)) TorrentTasks.resume(current)
            if (current.selectionPaused) {
                val file = current.files.firstOrNull { it.index == current.playingIndex && it.video && it.index !in current.deletedFiles }
                    ?: current.files.firstOrNull { it.video }
                if (file != null) TorrentTasks.playFile(current, file.index)
            }
        }
        if (player != null) { if (play) player?.play() else player?.pause() }
    }
    private fun switchVideo(task: TorrentTask, index: Int) {
        releasePlayer(); savedPosition = 0; retryAfter = 0
        TorrentTasks.switchStreamFile(task, index); requested = true; autoPlay = true
    }
    private fun startPlayback(task: TorrentTask, file: TaskFile, forceSoftware: Boolean = false) {
        val realMedia = forceSoftware || MediaFormats.useSoftware(file.path)
        val media: Player = if (realMedia) VlcTorrentPlayer(this, task, file) else ExoPlayer.Builder(this)
            .setRenderersFactory(androidx.media3.exoplayer.DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setSeekBackIncrementMs(15000).setSeekForwardIncrementMs(15000)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(1500, 15000, 300, 750).build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(DataSource.Factory { TorrentFileDataSource({ task.handle }, { task.engine?.cacheFlushGeneration ?: -1L }, task.nativeLock) })).build()
        var playedOnce = false
        task.renderedFrameAt = 0L
        media.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (player === media && task.renderedFrameAt == 0L) task.renderedFrameAt = System.currentTimeMillis()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) { playedOnce = true; if (task.playbackAt == 0L) task.playbackAt = System.currentTimeMillis(); playerMessage = "正在播放：${file.path}"; LibraryStore.addPlayback(this@PlayerActivity, file.path.substringAfterLast('/'), StoredVideos.fileSource(task, file)) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player === media && playedOnce && playbackState == Player.STATE_BUFFERING) task.rebufferCount++
            }
            override fun onPlayerError(error: PlaybackException) {
                // Release outside ExoPlayer's callback; stale callbacks must not clear a new player.
                window.decorView.post {
                    if (player === media && !isDestroyed) {
                        savedPosition = media.currentPosition
                        if (media !is VlcTorrentPlayer && error.errorCode in 3000..4999) {
                            releasePlayer()
                            playerMessage = "正在尝试兼容解码：${file.path}"
                            startPlayback(task, file, forceSoftware = true)
                            return@post
                        }
                        if (error.errorCode >= 3000 || media is VlcTorrentPlayer) requested = false
                        playerMessage = "播放失败：${error.errorCodeName}；可重试或查看网络诊断"
                        retryAfter = System.currentTimeMillis() + 3000
                        releasePlayer()
                    }
                }
            }
        })
        player = media
        if (!realMedia) {
            val directory = file.path.substringBeforeLast('/', "")
            val subtitleItems = task.files.filter { candidate ->
                candidate.path.substringBeforeLast('/', "") == directory &&
                    candidate.path.substringAfterLast('.', "").lowercase() in setOf("srt", "vtt")
            }.map { subtitle ->
                val mime = if (subtitle.path.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(task.folder, subtitle.path)))
                    .setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_AUTOSELECT).build()
            }
            val item = MediaItem.Builder().setMediaId(file.path).setUri(Uri.fromFile(File(task.folder, file.path)))
                .setSubtitleConfigurations(subtitleItems).build()
            media.setMediaItem(item)
        }
        if (media is ExoPlayer) media.trackSelectionParameters = media.trackSelectionParameters.buildUpon().setForceHighestSupportedBitrate(true).build()
        qualityHeight = 0
        media.seekTo(savedPosition); media.prepare(); media.setPlaybackSpeed(speed); media.playWhenReady = autoPlay
    }
    private fun openStored(target: TorrentTask, file: TaskFile) {
        val current = task ?: return
        if (target === current) { switchVideo(current, file.index); return }
        startActivity(android.content.Intent(this, PlayerActivity::class.java)
            .putExtra("source", StoredVideos.fileSource(target, file)).putExtra("title", file.path.substringAfterLast('/')).putExtra("mode", "play"))
        finish()
    }
    private fun playNextStored() {
        val current = task ?: return
        val queue = StoredVideos.queue(current)
        val index = queue.indexOfFirst { it.first === current && it.second.index == current.playingIndex }
        val next = queue.getOrNull(index + 1)
        if (next != null) openStored(next.first, next.second)
        else android.widget.Toast.makeText(this, "没有下一个已缓存或下载的视频", android.widget.Toast.LENGTH_SHORT).show()
    }
    private fun toggleFavorite() {
        val t=task ?: return;val f=t.files.firstOrNull { it.index==t.playingIndex }
        val link=f?.let { StoredVideos.fileSource(t,it) } ?: t.source
        val exists=LibraryStore.readFavorites(this).any { it.source==link }
        if(exists) LibraryStore.removeEntries(this,"收藏",setOf(link)) else LibraryStore.addFavorite(this,f?.path?.substringAfterLast('/') ?: t.title,link)
        refreshProgress()
    }
    private fun selectQuality(height: Int) {
        val media = player as? ExoPlayer
        if (height == 0) { qualityHeight = 0; media?.let { it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).clearVideoSizeConstraints().setForceHighestSupportedBitrate(true).build() }; return }
        val groups=media?.currentTracks?.groups.orEmpty().filter { it.type==C.TRACK_TYPE_VIDEO }
        val candidate=groups.flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it } }
            .filter { (group,index) -> val f=group.getTrackFormat(index); f.height in 1..height || (f.height<=0 && f.width in 1..(height*16/9)) }
            .maxByOrNull { (group,index) -> group.getTrackFormat(index).height }
        if(candidate==null || media==null) return
        qualityHeight=height
        media.trackSelectionParameters=media.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setForceHighestSupportedBitrate(false).setMaxVideoSize(height*16/9,height)
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(candidate.first.mediaTrackGroup,listOf(candidate.second))).build()

    }
    private fun copyFileMagnet(current: TorrentTask, file: TaskFile) {
        val extras = current.source.substringAfter('?', "").split('&').filter {
            it.startsWith("tr=", true) || it.startsWith("x.pe=", true) || it.startsWith("ws=", true) || it.startsWith("xs=", true)
        }.distinct().joinToString("") { "&$it" }
        val link = "magnet:?xt=urn:btih:${current.id}&dn=${java.net.URLEncoder.encode(file.path.substringAfterLast('/'), "UTF-8")}&so=${file.index}$extras"
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(file.path.substringAfterLast('/'), link))
        android.widget.Toast.makeText(this, "已复制磁力链接（包含文件选择，需接收方支持）", android.widget.Toast.LENGTH_SHORT).show()
    }
    private fun cycleSpeed() {
        val values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        speed = values[(values.indexOf(speed) + 1) % values.size]
        player?.setPlaybackSpeed(speed)
    }
    private fun changeFullscreen(enabled: Boolean) {
        if (enabled && !fullscreen) normalOrientation = resources.configuration.orientation
        fullscreen = enabled
        requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else if (normalOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        updateSystemBars()
    }
    private fun updateSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else controller.show(WindowInsetsCompat.Type.systemBars())
    }
    private fun enterFloating() {
        if (floating || pipRequested) return
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            playerMessage = "此设备不支持系统悬浮播放（需要 Android 8.0+ 与画中画支持）"
            return
        }
        try {
            pipRequested = true
            pipRequested = enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (error: Exception) { pipRequested = false; playerMessage = "无法进入悬浮播放，请检查系统画中画权限：${error.localizedMessage}" }
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        floating = isInPictureInPictureMode
        pipRequested = false
        if (!floating) updateSystemBars()
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.playWhenReady == true && !isFinishing && !superseded) enterFloating()
    }
    private fun releasePlayer() {
        val old = player ?: return
        player = null
        savedPosition = old.currentPosition
        old.stop()
        old.release()
    }
    @Composable
    private fun VideoSurface(modifier: Modifier, onQuality: () -> Unit = {}, onFiles: () -> Unit) {
        AndroidView(factory = { context ->
            (android.view.LayoutInflater.from(context).inflate(R.layout.player_surface, null) as PlayerView).apply {
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                controllerShowTimeoutMs = 5000
                setShowRewindButton(true); setShowFastForwardButton(true)
                findViewById<View>(R.id.player_fullscreen).setOnClickListener { changeFullscreen(!fullscreen) }
                findViewById<View>(R.id.player_pip).setOnClickListener { enterFloating() }
                findViewById<View>(R.id.player_files).setOnClickListener { onFiles() }
                findViewById<View>(R.id.player_speed).setOnClickListener { cycleSpeed() }
                findViewById<View>(R.id.player_next).setOnClickListener { playNextStored() }
                findViewById<View>(R.id.player_favorite).setOnClickListener { toggleFavorite() }
                findViewById<View>(R.id.player_quality).setOnClickListener { onQuality() }
                findViewById<View>(R.id.player_tracks).setOnClickListener { showTrackMenu() }
                findViewById<androidx.media3.ui.DefaultTimeBar>(R.id.player_progress).addListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
                    override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                        scrubbing = true; resumeAfterScrub = this@PlayerActivity.player?.playWhenReady == true
                        this@PlayerActivity.player?.pause()
                    }
                    override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                        findViewById<TextView>(R.id.player_position).text = formatTime(position)
                    }
                    override fun onScrubStop(timeBar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) {
                        if (!canceled) {
                            this@PlayerActivity.player?.seekTo(position)
                            task?.let { TorrentTasks.prioritizeSeek(it, position, this@PlayerActivity.player?.duration ?: 0L) }
                        }
                        scrubbing = false
                        if (resumeAfterScrub) this@PlayerActivity.player?.play()
                    }
                })
                activeView = this
            }
        }, update = { view ->
            view.player = player
            if (player is VlcTorrentPlayer) view.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = View.GONE
            view.useController = !floating
            view.findViewById<TextView>(R.id.player_speed).text = "${speed}×"
            view.findViewById<View>(R.id.player_fullscreen).contentDescription = if (fullscreen) "退出全屏" else "全屏播放"
        }, onRelease = { view -> if (activeView === view) activeView = null; view.player = null }, modifier = modifier.background(Color.Black))
    }
    private fun formatTime(value: Long): String {
        val seconds = (value.coerceAtLeast(0) / 1000)
        return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%02d:%02d".format(seconds / 60, seconds % 60)
    }
    private fun showTrackMenu() {
        val exo = player as? ExoPlayer
        if (exo == null) {
            android.widget.Toast.makeText(this, "当前播放内核暂不支持轨道选择", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this).setTitle("音轨与字幕")
            .setItems(arrayOf("选择音轨", "选择字幕")) { _, which ->
                showTrackPicker(exo, if (which == 0) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT)
            }.show()
    }
    private fun showTrackPicker(exo: ExoPlayer, type: Int) {
        val choices = mutableListOf<Pair<androidx.media3.common.Tracks.Group, Int>>()
        val labels = mutableListOf(if (type == C.TRACK_TYPE_TEXT) "关闭字幕" else "自动选择")
        exo.currentTracks.groups.filter { it.type == type }.forEach { group ->
            for (i in 0 until group.length) if (group.isTrackSupported(i)) {
                val format = group.getTrackFormat(i)
                val label = format.label?.takeIf(String::isNotBlank) ?: format.language?.takeIf(String::isNotBlank) ?: "轨道 ${choices.size + 1}"
                choices += group to i
                labels += label
            }
        }
        android.app.AlertDialog.Builder(this).setTitle(if (type == C.TRACK_TYPE_TEXT) "字幕轨道" else "音轨")
            .setItems(labels.toTypedArray()) { _, index ->
                val builder = exo.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
                if (index == 0) builder.setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT)
                else {
                    builder.setTrackTypeDisabled(type, false)
                    val (group, track) = choices[index - 1]
                    builder.addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(track)))
                }
                exo.trackSelectionParameters = builder.build()
            }.show()
    }
    private fun refreshProgress() {
        val view=activeView ?: return
        val t=task
        val f=t?.files?.firstOrNull { it.index==t.playingIndex }
        val link=if(t!=null && f!=null) StoredVideos.fileSource(t,f) else t?.source.orEmpty()
        val favorite=LibraryStore.readFavorites(this).any { it.source==link }
        view.findViewById<android.widget.ImageButton>(R.id.player_favorite)?.apply { setImageResource(if(favorite)R.drawable.ic_heart_filled else R.drawable.ic_heart_outline);contentDescription=if(favorite)"取消收藏" else "收藏视频" }
        if(f!=null) {
            MediaFacts.save(this,link,player?.duration ?: 0,player?.videoSize?.height ?: 0)
            val known=MediaFacts.read(this,link)
            val duration=player?.duration?.takeIf{it>0} ?: known.first
            val height=player?.videoSize?.height?.takeIf{it>0} ?: known.second
            val percent=(f.done.toDouble()*100/f.size.coerceAtLeast(1)).coerceIn(0.0,100.0)
            view.findViewById<TextView>(R.id.player_resource_info)?.apply {
                text="${if(duration>0)historyTime(duration) else "读取时长"} · ${if(height>0) "${height}P" else "读取分辨率"} · ${bytes(f.size)} · ${"%.1f".format(percent)}%"
                typeface=DisplaySettings.typeface(this@PlayerActivity)
            }
            view.findViewById<android.widget.ProgressBar>(R.id.player_cache_progress)?.progress=(percent*10).toInt()
            if(player?.isPlaying==true && System.currentTimeMillis()-lastHistoryAt>5000) {lastHistoryAt=System.currentTimeMillis();LibraryStore.addPlayback(this,f.path.substringAfterLast('/'),link,player?.currentPosition ?: 0,duration)}
        }
        for((id,size) in listOf(R.id.player_position to 12f,R.id.player_duration to 12f,R.id.player_speed to 13f,R.id.player_quality to 11f,R.id.player_resource_info to 11f)) view.findViewById<TextView>(id)?.apply { typeface=DisplaySettings.typeface(this@PlayerActivity);setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,size*DisplaySettings.scale(this@PlayerActivity)) }
        val current=player ?: return
        if (scrubbing) return
        val duration = current.duration.takeIf { it > 0 } ?: 0L
        view.findViewById<androidx.media3.ui.DefaultTimeBar>(R.id.player_progress)?.let {
            it.setDuration(duration); it.setPosition(current.currentPosition); it.setBufferedPosition(current.bufferedPosition)
            it.isEnabled = duration > 0
        }
        view.findViewById<TextView>(R.id.player_position)?.text = formatTime(current.currentPosition)
        view.findViewById<TextView>(R.id.player_duration)?.text = formatTime(duration)
    }
    override fun onStart() {
        super.onStart()
        task?.let { if (!attached) { it.viewers++; attached = true }; TorrentTasks.schedule() }
    }
    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 26 && (pipRequested || floating || isInPictureInPictureMode)) { super.onStop(); return }
        releasePlayer()
        task?.let { if (attached) { it.viewers = (it.viewers - 1).coerceAtLeast(0); attached = false } }
        super.onStop()
    }
    override fun onDestroy() {
        releasePlayer(); task?.let { if (attached) { it.viewers = (it.viewers - 1).coerceAtLeast(0); attached = false }; it.lastUsed = System.currentTimeMillis() }
        TorrentTasks.persist()
        if (activePlayer.get() === this) activePlayer.clear()
        super.onDestroy()
    }
    private fun bytes(value: Long): String = when {
        value >= 1024 * 1024 * 1024 -> "%.1f GB".format(value / (1024.0 * 1024 * 1024))
        value >= 1024 * 1024 -> "%.1f MB".format(value / (1024.0 * 1024))
        value >= 1024 -> "%.1f KB".format(value / 1024.0)
        else -> "$value B"
    }
}
