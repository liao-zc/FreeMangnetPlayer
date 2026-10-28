package com.freemangnetplayer

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.swig.settings_pack
import com.masterwok.simpletorrentandroid.TorrentSession
import com.masterwok.simpletorrentandroid.TorrentSessionOptions
import com.masterwok.simpletorrentandroid.contracts.TorrentSessionListener
import com.masterwok.simpletorrentandroid.models.TorrentSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class PlayerActivity : ComponentActivity() {
    private var player by mutableStateOf<ExoPlayer?>(null)
    private var torrentHandle: TorrentHandle? = null
    private data class ResourceFile(val path: String, val bytes: Long)
    private var resourceFiles by mutableStateOf<List<ResourceFile>>(emptyList())
    private var session: TorrentSession? = null
    private var currentUri: Uri? = null
    private var currentSource: String = ""
    private var downloadMode = false
    private var metadataReady by mutableStateOf(false)
    private var previewRequested = false
    private var autoplayRequested = false
    private var playbackAttemptBytes = -1L
    private var connectionTimedOut by mutableStateOf(false)
    private var favoriteSaved by mutableStateOf(false)
    private var playbackActive by mutableStateOf(false)
    private var playbackSpeed by mutableFloatStateOf(1f)
    private var resumeOnReady = false

    private var statusText by mutableStateOf("正在准备磁力任务…")
    private var progressValue by mutableFloatStateOf(0f)
    private var downloadedBytes by mutableLongStateOf(0L)
    private var totalBytes by mutableLongStateOf(0L)
    private var downloadRate by mutableIntStateOf(0)
    private var peerCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSource = intent.getStringExtra("source") ?: intent.data?.toString().orEmpty()
        downloadMode = intent.getStringExtra("mode") == "download"
        resumeOnReady = intent.getStringExtra("mode") == "play"
        val title = intent.getStringExtra("title").orEmpty()

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF087F79), secondary = Color(0xFF344AB8), background = Color(0xFFF3F8FA)
            )) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title.ifBlank { "资源解析" }, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Button(onClick = { finish() }, modifier = Modifier.height(54.dp)) { Text("返回搜索") }
                        Spacer(Modifier.weight(1f))
                        if (metadataReady || playbackActive) {
                            OutlinedButton(
                                onClick = {
                                    LibraryStore.addFavorite(this@PlayerActivity, title.ifBlank { "磁力资源" }, currentSource)
                                    favoriteSaved = true
                                },
                                modifier = Modifier.height(54.dp)
                            ) { Text(if (favoriteSaved) "已收藏" else "收藏") }
                        }
                    }

                    LaunchedEffect(currentSource) {
                        delay(45_000)
                        if (!metadataReady) {
                            connectionTimedOut = true
                            statusText = "45 秒内未取得种子元数据。请确认链接、网络及做种者状态。"
                        }
                    }

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (metadataReady) "资源解析完成" else "正在解析磁力资源", style = MaterialTheme.typography.titleLarge)
                            Text(statusText, style = MaterialTheme.typography.bodyMedium)
                            if (metadataReady) {
                                LinearProgressIndicator(progress = { progressValue.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("${(progressValue * 100).toInt()}%  ·  ${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}")
                            } else if (!connectionTimedOut) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("正在获取元数据，尚无可计算的完成百分比")
                            }
                            Text("${formatBytes(downloadRate.toLong())}/秒  ·  已连接 $peerCount 个节点", style = MaterialTheme.typography.bodySmall)
                            Text(currentSource.take(150), maxLines = 2, style = MaterialTheme.typography.bodySmall, color = Color(0xFF087F79))
                        }
                    }

                    if (resourceFiles.isNotEmpty()) {
                        Text("真实文件列表（${resourceFiles.size} 项；当前缓存最大文件）", style = MaterialTheme.typography.titleSmall)
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 130.dp)) {
                            items(resourceFiles) { file ->
                                Text("${file.path} · ${formatBytes(file.bytes)}", Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (!metadataReady && (connectionTimedOut || statusText.contains("失败") || statusText.contains("无效"))) {
                        Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text(if (connectionTimedOut) "连接未成功" else "资源解析失败", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(10.dp))
                                Text(statusText)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("返回搜索结果") }
                            }
                        }
                    } else if (!metadataReady) {
                        Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text("正在连接种子并读取文件列表…")
                                Spacer(Modifier.height(6.dp))
                                Text("资源信息取决于种子节点是否在线。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else if (player != null) {
                        AndroidView(
                            factory = { context -> PlayerView(context).apply { useController = true; player = this@PlayerActivity.player } },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    } else {
                        Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(18.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFE4F2F1))) {
                            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("▣", style = MaterialTheme.typography.displayLarge, color = Color(0xFF087F79))
                                Spacer(Modifier.height(10.dp))
                                Text(currentUri?.lastPathSegment ?: title.ifBlank { "视频资源已就绪" }, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Text("选择预览、下载或播放", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (player != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { seekBySeconds(-15) }, modifier = Modifier.weight(1f).height(50.dp)) { Text("后退 15 秒") }
                            OutlinedButton(onClick = { seekBySeconds(15) }, modifier = Modifier.weight(1f).height(50.dp)) { Text("前进 15 秒") }
                            OutlinedButton(onClick = { cyclePlaybackSpeed() }, modifier = Modifier.weight(1f).height(50.dp)) { Text("${playbackSpeed}×") }
                        }
                    }

                    if (metadataReady) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { requestPlayback(autoPlay = false) }, modifier = Modifier.weight(1f)) { Text("预览") }
                            OutlinedButton(onClick = {
                                downloadMode = true
                                statusText = "下载与缓存进行中；保持任务页面打开。"
                                LibraryStore.updateDownload(this@PlayerActivity, title.ifBlank { "磁力资源" }, currentSource, statusText, downloadedBytes, totalBytes)
                            }, modifier = Modifier.weight(1f)) { Text("下载") }
                            Button(onClick = { requestPlayback(autoPlay = true) }, modifier = Modifier.weight(1f)) { Text("▶ 播放") }
                        }
                    } else if (downloadMode) {
                        OutlinedButton(onClick = { togglePause() }, modifier = Modifier.fillMaxWidth()) { Text("暂停 / 继续下载") }
                    }
                    if (downloadMode) Text("下载任务在应用内运行；当前版本不支持退出应用后的后台续传。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        startTorrent(currentSource)
    }

    private fun startTorrent(raw: String) {
        val source = normalizeMagnetUri(raw)?.let { normalized ->
            // Bare hashes have no trackers. Keep provided trackers and add bootstrap
            // alternatives so discovery does not depend exclusively on UDP DHT.
            normalized + listOf("udp://tracker.opentrackr.org:1337/announce", "https://tracker.tamersunion.org:443/announce", "udp://open.stealth.si:80/announce")
                .filter { !normalized.contains(java.net.URLEncoder.encode(it, "UTF-8")) }
                .joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") }
        } ?: raw.trim().replace("&amp;", "&", ignoreCase = true)
        val validMagnet = source.startsWith("magnet:?", true) && Regex("(?i)(?:[?&])xt=urn:btih:").containsMatchIn(source)
        val validTorrentUrl = source.startsWith("https://", true) || source.startsWith("http://", true)
        if (!validMagnet && !validTorrentUrl) {
            statusText = "磁力链接无效。请从搜索结果重新选择资源。"
            return
        }

        statusText = if (downloadMode) "正在连接并下载…" else "正在连接种子、获取文件信息…"
        if (downloadMode) LibraryStore.updateDownload(this, intent.getStringExtra("title").orEmpty(), source, statusText)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // SessionManager loads libtorrent JNI. Initialize it off the UI thread so the
                // activity can render progress and errors even when native startup is slow.
                val folder = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "FreeMangnetPlayer").apply { mkdirs() }
                val options = TorrentSessionOptions(
                    downloadLocation = folder,
                    onlyDownloadLargestFile = true,
                    shouldStream = !downloadMode,
                    streamBufferSize = 12,
                    enableLogging = true,
                    // Do not wait forever for a fixed DHT node count before announcing to trackers.
                    // The upstream wrapper blocks inside start() when this threshold is not reached.
                    dhtNodeMinimum = 0
                )
                options.settingsPack.setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                    "router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881,dht.libtorrent.org:25401")
                val engine = TorrentSession(options)
                engine.listener = createTorrentListener()
                withContext(Dispatchers.Main) { session = engine }
                val hash = Regex("(?i)xt=urn:btih:([0-9a-f]{40})").find(source)?.groupValues?.get(1)
                val cache = hash?.let { File(filesDir, "${it.lowercase()}.torrent") }
                val metadata = cache?.takeIf { it.exists() }?.readBytes()
                    ?: if (!raw.contains("&tr=", true) && hash != null) fetchVerifiedMetadata(hash) else null
                if (metadata != null) {
                    cache?.writeBytes(metadata)
                    engine.start(metadata)
                } else engine.start(this@PlayerActivity, Uri.parse(source))
                while (engine.isRunning) {
                    delay(1000)
                    val handle = torrentHandle ?: continue
                    if (!handle.isValid) continue
                    val native = handle.status()
                    runOnUiThread {
                        peerCount = native.numPeers()
                        downloadRate = native.downloadRate()
                        downloadedBytes = native.totalDone()
                        totalBytes = native.totalWanted()
                        progressValue = native.progress()
                        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                            val fileRows = org.json.JSONArray()
                            resourceFiles.forEach { fileRows.put(org.json.JSONObject().put("path", it.path).put("size", it.bytes)) }
                            File(filesDir, "torrent-check.json").writeText(org.json.JSONObject()
                                .put("source", currentSource).put("metadata", metadataReady).put("peers", peerCount)
                                .put("downloaded", downloadedBytes).put("total", totalBytes).put("files", fileRows)
                                .put("playing", player?.isPlaying == true).put("position", player?.currentPosition ?: 0)
                                .put("speed", playbackSpeed).put("status", statusText).toString())
                        }
                    }
                }
            } catch (error: Exception) {
                runOnUiThread { statusText = "启动失败：${error.localizedMessage ?: error.javaClass.simpleName}" }
            }
        }
    }

    private fun createTorrentListener() = object : TorrentSessionListener {
        override fun onAddTorrent(handle: TorrentHandle, state: TorrentSessionStatus) {
            torrentHandle = handle
            handle.forceReannounce()
            handle.forceDHTAnnounce()
            updateFrom(state, "任务已添加，正在发现节点并获取元数据…")
            if (handle.torrentFile() != null) publishMetadata(handle)
        }
        override fun onMetadataReceived(handle: TorrentHandle, state: TorrentSessionStatus) {
            publishMetadata(handle)
            runOnUiThread {
                if (resumeOnReady) {
                    previewRequested = true
                    autoplayRequested = true
                    statusText = "已读取文件信息，正在缓冲并继续播放…"
                }
            }
            updateFrom(state, if (resumeOnReady) "已读取文件信息，正在缓冲并继续播放…" else "已获取文件信息，开始缓存…")
        }
        override fun onPieceFinished(handle: TorrentHandle, state: TorrentSessionStatus) {
            updateFrom(state, if (downloadMode) "正在下载并缓存…" else "正在缓冲，准备播放…")
            if (previewRequested && hasPlaybackBuffer(state)) startPlayback(state.videoFileUri, state.bytesDownloaded, autoplayRequested)
        }
        override fun onTorrentFinished(handle: TorrentHandle, state: TorrentSessionStatus) {
            updateFrom(state, "下载完成，文件已保存在应用 Movies 目录")
            if (previewRequested) startPlayback(state.videoFileUri, state.bytesDownloaded, autoplayRequested)
        }
        override fun onTorrentError(handle: TorrentHandle, state: TorrentSessionStatus) = updateFrom(state, "种子任务失败：${state.state}")
        override fun onMetadataFailed(handle: TorrentHandle, state: TorrentSessionStatus) = updateFrom(state, "无法读取种子元数据。请尝试其他资源或检查做种者是否在线。")
        override fun onTorrentDeleteFailed(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
        override fun onTorrentPaused(handle: TorrentHandle, state: TorrentSessionStatus) = updateFrom(state, "下载已暂停")
        override fun onTorrentDeleted(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
        override fun onTorrentRemoved(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
        override fun onTorrentResumed(handle: TorrentHandle, state: TorrentSessionStatus) = updateFrom(state, "下载已继续…")
        override fun onBlockUploaded(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    }

    private fun publishMetadata(handle: TorrentHandle) {
        val info = handle.torrentFile() ?: return
        val files = info.files()
        val actual = (0 until files.numFiles()).map { ResourceFile(files.filePath(it), files.fileSize(it)) }
        File(filesDir, "${info.infoHash().toString().lowercase()}.torrent").writeBytes(info.bencode())
        runOnUiThread {
            resourceFiles = actual
            metadataReady = true
            connectionTimedOut = false
            if (resumeOnReady) { previewRequested = true; autoplayRequested = true }
            statusText = "已取得并校验元数据；正在连接节点缓存视频…"
        }
    }

    private fun fetchVerifiedMetadata(hash: String): ByteArray? = runCatching {
        runOnUiThread { statusText = "正在查询元数据缓存；将校验内容是否与磁力哈希一致…" }
        val connection = java.net.URL("https://itorrents.org/torrent/${hash.uppercase()}.torrent")
            .openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 2 * 1024 * 1024) return@runCatching null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val info = TorrentInfo.bdecode(bytes)
            if (!info.infoHash().toString().equals(hash, true)) null else info.bencode()
        } finally { connection.disconnect() }
    }.getOrNull()

    private fun hasPlaybackBuffer(state: TorrentSessionStatus): Boolean {
        val buffer = state.torrentSessionBuffer
        val readyPieces = (buffer.bufferHeadIndex - buffer.startIndex).coerceAtLeast(0)
        return readyPieces >= 3 || state.progress >= 0.99f
    }

    private fun updateFrom(state: TorrentSessionStatus, message: String) {
        runOnUiThread {
            if (!connectionTimedOut || metadataReady) statusText = message
            progressValue = state.progress.coerceIn(0f, 1f)
            downloadedBytes = state.bytesDownloaded.coerceAtLeast(0L)
            totalBytes = state.bytesWanted.coerceAtLeast(0L)
            downloadRate = state.downloadRate.coerceAtLeast(0)
            if (state.videoFileUri != Uri.EMPTY) currentUri = state.videoFileUri
            if (downloadMode) {
                LibraryStore.updateDownload(
                    this@PlayerActivity,
                    intent.getStringExtra("title").orEmpty(),
                    currentSource,
                    message,
                    state.bytesDownloaded,
                    state.bytesWanted
                )
            }
        }
    }

    private fun requestPlayback(autoPlay: Boolean) {
        autoplayRequested = autoPlay
        if (player != null) {
            if (autoPlay) player?.play() else player?.pause()
            statusText = if (autoPlay) "正在播放…" else "预览已暂停；点击播放继续。"
            return
        }
        previewRequested = true
        statusText = if (autoPlay) "正在等待可播放缓存…" else "正在准备预览画面…"
        currentUri?.let { uri ->
            if (downloadedBytes > 0) startPlayback(uri, downloadedBytes, autoPlay)
        }
    }

    private fun seekBySeconds(seconds: Int) {
        val activePlayer = player ?: return
        val target = (activePlayer.currentPosition + seconds * 1_000L).coerceAtLeast(0L)
        activePlayer.seekTo(target)
    }

    private fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val index = speeds.indexOf(playbackSpeed).coerceAtLeast(0)
        playbackSpeed = speeds[(index + 1) % speeds.size]
        player?.setPlaybackSpeed(playbackSpeed)
    }

    private fun startPlayback(uri: Uri, bytesAtAttempt: Long, autoPlay: Boolean) {
        if (uri == Uri.EMPTY) return
        runOnUiThread {
            if (player != null) return@runOnUiThread
            playbackAttemptBytes = bytesAtAttempt
            currentUri = uri
            player?.release()
            val newPlayer = ExoPlayer.Builder(this).build()
            newPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playbackActive = isPlaying
                    if (isPlaying) LibraryStore.addPlayback(
                        this@PlayerActivity,
                        intent.getStringExtra("title").orEmpty().ifBlank { "磁力资源" },
                        currentSource
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackActive = false
                    statusText = "播放器暂时无法读取已缓存片段，下载仍会继续：${error.errorCodeName}"
                    player = null
                    newPlayer.release()
                }
            })
            newPlayer.setMediaItem(MediaItem.fromUri(uri))
            newPlayer.prepare()
            newPlayer.setPlaybackSpeed(playbackSpeed)
            newPlayer.playWhenReady = autoPlay
            player = newPlayer
            statusText = if (autoPlay) "正在播放，下载仍会继续…" else "预览已就绪，点击播放开始播放。"
        }
    }

    private fun togglePause() {
        val engine = session ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (engine.isPaused) engine.resume() else engine.pause()
                runOnUiThread { statusText = if (engine.isPaused) "下载已暂停" else "下载已继续…" }
            } catch (error: Exception) {
                runOnUiThread { statusText = "操作失败：${error.localizedMessage ?: error.javaClass.simpleName}" }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) { value /= 1024.0; unit++ }
        return "%.1f %s".format(value, units[unit])
    }

    override fun onDestroy() {
        session?.listener = null
        val closing = session
        session = null
        Thread { runCatching { closing?.stop() } }.start()
        player?.release()
        player = null
        super.onDestroy()
    }
}
