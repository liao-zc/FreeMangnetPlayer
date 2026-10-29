package com.freemangnetplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.swig.settings_pack
import com.masterwok.simpletorrentandroid.*
import com.masterwok.simpletorrentandroid.contracts.TorrentSessionListener
import com.masterwok.simpletorrentandroid.models.TorrentSessionStatus
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class TaskFile(val index: Int, val path: String, val size: Long, val done: Long = 0) {
    val video get() = path.substringAfterLast('.', "").lowercase() in listOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "wmv")
}
class TorrentTask(val id: String, val source: String, val title: String, folder: File, download: Boolean) {
    var folder by mutableStateOf(folder)
    var download by mutableStateOf(download)
    var files by mutableStateOf<List<TaskFile>>(emptyList())
    var selected by mutableStateOf<Set<Int>>(emptySet())
    var playingIndex by mutableIntStateOf(-1)
    var status by mutableStateOf("等待任务调度…")
    var paused by mutableStateOf(false)
    var cancelled by mutableStateOf(false)
    var complete by mutableStateOf(false)
    var queued by mutableStateOf(true)
    var done by mutableLongStateOf(0)
    var total by mutableLongStateOf(0)
    var peers by mutableIntStateOf(0)
    var rate by mutableIntStateOf(0)
    var streaming = false
    var viewers = 0
    @Volatile var handle: TorrentHandle? = null
    @Volatile var engine: TorrentSession? = null
    var job: Job? = null
    var lastUsed = System.currentTimeMillis()
    var stopping = false
    var clearing = false
    var moving = false
}

/** Sessions belong to a foreground service, never to an Activity. */
object TorrentTasks {
    val tasks = mutableStateMapOf<String, TorrentTask>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var context: Context
    private var initialized = false
    private val trackers = listOf("udp://tracker.opentrackr.org:1337/announce", "https://tracker.tamersunion.org:443/announce", "udp://open.stealth.si:80/announce")

