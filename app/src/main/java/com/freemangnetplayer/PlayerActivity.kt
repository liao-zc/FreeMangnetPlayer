package com.freemangnetplayer

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.masterwok.simpletorrentandroid.TorrentSession
import com.masterwok.simpletorrentandroid.TorrentSessionOptions
import com.masterwok.simpletorrentandroid.contracts.TorrentSessionListener
import com.masterwok.simpletorrentandroid.models.TorrentSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var session: TorrentSession? = null
    private var currentUri: Uri? = null
    private var currentSource: String = ""
    private var downloadMode = false
    private var playbackAttemptBytes = -1L

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
        val title = intent.getStringExtra("title").orEmpty()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF44D7B6), background = Color(0xFF101419), surface = Color(0xFF191F26)
            )) {
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title.ifBlank { if (downloadMode) "下载与缓存" else "磁力播放" }, style = MaterialTheme.typography.titleLarge)
                    Text(statusText, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { progressValue.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${(progressValue * 100).toInt()}%  ·  ${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}")
                    Text("${formatBytes(downloadRate.toLong())}/秒  ·  连接到 $peerCount 个做种者")

                    if (!downloadMode || player != null) {
                        AndroidView(
                            factory = { context -> PlayerView(context).apply { useController = true; player = this@PlayerActivity.player } },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    } else {
                        Card(Modifier.fillMaxWidth().weight(1f)) {
                            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Center) {
                                Text("下载任务在应用内运行", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Text("已选择种子中的最大文件。请保持此页面打开；当前版本不支持退出应用后的后台续传。")
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { togglePause() }, modifier = Modifier.weight(1f)) { Text("暂停 / 继续") }
                        if (currentUri != null) {
                            Button(onClick = { startPlayback(currentUri!!, downloadedBytes) }, modifier = Modifier.weight(1f)) {
                                Text(if (progressValue >= 1f) "播放已下载文件" else "尝试播放")
                            }
                        }
                    }
                    TextButton(onClick = { finish() }) { Text("返回搜索") }
                }
            }
        }
        startTorrent(currentSource)
    }

    private fun startTorrent(raw: String) {
        val source = raw.trim().replace("&amp;", "&", ignoreCase = true)
        val validMagnet = source.startsWith("magnet:?", true) && Regex("(?i)(?:[?&])xt=urn:btih:").containsMatchIn(source)
        val validTorrentUrl = source.startsWith("https://", true) || source.startsWith("http://", true)
        if (!validMagnet && !validTorrentUrl) {
            statusText = "磁力链接无效。请从搜索结果重新选择资源。"
            return
        }

        statusText = if (downloadMode) "正在连接并下载…" else "正在连接种子、获取文件信息…"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // SessionManager loads libtorrent JNI. Initialize it off the UI thread so the
                // activity can render progress and errors even when native startup is slow.
                val folder = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "FreeMangnetPlayer").apply { mkdirs() }
                val engine = TorrentSession(TorrentSessionOptions(
                    downloadLocation = folder,
                    onlyDownloadLargestFile = true,
                    shouldStream = !downloadMode,
                    streamBufferSize = 12,
                    enableLogging = true
                ))
                engine.listener = createTorrentListener()
                withContext(Dispatchers.Main) { session = engine }
                engine.start(this@PlayerActivity, Uri.parse(source))
            } catch (error: Exception) {
                runOnUiThread { statusText = "启动失败：${error.localizedMessage ?: error.javaClass.simpleName}" }
            }
        }
    }

    private fun createTorrentListener() = object : TorrentSessionListener {
        override fun onAddTorrent(handle: TorrentHandle, state: TorrentSessionStatus) = updateFrom(state, "种子已连接，正在读取文件信息…")
        override fun onMetadataReceived(handle: TorrentHandle, state: TorrentSessionStatus) = updateFrom(state, "已获取文件信息，开始缓存…")
        override fun onPieceFinished(handle: TorrentHandle, state: TorrentSessionStatus) {
            updateFrom(state, if (downloadMode) "正在下载并缓存…" else "正在缓冲，准备播放…")
            if (!downloadMode && hasPlaybackBuffer(state)) startPlayback(state.videoFileUri, state.bytesDownloaded)
        }
        override fun onTorrentFinished(handle: TorrentHandle, state: TorrentSessionStatus) {
            updateFrom(state, "下载完成，文件已保存在应用 Movies 目录")
            if (!downloadMode) startPlayback(state.videoFileUri, state.bytesDownloaded)
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

    private fun hasPlaybackBuffer(state: TorrentSessionStatus): Boolean {
        val buffer = state.torrentSessionBuffer
        val readyPieces = (buffer.bufferHeadIndex - buffer.startIndex).coerceAtLeast(0)
        return readyPieces >= 3 || state.progress >= 0.99f
    }

    private fun updateFrom(state: TorrentSessionStatus, message: String) {
        runOnUiThread {
            statusText = message
            progressValue = state.progress.coerceIn(0f, 1f)
            downloadedBytes = state.bytesDownloaded.coerceAtLeast(0L)
            totalBytes = state.bytesWanted.coerceAtLeast(0L)
            downloadRate = state.downloadRate.coerceAtLeast(0)
            peerCount = state.seederCount.coerceAtLeast(0)
            if (state.videoFileUri != Uri.EMPTY) currentUri = state.videoFileUri
        }
    }

    private fun startPlayback(uri: Uri, bytesAtAttempt: Long) {
        if (uri == Uri.EMPTY) return
        runOnUiThread {
            if (bytesAtAttempt >= 0 && playbackAttemptBytes >= 0 && bytesAtAttempt - playbackAttemptBytes < 2L * 1024 * 1024) return@runOnUiThread
            playbackAttemptBytes = bytesAtAttempt
            currentUri = uri
            player?.release()
            val newPlayer = ExoPlayer.Builder(this).build()
            newPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    statusText = "播放器暂时无法读取已缓存片段，下载仍会继续：${error.errorCodeName}"
                }
            })
            newPlayer.setMediaItem(MediaItem.fromUri(uri))
            newPlayer.prepare()
            newPlayer.playWhenReady = true
            player = newPlayer
            statusText = if (progressValue >= 1f) "下载完成，正在播放" else "正在播放，下载仍在继续…"
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
        player?.release()
        player = null
        super.onDestroy()
    }
}
