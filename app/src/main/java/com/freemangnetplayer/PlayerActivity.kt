package com.freemangnetplayer

import android.app.PictureInPictureParams
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
    private var fullscreen by mutableStateOf(false)
    private var floating by mutableStateOf(false)
    private var speed by mutableFloatStateOf(1f)
    private var requested by mutableStateOf(false)
    private var autoPlay = true
    private var playerMessage by mutableStateOf("")
    private var savedPosition = 0L
    private var retryAfter = 0L
    private var task: TorrentTask? = null
    private var attached = false
    private var superseded = false
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
        autoPlay = savedInstanceState?.getBoolean("autoplay") ?: true
        savedPosition = savedInstanceState?.getLong("position") ?: 0L
        speed = savedInstanceState?.getFloat("speed") ?: 1f
        fullscreen = savedInstanceState?.getBoolean("fullscreen") ?: false
        updateSystemBars()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF087F79), background = Color(0xFFF3F8FA))) {
                val current = task
                var favorite by remember { mutableStateOf(LibraryStore.readFavorites(this).any { it.source == source }) }
                var fileDialog by remember { mutableStateOf(false) }
                var deleteDialog by remember { mutableStateOf(false) }
                var networkDialog by remember { mutableStateOf(false) }
                BackHandler(enabled = fullscreen && !floating) { changeFullscreen(false) }
                if (current != null) {
                        LaunchedEffect(current) {
                            while (true) {
                                if (attached && requested && player == null && !current.cancelled && !current.paused && !current.queued && !current.moving && current.destination == null && current.files.isNotEmpty() && System.currentTimeMillis() > retryAfter) {
                                    val file = current.files.firstOrNull { it.index == current.playingIndex && it.video }
                                        ?: current.files.filter { it.video }.sortedWith(compareByDescending<TaskFile> { it.done >= it.size }.thenByDescending { it.index in current.selected }.thenByDescending { it.size }).firstOrNull()
                                    if (file != null) {
                                        TorrentTasks.playFile(current, file.index)
                                        runCatching { startPlayback(current, file) }.onFailure { releasePlayer(); playerMessage = "播放器准备失败：${it.localizedMessage}"; retryAfter = System.currentTimeMillis() + 3000 }
                                    } else playerMessage = "该链接没有可识别的视频文件，可选择文件下载"
                                }
                                if (attached && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                                    val rows = JSONArray()
                                    current.files.forEach { rows.put(JSONObject().put("index", it.index).put("path", it.path).put("size", it.size).put("done", it.done).put("selected", it.index in current.selected)) }
                                    File(filesDir, "torrent-check.json").writeText(JSONObject().put("source", current.source).put("id", current.id)
                                        .put("metadata", current.files.isNotEmpty()).put("peers", current.peers).put("downloaded", current.done).put("total", current.total)
                                        .put("files", rows).put("playing", player?.isPlaying == true).put("position", player?.currentPosition ?: 0)
                                        .put("duration", player?.duration ?: 0).put("buffered", player?.bufferedPosition ?: 0).put("playerError", playerMessage).put("retainedCache", current.retainedCache).put("speed", speed).put("status", current.status).put("folder", current.folder.absolutePath).put("paused", current.paused)
                                        .put("payloadRate", current.payloadRate).put("payloadBytes", current.payloadBytes)
                                        .put("rate", current.rate).put("dhtNodes", current.dhtNodes).put("seeds", current.seeds)
                                        .put("downloadLimit", current.downloadLimit).put("uploadLimit", current.uploadLimit)
                                        .put("outgoingUtp", current.outgoingUtp).put("fullscreen", fullscreen).put("floating", floating).put("networkMessage", current.networkMessage)
                                        .put("queued", current.queued).put("cancelled", current.cancelled).put("currentFile", current.playingIndex).toString())
                                    val tasks = JSONArray()
                                    TorrentTasks.tasks.values.forEach { tasks.put(JSONObject().put("id", it.id).put("done", it.done).put("total", it.total).put("paused", it.paused).put("queued", it.queued).put("cancelled", it.cancelled).put("folder", it.folder.absolutePath)) }
                                    File(filesDir, "tasks-check.json").writeText(tasks.toString())
                                }
                                delay(500)
                            }
                        }
                }
                if (fullscreen || floating) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        VideoSurface(Modifier.fillMaxSize(), { fileDialog = true })
                        if (player == null && !floating) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Text(playerMessage.ifBlank { "?????????" }, color = Color.White)
                        }
                    }
                } else {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { finish() }, modifier = Modifier.height(52.dp)) { Text("返回搜索") }
                        OutlinedButton(onClick = { LibraryStore.addFavorite(this@PlayerActivity, title, source); favorite = true }, modifier = Modifier.height(52.dp)) { Text(if (favorite) "已收藏" else "收藏") }
                    }
                    val compact = LocalConfiguration.current.screenHeightDp < 720
                    val bodyScroll = rememberScrollState()
                    Column(Modifier.weight(1f).fillMaxWidth().then(if (compact) Modifier.verticalScroll(bodyScroll) else Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current == null) {
                        Text("任务启动失败：${result.exceptionOrNull()?.localizedMessage}")
                    } else {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (current.files.isEmpty()) "正在解析磁力资源" else "资源解析完成", style = MaterialTheme.typography.titleMedium)
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
                                        if (file.video) TextButton(onClick = { switchVideo(current, file.index) }) { Text("播放此文件") }
                                    }
                                }
                            }
                        }
                        if (player != null) {
                            VideoSurface(Modifier.fillMaxWidth().then(if (compact) Modifier.height(240.dp) else Modifier.weight(1f)), { fileDialog = true })
                        } else {
                            Card(Modifier.fillMaxWidth().then(if (compact) Modifier.height(240.dp) else Modifier.weight(1f))) { Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (current.cancelled) "任务已取消" else if (requested) "正在等待可播放缓存…" else "选择视频后播放或下载")
                                Text(playerMessage, style = MaterialTheme.typography.bodySmall)
                            } }
                        }
                        if (current.files.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { TorrentTasks.retainCache(current); playerMessage = "已保存为缓存，切换资源后继续保留" }, modifier = Modifier.weight(1f).height(48.dp)) { Text(if (current.retainedCache) "已缓存" else "缓存") }
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
                    if (networkDialog) AlertDialog(onDismissRequest = { networkDialog = false }, title = { Text("真实网络诊断") }, text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("应用下载限速：${current.downloadLimit}（0 为不限速）\n应用上传限速：${current.uploadLimit}（0 为不限速）")
                            Text("有效载荷速度：${bytes(current.payloadRate.toLong())}/秒\n含协议总速度：${bytes(current.rate.toLong())}/秒")
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
            if (current.selectionPaused) {
                val file = current.files.firstOrNull { it.index == current.playingIndex && it.video }
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
    private fun startPlayback(task: TorrentTask, file: TaskFile) {
        val media = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(15000).setSeekForwardIncrementMs(15000)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(1500, 15000, 300, 750).build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(DataSource.Factory { TorrentFileDataSource({ task.handle }, { task.engine?.cacheFlushGeneration ?: -1L }, task.nativeLock) })).build()
        media.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) { playerMessage = "正在播放：${file.path}"; LibraryStore.addPlayback(this@PlayerActivity, task.title, task.source) }
            }
            override fun onPlayerError(error: PlaybackException) {
                // Release outside ExoPlayer's callback; stale callbacks must not clear a new player.
                window.decorView.post {
                    if (player === media && !isDestroyed) {
                        savedPosition = media.currentPosition
                        playerMessage = "播放失败：${error.errorCodeName}；可重试或查看网络诊断"
                        retryAfter = System.currentTimeMillis() + 3000
                        releasePlayer()
                    }
                }
            }
        })
        player = media
        media.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(task.folder, file.path))))
        media.seekTo(savedPosition); media.prepare(); media.setPlaybackSpeed(speed); media.playWhenReady = autoPlay
    }
    private fun cycleSpeed() {
        val values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        speed = values[(values.indexOf(speed) + 1) % values.size]
        player?.setPlaybackSpeed(speed)
    }
    private fun changeFullscreen(enabled: Boolean) {
        fullscreen = enabled
        requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            playerMessage = "此设备不支持系统悬浮播放（需要 Android 8.0+ 与画中画支持）"
            return
        }
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (error: Exception) { playerMessage = "无法进入悬浮播放，请检查系统画中画权限：${error.localizedMessage}" }
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        floating = isInPictureInPictureMode
        if (!floating) updateSystemBars()
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true && !isFinishing && !superseded) enterFloating()
    }
    private fun releasePlayer() {
        val old = player ?: return
        player = null
        savedPosition = old.currentPosition
        old.stop()
        old.release()
    }
    @Composable
    private fun VideoSurface(modifier: Modifier, onFiles: () -> Unit) {
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
            }
        }, update = { view ->
            view.player = player
            view.useController = !floating
            view.findViewById<TextView>(R.id.player_speed).text = "${speed}×"
            view.findViewById<View>(R.id.player_fullscreen).contentDescription = if (fullscreen) "退出全屏" else "全屏播放"
        }, onRelease = { view -> view.player = null }, modifier = modifier.background(Color.Black))
    }
    override fun onStart() {
        super.onStart()
        task?.let { if (!attached) { it.viewers++; attached = true }; TorrentTasks.schedule() }
    }
    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode) { super.onStop(); return }
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