    fun initialize(c: Context) {
        context = c.applicationContext
        if (initialized) return
        initialized = true
        val saved = runCatching { JSONArray(SettingsStore.prefs(c).getString("torrent-tasks", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until saved.length()) runCatching {
            val row = saved.getJSONObject(i)
            val task = TorrentTask(row.getString("id"), row.getString("source"), row.getString("title"),
                File(row.getString("folder")), row.getBoolean("download"))
            task.paused = row.optBoolean("paused")
            task.complete = row.optBoolean("complete")
            task.selected = row.optJSONArray("selected")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
            task.playingIndex = row.optInt("playing", -1)
            task.cancelled = row.optBoolean("cancelled")
            task.status = if (task.cancelled) "已取消，已下载文件保留" else if (task.paused) "已暂停" else "正在恢复并校验缓存…"
            tasks[task.id] = task
        }
    }
    private fun hasData(folder: File) = folder.isDirectory && folder.walkTopDown().any { it.isFile && it.length() > 0 }
    fun obtain(c: Context, raw: String, title: String, download: Boolean): TorrentTask {
        initialize(c)
        val source = normalizeMagnetUri(raw) ?: raw
        require(source.startsWith("magnet:?", true) || source.startsWith("https://") || source.startsWith("http://")) { "资源链接无效" }
        val id = Regex("(?i)xt=urn:btih:([0-9a-f]{40})").find(source)?.groupValues?.get(1)?.lowercase()
            ?: MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
        val task = tasks[id] ?: run {
            // Reuse existing download data before cache data; libtorrent verifies pieces.
            val downloadFolder = File(SettingsStore.root(c, true), "FreeMangnetPlayerTasks/$id")
            val cacheFolder = File(SettingsStore.root(c, false), "FreeMangnetPlayerTasks/$id")
            val folder = if (hasData(downloadFolder)) downloadFolder else if (hasData(cacheFolder)) cacheFolder
                else SettingsStore.taskFolder(c, download, id)
            TorrentTask(id, source, title.ifBlank { "磁力资源" }, folder, download).also { tasks[id] = it }
        }
        task.lastUsed = System.currentTimeMillis()
        if (task.cancelled && !task.stopping && !task.clearing) { task.cancelled = false; task.paused = false; task.queued = true; task.files = emptyList() }
        if (download) task.download = true
        persist()
        val service = Intent(c, DownloadService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(service) else c.startService(service)
        return task
    }
    fun schedule() {
        if (!initialized) return
        trimCaches()
        var active = tasks.values.count { (it.engine != null || it.job?.isActive == true) && !it.queued && !it.paused && !it.complete && !it.cancelled }
        val limit = SettingsStore.maxDownloads(context)
        if (active > limit) {
            for (task in tasks.values.filter { (it.engine != null || it.job?.isActive == true) && !it.queued && !it.paused && !it.complete && !it.cancelled }.sortedBy { it.viewers > 0 }) {
                if (active <= limit) break
                task.queued = true; task.engine?.pause(); task.status = "排队中：并发数量已调整"; active--
            }
        }
        for (task in tasks.values.sortedByDescending { it.viewers > 0 }) {
            if (task.cancelled || task.stopping || task.clearing || task.paused || task.moving) continue
            if (task.engine != null) {
                if (task.queued && active < limit) { task.queued = false; resumeEngine(task); active++ }
                continue
            }
            if (task.job?.isActive == true || (task.complete && task.viewers == 0)) continue
            if (active >= limit) { task.queued = true; task.status = "排队中：已达到最大同时下载数量 $limit"; continue }
            task.queued = false
            task.status = "正在连接种子并获取文件信息…"
            active++
            start(task)
        }
        persist()
    }
    private fun setMetadataPriorities(handle: TorrentHandle, task: TorrentTask) {
        val info = handle.torrentFile() ?: return
        val f = info.files()
        val rows = (0 until f.numFiles()).map { TaskFile(it, f.filePath(it), f.fileSize(it)) }
        val selected = task.selected.ifEmpty { setOf((rows.filter { it.video }.maxByOrNull { it.size } ?: rows.maxBy { it.size }).index) }
        handle.prioritizeFiles(Array(rows.size) { if (it in selected) Priority.NORMAL else Priority.IGNORE })
    }
    private fun start(task: TorrentTask) {
        task.job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    task.folder.mkdirs()
                    val options = TorrentSessionOptions(downloadLocation = task.folder, onlyDownloadLargestFile = false,
                        shouldStream = false, streamBufferSize = 4, enableLogging = false, dhtNodeMinimum = 0)
                    options.settingsPack.setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), "router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881")
                    options.settingsPack.cacheSize(0)
                    options.settingsPack.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), false)
                    options.settingsPack.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
                    options.settingsPack.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
                    val engine = TorrentSession(options)
                    task.engine = engine
                    engine.listener = object : QuietTorrentListener() {
                        override fun onAddTorrent(handle: TorrentHandle, state: TorrentSessionStatus) {
                            task.handle = engine.retainHandle(handle)
                            setMetadataPriorities(handle, task)
                            handle.forceReannounce(); handle.forceDHTAnnounce()
                        }
                        override fun onMetadataReceived(handle: TorrentHandle, state: TorrentSessionStatus) {
                            setMetadataPriorities(handle, task)
                        }
                    }
                    val metadataFile = File(context.filesDir, "${task.id}.torrent")
                    val bytes = metadataFile.takeIf { it.exists() }?.readBytes()
                        ?: if (!task.source.contains("&tr=", true) && task.id.length == 40) fetchMetadata(task.id) else null
                    if (task.cancelled) return@withContext
                    if (bytes != null) {
                        val info = TorrentInfo.bdecode(bytes)
                        Regex("[?&]tr=([^&]+)").findAll(task.source).forEach {
                            info.addTracker(java.net.URLDecoder.decode(it.groupValues[1], "UTF-8"), 0)
                        }
                        trackers.forEach { info.addTracker(it, 0) }
                        engine.start(info.bencode())
                    } else {
                        val source = task.source + trackers.filter { !task.source.contains(java.net.URLEncoder.encode(it, "UTF-8")) }
                            .joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") }
                        engine.start(context, Uri.parse(source))
                    }
                }
                var ticks = 0
                while (isActive && !task.cancelled && task.engine?.isRunning == true) {
                    delay(500)
                    val handle = task.handle ?: continue
                    val snapshot = withContext(Dispatchers.IO) {
                        val info = handle.torrentFile()
                        val native = handle.status(TorrentHandle.QUERY_ACCURATE_DOWNLOAD_COUNTERS)
                        val progress = if (info != null) handle.fileProgress() else longArrayOf()
                        val rows = info?.files()?.let { f -> (0 until f.numFiles()).map { TaskFile(it, f.filePath(it), f.fileSize(it), progress.getOrElse(it) { 0L }) } } ?: emptyList()
                        if (info != null && task.files.isEmpty()) File(context.filesDir, "${task.id}.torrent").writeBytes(info.bencode())
                        Triple(rows, native, File(handle.savePath()))
                    }
                    task.folder = snapshot.third
                    if (task.moving && task.folder.canonicalPath == SettingsStore.taskFolder(context, true, task.id).canonicalPath) task.moving = false
                    if (snapshot.first.isNotEmpty() && task.files.isEmpty()) {
                        task.files = snapshot.first
                        if (task.selected.isEmpty()) {
                            task.selected = setOf((task.files.filter { it.video }.maxByOrNull { it.size } ?: task.files.maxBy { it.size }).index)
                        }
                        if (task.playingIndex < 0) task.playingIndex = task.files.filter { it.video }.sortedWith(
                            compareByDescending<TaskFile> { it.done >= it.size }.thenByDescending { it.index in task.selected }.thenByDescending { it.size }).firstOrNull()?.index ?: -1
                        applyPriorities(task)
                    } else task.files = snapshot.first
                    task.done = task.files.filter { it.index in task.selected || (task.streaming && it.index == task.playingIndex) }.sumOf { it.done }
                    task.total = task.files.filter { it.index in task.selected || (task.streaming && it.index == task.playingIndex) }.sumOf { it.size }
                    task.complete = task.total > 0 && task.done >= task.total
                    task.peers = snapshot.second.numPeers(); task.rate = snapshot.second.downloadRate()
                    task.status = when {
                        task.paused -> "已暂停，缓存保留"
                        task.queued -> "排队等待下载"
                        task.complete -> "下载完成，已校验文件"
                        task.files.isEmpty() && ++ticks > 90 -> "未取得元数据；仍在寻找节点，可暂停或返回"
                        task.files.isEmpty() -> "正在连接种子并读取文件列表…"
                        else -> "正在下载 / 缓存，可边下边播"
                    }
                    if (task.download) LibraryStore.updateDownload(context, task.title, task.source, task.status, task.done, task.total)
                    if (ticks++ % 4 == 0) { persist(); schedule() }
                    if (task.complete && task.viewers == 0) break
                }
            } catch (error: Exception) {
                if (error !is CancellationException) { task.status = "任务失败：${error.localizedMessage}"; task.paused = true; persist() }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { closeEngine(task) }
            }
        }
    }
    fun select(task: TorrentTask, selected: Set<Int>) {
        val wasEmpty = task.selected.isEmpty()
        task.selected = selected.filter { index -> task.files.any { it.index == index } }.toSet()
        task.complete = false
        applyPriorities(task)
        if (wasEmpty && task.selected.isNotEmpty() && !task.paused) task.handle?.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL)
        persist()
    }
    fun playFile(task: TorrentTask, index: Int) {
        task.streaming = true; task.playingIndex = index; task.complete = false; task.lastUsed = System.currentTimeMillis()
        applyPriorities(task); persist()
    }
    private fun applyPriorities(task: TorrentTask) {
        val handle = task.handle ?: return
        handle.prioritizeFiles(Array(task.files.size) { if (it in task.selected || (task.streaming && it == task.playingIndex)) Priority.NORMAL else Priority.IGNORE })
        val info = handle.torrentFile() ?: return
        if (!task.streaming) return
        val file = task.files.firstOrNull { it.index == task.playingIndex } ?: return
        val offset = info.files().fileOffset(file.index)
        val first = (offset / info.pieceLength()).toInt()
        val last = ((offset + file.size - 1) / info.pieceLength()).toInt()
        // Request headers and the MP4 tail early; reads set deadlines near every seek.
        for (piece in first..minOf(first + 3, last)) if (!handle.havePiece(piece)) handle.setPieceDeadline(piece, 0)
        for (piece in maxOf(first, last - 1)..last) if (!handle.havePiece(piece)) handle.setPieceDeadline(piece, 500)
    }
    fun download(task: TorrentTask) {
        task.download = true
        val target = SettingsStore.taskFolder(context, true, task.id)
        if (task.folder.canonicalPath != target.canonicalPath && task.handle != null) {
            task.moving = true
            task.handle?.moveStorage(target.absolutePath)
        }
        task.paused = false; task.cancelled = false; task.complete = false
        LibraryStore.updateDownload(context, task.title, task.source, "正在下载所选文件", task.done, task.total)
        schedule(); persist()
    }
    fun pause(task: TorrentTask) {
        task.paused = true; task.engine?.pause(); task.status = "已暂停，缓存保留"; persist(); schedule()
    }
    private fun resumeEngine(task: TorrentTask) {
        task.engine?.resume()
        task.handle?.let { handle ->
            handle.resume()
            handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL)
            handle.forceDHTAnnounce()
        }
        applyPriorities(task)
    }
    fun resume(task: TorrentTask) {
        task.paused = false
        val active = tasks.values.count { it !== task && (it.engine != null || it.job?.isActive == true) && !it.paused && !it.queued && !it.complete && !it.cancelled }
        task.queued = active >= SettingsStore.maxDownloads(context)
        if (!task.queued) resumeEngine(task)
        schedule(); persist()
    }
    fun cancel(task: TorrentTask, delete: Boolean = false) {
        task.cancelled = true; task.paused = true; task.status = if (delete) "正在清理任务文件…" else "已取消，已下载文件保留"
        if (task.stopping) return
        task.stopping = true
        task.clearing = delete
        task.handle = null
        task.job?.cancel()
        scope.launch {
            task.job?.join()
            withContext(Dispatchers.IO) {
                closeEngine(task)
                if (delete) {
                    val folder = task.folder.canonicalFile
                    // Only this task's dedicated directory; never delete the configured root.
                    require(folder.name == task.id && folder.parentFile?.name == "FreeMangnetPlayerTasks")
                    require(!folder.exists() || folder.deleteRecursively()) { "任务文件删除失败" }
                }
            }
            if (delete) { tasks.remove(task.id); LibraryStore.removeDownload(context, task.source) }
            task.stopping = false; task.clearing = false; persist(); schedule()
        }
        persist()
    }
    private fun closeEngine(task: TorrentTask) {
        val engine = task.engine
        task.handle = null; task.engine = null
        engine?.listener = null
        runCatching { engine?.stop() }
    }
    fun clear(download: Boolean): Int {
        val values = tasks.values.filter { it.download == download && it.viewers == 0 }.toList()
        values.forEach { cancel(it, true) }
        return tasks.values.count { it.download == download && it.viewers > 0 }
    }
    private fun trimCaches() {
        val values = tasks.values.filter { !it.download && !it.clearing }.sortedBy { it.lastUsed }
        var excess = values.size - SettingsStore.maxCaches(context)
        for (task in values) if (excess > 0 && task.viewers == 0) { cancel(task, true); excess-- }
    }
    fun persist() {
        if (!initialized) return
        val rows = JSONArray()
        tasks.values.filter { it.download && !it.clearing }.forEach { LibraryStore.updateDownload(context, it.title, it.source, it.status, it.done, it.total) }
        tasks.values.filter { !it.clearing }.forEach { rows.put(JSONObject().put("id", it.id).put("source", it.source).put("title", it.title)
            .put("folder", it.folder.absolutePath).put("download", it.download).put("paused", it.paused)
            .put("cancelled", it.cancelled).put("complete", it.complete).put("selected", JSONArray(it.selected.toList())).put("playing", it.playingIndex)) }
        SettingsStore.prefs(context).edit().putString("torrent-tasks", rows.toString()).apply()
    }
    fun stopAll() {
        val values = tasks.values.toList()
        values.forEach { it.paused = true; it.engine?.pause(); it.job?.cancel(); it.status = "后台服务已停止，缓存保留" }
        persist()
        scope.launch { values.forEach { it.job?.join(); withContext(Dispatchers.IO) { closeEngine(it) } } }
    }
    private fun fetchMetadata(hash: String): ByteArray? = runCatching {
        val connection = java.net.URL("https://itorrents.net/torrent/${hash.uppercase()}.torrent").openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 6000; connection.readTimeout = 6000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
            if (connection.responseCode != 200) return@runCatching null
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
            } ?: return@runCatching null
            val info = TorrentInfo.bdecode(bytes)
            if (!info.infoHash().toString().equals(hash, true)) return@runCatching null
            trackers.forEach { info.addTracker(it, 0) }; info.bencode()
        } finally { connection.disconnect() }
    }.getOrNull()
}

open class QuietTorrentListener : TorrentSessionListener {
    override fun onAddTorrent(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onMetadataReceived(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onPieceFinished(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onTorrentFinished(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onTorrentError(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onMetadataFailed(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onTorrentDeleteFailed(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onTorrentPaused(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onTorrentDeleted(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onTorrentRemoved(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onTorrentResumed(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
    override fun onBlockUploaded(handle: TorrentHandle, state: TorrentSessionStatus) = Unit
}
