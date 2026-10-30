package com.freemangnetplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import org.libtorrent4j.*
import org.libtorrent4j.swig.settings_pack
import com.masterwok.simpletorrentandroid.*
import com.masterwok.simpletorrentandroid.extensions.bencode
import com.masterwok.simpletorrentandroid.contracts.TorrentSessionListener
import com.masterwok.simpletorrentandroid.models.TorrentSessionStatus
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class TaskFile(val index: Int, val path: String, val size: Long, val done: Long = 0) {
    val video get() = MediaFormats.isVideo(path)
}
class TorrentTask(val id: String, var source: String, val title: String, folder: File, download: Boolean) {
    var folder by mutableStateOf(folder)
    var download by mutableStateOf(download)
    var files by mutableStateOf<List<TaskFile>>(emptyList())
    private var selectedState by mutableStateOf<Set<Int>>(emptySet())
    private val selectionChanges = java.util.ArrayDeque<String>()
    var selected: Set<Int>
        get() = selectedState
        set(value) {
            synchronized(selectionChanges) {
                if (value != selectedState) {
                    if (selectionChanges.size >= 20) selectionChanges.removeFirst()
                    val caller = Throwable().stackTrace.drop(1).take(4).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
                    selectionChanges.addLast("${System.currentTimeMillis()} $selectedState -> $value @ $caller")
                }
                selectedState = value
            }
        }
    fun recentSelectionChanges(): List<String> = synchronized(selectionChanges) { selectionChanges.toList() }
    var deletedFiles by mutableStateOf<Set<Int>>(emptySet())
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
    var payloadRate by mutableIntStateOf(0)
    var payloadBytes by mutableLongStateOf(0)
    var dhtNodes by mutableLongStateOf(0)
    var seeds by mutableIntStateOf(0)
    var connectionCount by mutableIntStateOf(0)
    var networkMessage by mutableStateOf("")
    var downloadLimit by mutableIntStateOf(0)
    var uploadLimit by mutableIntStateOf(0)
    val startedAt = System.currentTimeMillis()
    var metadataAt = 0L
    var peerAt = 0L
    var verifiedPieceAt = 0L
    var playbackAt = 0L
    var rebufferCount = 0
    val discoveryStage get() = when {
        paused -> "PAUSED"
        queued -> "QUEUED"
        complete -> "COMPLETED"
        files.isEmpty() -> "METADATA"
        peers == 0 && connectionCount > 0 -> "HANDSHAKE"
        peers == 0 -> "DISCOVERY"
        done == 0L -> "WAITING_VERIFIED_PIECE"
        payloadRate < 1024 -> "LOW_SUPPLY"
        else -> "TRANSFERRING"
    }
    var lastDiscovery = 0L
    var outgoingUtp by mutableStateOf(false)
    var transportFallbackUsed = false
    var transportRecoveryAt = 0L
    var renderedFrameAt = 0L
    var lastPayloadAt = System.currentTimeMillis()
    var retainedCache by mutableStateOf(false)
    val nativeLock = Any()
    var selectionPaused = false
    var streaming = false
    var viewers = 0
    @Volatile var handle: TorrentHandle? = null
    @Volatile var engine: TorrentSession? = null
    var job: Job? = null
    var lastUsed = System.currentTimeMillis()
    var stopping = false
    var clearing by mutableStateOf(false)
    var destination: File? = null
    var moving = false
}

/** Sessions belong to a foreground service, never to an Activity. */
object TorrentTasks {
    val tasks = mutableStateMapOf<String, TorrentTask>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var context: Context
    private var initialized = false
    var serviceRunning = false
    @Volatile private var trackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce", "https://tracker.tamersunion.org:443/announce",
        "udp://open.stealth.si:80/announce", "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce", "udp://open.demonii.com:1337/announce",
        "https://tracker.gbitt.info/announce", "http://tracker.opentrackr.org:1337/announce")

    fun initialize(c: Context) {
        context = c.applicationContext
        if (initialized) return
        initialized = true
        trackers = (trackers + TrackerDirectory.cached(context)).distinct()
        scope.launch {
            val refreshed = withContext(Dispatchers.IO) { TrackerDirectory.refresh(context) }
            trackers = (trackers + refreshed).distinct().take(56)
            tasks.values.toList().forEach { task -> synchronized(task.nativeLock) {
                val handle = task.handle
                if (handle != null && handle.isValid && !task.cancelled) {
                    val existing = handle.trackers().map { it.url() }.toSet()
                    trackers.filterNot { it in existing }.forEach { handle.addTracker(AnnounceEntry(it)) }
                    if (!task.paused) handle.forceReannounce()
                }
            } }
        }
        val saved = runCatching { JSONArray(SettingsStore.prefs(c).getString("torrent-tasks", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until saved.length()) runCatching {
            val row = saved.getJSONObject(i)
            val task = TorrentTask(row.getString("id"), row.getString("source"), row.getString("title"),
                File(row.getString("folder")), row.getBoolean("download"))
            task.retainedCache = row.optBoolean("retainedCache", row.getBoolean("download"))
            task.paused = row.optBoolean("paused")
            task.complete = row.optBoolean("complete")
            task.selected = row.optJSONArray("selected")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
            task.playingIndex = row.optInt("playing", -1)
            task.deletedFiles = row.optJSONArray("deletedFiles")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
            task.files = row.optJSONArray("files")?.let { a -> (0 until a.length()).map { n -> a.getJSONObject(n).let { f -> TaskFile(f.getInt("index"), f.getString("path"), f.getLong("size"), f.optLong("done")) } } } ?: emptyList()
            task.destination = row.optString("destination").takeIf { it.isNotBlank() }?.let { File(it) }
            task.cancelled = row.optBoolean("cancelled")
            if (!task.download && !task.retainedCache) {
                task.cancelled = true; task.paused = true
            }
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
        require(tasks[id]?.clearing != true) { "当前资源正在清理，请稍后重试解析" }
        val task = tasks[id] ?: run {
            // Reuse existing download data before cache data; libtorrent verifies pieces.
            val downloadFolder = File(SettingsStore.root(c, true), "FreeMangnetPlayerTasks/$id")
            val cacheFolder = File(SettingsStore.root(c, false), "FreeMangnetPlayerTasks/$id")
            val folder = if (hasData(downloadFolder)) downloadFolder else if (hasData(cacheFolder)) cacheFolder
                else SettingsStore.taskFolder(c, download, id)
            TorrentTask(id, source, title.ifBlank { "磁力资源" }, folder, download).also { tasks[id] = it }
        }
        Regex("(?i)[?&]so=(\\d+)(?:&|$)").find(source)?.groupValues?.get(1)?.toIntOrNull()?.let {
            task.selected = if (task.download || task.retainedCache) task.selected + it else setOf(it); task.deletedFiles = task.deletedFiles - it; task.playingIndex = it
        }
        // Reopening the same hash must not discard newly pasted trackers/peers.
        if (source.startsWith("magnet:?", true) && task.source.startsWith("magnet:?", true)) {
            val incoming = source.substringAfter('?').split('&').filter {
                it.startsWith("tr=", true) || it.startsWith("x.pe=", true) || it.startsWith("ws=", true)
            }
            val original = task.source.substringAfter('?').split('&').toSet()
            val additional = incoming.filter { it !in original }.distinct()
            if (additional.isNotEmpty()) {
                task.source += additional.joinToString("") { "&$it" }
                task.handle?.let { handle ->
                    additional.filter { it.startsWith("tr=", true) }.forEach {
                        runCatching { handle.addTracker(AnnounceEntry(java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8"))) }
                    }
                    connectExplicitPeers(task, handle)
                    handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL); handle.forceDHTAnnounce()
                }
            }
        }
        task.lastUsed = System.currentTimeMillis()
        if (task.cancelled && !task.clearing) { task.cancelled = false; task.paused = false; task.queued = true; task.files = emptyList() }
        if (task.paused && task.files.isEmpty() && !task.clearing) { task.paused = false; task.queued = true }
        if (download) {
            recordHistory(task,"下载")
            if (!task.download || task.folder.canonicalPath == File(SettingsStore.root(c, false), "FreeMangnetPlayerTasks/$id").canonicalPath) {
                task.destination = SettingsStore.taskFolder(c, true, id)
            }
            task.download = true; task.retainedCache = true
        }
        persist()
        val service = Intent(c, DownloadService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(service) else c.startService(service)
        return task
    }
    fun schedule() {
        if (!initialized) return
        trimCaches()
        if (!serviceRunning) return
        for (download in listOf(true, false)) {
            var active = TaskLanePolicy.activeCount(tasks.values, download)
            val limit = if (download) SettingsStore.maxDownloads(context) else SettingsStore.maxCaches(context)
            if (active > limit) {
                for (task in tasks.values.filter { it.download == download && TaskLanePolicy.active(it) }.sortedBy { it.viewers > 0 }) {
                    if (active <= limit) break
                    task.queued = true; task.engine?.pause(); task.status = "排队中：并发数量已调整"; active--
                }
            }
            for (task in tasks.values.filter { it.download == download }.sortedByDescending { it.viewers > 0 }) {
                if (task.cancelled || task.stopping || task.clearing || task.paused || task.moving) continue
                if (task.engine != null) {
                    if (task.queued && (active < limit || task.complete)) { task.queued = false; resumeEngine(task); if (!task.complete) active++ }
                    continue
                }
                if (task.job?.isActive == true || (task.complete && task.viewers == 0)) continue
                if (active >= limit && !task.complete) { task.queued = true; task.status = "排队中：已达到该类任务数量 $limit"; continue }
                task.queued = false
                task.status = "正在连接种子并获取文件信息…"
                if (!task.complete) active++
                start(task)
            }
        }
        persist()
    }
    private fun connectExplicitPeers(task: TorrentTask, handle: TorrentHandle) {
                            // jlibtorrent 1.2 does not import the standard x.pe extension.
                            Regex("[?&]x[.]pe=([^&]+)").findAll(task.source).forEach { match ->
                                runCatching {
                                    val endpoint = java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
                                    val port = endpoint.substringAfterLast(':').toInt()
                                    val ip = endpoint.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
                                    require(port in 1..65535)
                                    handle.swig().connect_peer(TcpEndpoint(ip, port).swig())
                                }
                            }
    }
    private fun setMetadataPriorities(handle: TorrentHandle, task: TorrentTask) {
        val info = handle.torrentFile() ?: return
        val f = info.files()
        val rows = (0 until f.numFiles()).map { TaskFile(it, f.filePath(it), f.fileSize(it)) }
        val selected = task.selected.filter { it in rows.indices && it !in task.deletedFiles }.toSet().ifEmpty { rows.filter { it.index !in task.deletedFiles }.let { available -> (available.filter { it.video }.maxByOrNull { it.size } ?: available.maxByOrNull { it.size })?.let { setOf(it.index) } ?: emptySet() } }
        handle.prioritizeFiles(Array(rows.size) { if (it in selected) Priority.DEFAULT else Priority.IGNORE })
    }
    private fun start(task: TorrentTask) {
        task.job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    task.folder.mkdirs()
                    task.files = emptyList()
                    val options = TorrentSessionOptions(downloadLocation = task.folder, onlyDownloadLargestFile = false,
                        shouldStream = false, streamBufferSize = 4, enableLogging = false, dhtNodeMinimum = 0,
                        downloadRateLimit = 0, uploadRateLimit = 0, connectionsLimit = 300)
                    // Keep several independently operated bootstrap routers. A short
                    // override here used to discard libtorrent's broader defaults.
                    options.settingsPack.setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                        "router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881," +
                            "dht.libtorrent.org:25401,dht.aelitis.com:6881,router.bitcomet.com:6881,router.silotis.us:6881")
                    // 16 MiB per active session; queue limits already bound memory usage.
                    // libtorrent 2 uses the OS page cache instead of the removed 1.x cache_size.
                    options.settingsPack.listenInterfaces("0.0.0.0:0,[::]:0")
                    options.settingsPack.setInteger(settings_pack.int_types.connection_speed.swigValue(), 30)
                    for (flag in listOf(settings_pack.bool_types.enable_dht, settings_pack.bool_types.enable_lsd,
                        settings_pack.bool_types.enable_upnp, settings_pack.bool_types.enable_natpmp,
                        settings_pack.bool_types.enable_incoming_tcp, settings_pack.bool_types.enable_outgoing_tcp,
                        settings_pack.bool_types.enable_incoming_utp, settings_pack.bool_types.enable_outgoing_utp)) {
                        options.settingsPack.setBoolean(flag.swigValue(), true)
                    }
                    options.settingsPack.setInteger(settings_pack.int_types.min_reconnect_time.swigValue(), 5)
                    options.settingsPack.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
                    options.settingsPack.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
                    // Both transports must be available immediately on real mobile networks.
                    options.settingsPack.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)
                    task.transportFallbackUsed = false; task.transportRecoveryAt = 0L
                    task.outgoingUtp = true; task.lastPayloadAt = System.currentTimeMillis()
                    val engine = TorrentSession(options)
                    task.engine = engine
                    engine.listener = object : QuietTorrentListener() {
                        override fun onAddTorrent(handle: TorrentHandle, state: TorrentSessionStatus) {
                            task.handle = engine.retainHandle(handle)
                            handle.setDownloadLimit(0); handle.setUploadLimit(0)
                            connectExplicitPeers(task, handle)
                            setMetadataPriorities(handle, task)
                            handle.forceReannounce(); handle.forceDHTAnnounce()
                        }
                        override fun onMetadataReceived(handle: TorrentHandle, state: TorrentSessionStatus) {
                            setMetadataPriorities(handle, task)
                        }
                    }
                    val metadataFile = File(context.filesDir, "${task.id}.torrent")
                    val bytes = metadataFile.takeIf { it.exists() }?.let { file ->
                        runCatching { file.readBytes().also { require(TorrentInfo.bdecode(it).infoHash().toString().equals(task.id, true)) { "缓存元数据哈希不匹配" } } }.getOrElse { file.delete(); null }
                    }
                    if (task.cancelled) return@withContext
                    if (bytes != null) {
                        val info = TorrentInfo.bdecode(bytes)
                        val sourceTrackers = Regex("[?&]tr=([^&]+)").findAll(task.source).map {
                            java.net.URLDecoder.decode(it.groupValues[1], "UTF-8")
                        }.toList()
                        engine.start(info.bencode(sourceTrackers + trackers))
                    } else {
                        val source = if (task.source.startsWith("magnet:", true)) task.source + trackers.filter { !task.source.contains(java.net.URLEncoder.encode(it, "UTF-8")) }
                            .joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") } else task.source
                        engine.start(context, Uri.parse(source))
                    }
                }
                // Start DHT/trackers immediately. A blocked HTTP site must not delay them.
                val currentEngine = task.engine
                val metadataFallback = launch(Dispatchers.IO) {
                    delay(3000)
                    if (task.files.isEmpty() && task.id.length == 40 && !task.cancelled) {
                        val bytes = fetchMetadata(task)
                        if (bytes != null && task.engine === currentEngine && !task.cancelled) {
                            val handle = task.handle
                            if (handle != null && handle.isValid && handle.torrentFile() == null) {
                                synchronized(task.nativeLock) {
                                    if (handle.isValid && handle.torrentFile() == null) {
                                        task.handle = null
                                        task.outgoingUtp = false
                                        task.transportRecoveryAt = System.currentTimeMillis()
                                        currentEngine?.supplyMetadata(handle, bytes)
                                    }
                                }
                            }
                        }
                    }
                }
                val peerDiscovery = launch(Dispatchers.IO) {
                    delay(6000)
                    while (isActive && task.engine === currentEngine && !task.cancelled) {
                        if (!task.paused && !task.queued && !task.complete && task.payloadRate < 1024) {
                            task.handle?.takeIf { it.isValid }?.let { handle -> runCatching { currentEngine?.discoverPeers(handle) } }
                        }
                        delay(20_000)
                    }
                }
                var ticks = 0
                while (isActive && !task.cancelled && task.engine?.isRunning == true) {
                    delay(500)
                    val handle = task.handle ?: continue
                    if(ticks%20==0) withContext(Dispatchers.IO) { synchronized(task.nativeLock) { runCatching { if(task.payloadBytes>0) PeerPool.capture(context,task.id,handle); if(task.payloadRate<1024) PeerPool.reconnect(context,task.id,handle) } } }
                    val snapshot = withContext(Dispatchers.IO) { synchronized(task.nativeLock) {
                        require(handle.isValid) { "资源会话已结束" }
                        val info = handle.torrentFile()
                        val native = handle.status(TorrentHandle.QUERY_ACCURATE_DOWNLOAD_COUNTERS)
                        val progress = if (info != null) handle.fileProgress() else longArrayOf()
                        val rows = info?.files()?.let { f -> (0 until f.numFiles()).map { TaskFile(it, f.filePath(it), f.fileSize(it), progress.getOrElse(it) { 0L }) } } ?: emptyList()
                        if (info != null && task.files.isEmpty()) File(context.filesDir, "${task.id}.torrent").writeBytes(info.bencode())
                        Triple(rows, native, File(handle.savePath()))
                    } }
                    task.folder = snapshot.third
                    task.destination?.let { target ->
                        if (task.folder.canonicalPath == target.canonicalPath) {
                            task.moving = false; task.destination = null
                        } else if (!task.moving) {
                            target.mkdirs(); task.moving = true; handle.moveStorage(target.absolutePath)
                        }
                    }
                    if (snapshot.first.isNotEmpty() && task.files.isEmpty()) {
                        task.files = snapshot.first
                        if (task.metadataAt == 0L) task.metadataAt = System.currentTimeMillis()
                        if (task.selected.none { index -> index in task.files.indices }) {
                            task.selected = setOf((task.files.filter { it.video && it.index !in task.deletedFiles }.maxByOrNull { it.size } ?: task.files.filter { it.index !in task.deletedFiles }.maxBy { it.size }).index)
                        }
                        if (task.playingIndex < 0) task.playingIndex = task.files.filter { it.video }.sortedWith(
                            compareByDescending<TaskFile> { it.index in task.selected }.thenByDescending { it.done >= it.size }.thenByDescending { it.size }).firstOrNull()?.index ?: -1
                        applyPriorities(task)
                    } else task.files = snapshot.first
                    task.done = task.files.filter { it.index in task.selected || (task.streaming && it.index == task.playingIndex) }.sumOf { it.done }
                    task.total = task.files.filter { it.index in task.selected || (task.streaming && it.index == task.playingIndex) }.sumOf { it.size }
                    task.complete = task.total > 0 && task.done >= task.total
                    task.peers = snapshot.second.numPeers(); task.rate = snapshot.second.downloadRate()
                    val previousPayloadBytes = task.payloadBytes
                    task.payloadRate = snapshot.second.downloadPayloadRate(); task.payloadBytes = snapshot.second.totalPayloadDownload()
                    task.seeds = snapshot.second.numSeeds(); task.connectionCount = snapshot.second.numConnections()
                    if (task.peers > 0 && task.peerAt == 0L) task.peerAt = System.currentTimeMillis()
                    if (task.done > 0 && task.verifiedPieceAt == 0L) task.verifiedPieceAt = System.currentTimeMillis()
                    task.dhtNodes = task.engine?.dhtNodes ?: 0
                    task.downloadLimit = task.engine?.actualDownloadLimit ?: 0
                    task.uploadLimit = task.engine?.actualUploadLimit ?: 0
                    task.networkMessage = task.engine?.networkMessage.orEmpty()
                    if (task.payloadBytes > previousPayloadBytes) task.lastPayloadAt = System.currentTimeMillis()
                    if (!task.transportFallbackUsed && task.outgoingUtp && !task.paused && !task.queued && !task.complete &&
                        task.peers > 0 && System.currentTimeMillis() - maxOf(task.lastPayloadAt, task.peerAt) > 30_000) {
                        task.transportFallbackUsed = true; task.transportRecoveryAt = System.currentTimeMillis()
                        task.engine?.preferTcp(handle); task.outgoingUtp = false
                        connectExplicitPeers(task, handle)
                    }
                    if (!task.outgoingUtp && !task.paused && !task.queued && !task.complete && task.peers == 0 &&
                        System.currentTimeMillis() - task.transportRecoveryAt > 20_000) {
                        // Some mobile peers accept only uTP. Re-enable it only if
                        // TCP has failed to make a connection; keep useful TCP peers.
                        task.engine?.enableOutgoingUtp(); task.outgoingUtp = true
                        handle.forceDHTAnnounce(); connectExplicitPeers(task, handle)
                    }
                    if (!task.paused && !task.queued && !task.complete && task.peers == 0 &&
                        System.currentTimeMillis() - task.lastDiscovery > 15_000) {
                        task.lastDiscovery = System.currentTimeMillis()
                        handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL); handle.forceDHTAnnounce()
                        connectExplicitPeers(task, handle); task.engine?.reconnectKnownPeers(handle)
                    }
                    task.status = when {
                        task.paused -> "已暂停，缓存保留"
                        task.queued -> "排队等待下载"
                        task.complete -> "下载完成，已校验文件"
                        task.files.isEmpty() && System.currentTimeMillis() - task.startedAt > 45_000 ->
                            "尚未找到提供文件信息的节点；DHT ${task.dhtNodes} 节点，可查看网络诊断或返回"
                        task.files.isEmpty() -> "正在连接种子并读取文件列表…"
                        task.peers == 0 -> "尚无可连接资源节点，正在重新发现；应用未限速"
                        task.payloadRate < 1024 -> "已连接 ${task.peers} 节点，但有效数据供给较低；可查看网络诊断"
                        else -> "正在下载 / 缓存，可边下边播"
                    }
                    StoredVideos.sync(context, task)
                    if (task.download) LibraryStore.updateDownload(context, task.title, task.source, task.status, task.done, task.total)
                    if (ticks++ % 4 == 0) { persist(); schedule() }
                    if (task.complete && task.viewers == 0) break
                }
                metadataFallback.cancel(); peerDiscovery.cancel()
            } catch (error: Exception) {
                if (error !is CancellationException) { task.status = "任务失败：${error.localizedMessage}"; task.paused = true; persist() }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { closeEngine(task) }
            }
        }
    }
    private fun recordHistory(task: TorrentTask, kind: String) {
        HistoryStore.record(context,kind,HistoryEntry(task.title,"magnet:?xt=urn:btih:${task.id}",System.currentTimeMillis(),task.status,done=task.done,size=task.total))
    }
    fun retainCache(task: TorrentTask): Boolean {
        if (!task.retainedCache && !task.download && tasks.values.count { it.retainedCache && !it.download && !it.clearing } >= SettingsStore.maxCaches(context)) return false
        task.retainedCache = true
        recordHistory(task,"缓存")
        StoredVideos.sync(context, task)
        if (task.paused || task.cancelled) resume(task)
        persist()
        return true
    }
    fun releaseOtherTemporaryTasks(keep: TorrentTask) {
        // The previous Activity may still be stopping during the new onCreate.
        scope.launch {
            delay(1200)
            tasks.values.filter { it !== keep && !it.download && !it.retainedCache && it.viewers == 0 && !it.stopping }.toList().forEach { cancel(it, true) }
        }
    }
    fun switchStreamFile(task: TorrentTask, index: Int) {
        val previous = task.files.firstOrNull { it.index == task.playingIndex }
        if (previous == null || previous.index == index || task.download || task.retainedCache) {
            playFile(task, index)
            return
        }
        // Stop the native disk writer before deleting temporary bytes. Never unlink
        // an active libtorrent file while a decoder or its disk cache uses it.
        task.moving = true
        task.handle = null
        task.job?.cancel()
        scope.launch {
            try {
            task.job?.join()
            withContext(Dispatchers.IO) {
                closeEngine(task)
                val file = File(task.folder, previous.path).canonicalFile
                require(file.path.startsWith(task.folder.canonicalPath + File.separator))
                require(!file.exists() || file.delete()) { "无法释放上个视频的临时缓存" }
            }
            task.files = emptyList(); task.selected = setOf(index); task.playingIndex = index
            task.complete = false; task.streaming = true; task.moving = false
            task.paused = false; task.queued = true; task.job = null
            schedule()
            } catch (error: Exception) {
                task.moving = false; task.paused = true
                task.status = "切换失败：${error.localizedMessage}，可以返回或重试"
                persist()
            }
        }
    }
    fun retryDiscovery(task: TorrentTask) {
        task.lastDiscovery = 0
        task.lastPayloadAt = System.currentTimeMillis()
        task.handle?.let { handle ->
            if (handle.isValid) {
                connectExplicitPeers(task, handle)
                handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL)
                handle.forceDHTAnnounce()
            }
        }
        if (task.paused || task.queued || task.engine == null) resume(task)
        task.status = "正在重新发现资源节点；可返回，不会阻塞界面"
    }
    fun select(task: TorrentTask, selected: Set<Int>) {
        task.selected = selected.filter { index -> index !in task.deletedFiles && task.files.any { it.index == index } }.toSet()
        task.complete = allWantedFilesPresent(task)
        if (task.selected.isEmpty() && !task.streaming) {
            // Zero wanted bytes makes libtorrent announce itself as a seed and
            // disconnect the real seed. Pause instead, keeping native priorities.
            task.selectionPaused = !task.paused
            task.paused = true
            task.engine?.pause()
            task.status = "未选择文件，任务已暂停"
        } else {
            applyPriorities(task)
            if (task.selectionPaused) {
                task.selectionPaused = false
                resume(task)
            }
        }
        persist()
    }
    fun playFile(task: TorrentTask, index: Int) {
        if (index in task.deletedFiles) return
        if (task.playingIndex != index) task.handle?.clearPieceDeadlines()
        // Decoder preparation or retry must not erase an explicit multi-file selection.
        // Temporary file switches set their own selection in switchStreamFile.
        if (!task.download && !task.retainedCache && task.selected.isEmpty()) task.selected = setOf(index)
        task.streaming = true; task.playingIndex = index; task.complete = allWantedFilesPresent(task); task.lastUsed = System.currentTimeMillis()
        applyPriorities(task)
        if (task.selectionPaused) { task.selectionPaused = false; resume(task) }
        persist()
    }
    private fun allWantedFilesPresent(task: TorrentTask): Boolean {
        val wanted = task.files.filter { it.index in task.selected || (task.streaming && it.index == task.playingIndex) }
        return wanted.isNotEmpty() && wanted.all { it.done >= it.size }
    }
    private fun applyPriorities(task: TorrentTask) {
        val handle = task.handle ?: return
        if (task.files.isEmpty() || (task.selected.isEmpty() && !task.streaming)) return
        handle.prioritizeFiles(Array((task.files.maxOfOrNull { it.index } ?: -1) + 1) { if (it !in task.deletedFiles && (it in task.selected || (task.streaming && it == task.playingIndex))) Priority.DEFAULT else Priority.IGNORE })
        val info = handle.torrentFile() ?: return
        if (!task.streaming) return
        val file = task.files.firstOrNull { it.index == task.playingIndex } ?: return
        val offset = info.files().fileOffset(file.index)
        val first = (offset / info.pieceLength()).toInt()
        val last = ((offset + file.size - 1) / info.pieceLength()).toInt()
        // Request headers and the MP4 tail early; reads set deadlines near every seek.
        for (piece in first..minOf(first + 3, last)) if (!handle.havePiece(piece)) handle.setPieceDeadline(piece, 0)
        for (piece in maxOf(first, last - 7)..last) if (!handle.havePiece(piece)) handle.setPieceDeadline(piece, 500)
    }
    fun download(task: TorrentTask) {
        recordHistory(task,"下载")
        task.download = true; task.retainedCache = true
        val target = SettingsStore.taskFolder(context, true, task.id)
        task.destination = target
        if (task.folder.canonicalPath != target.canonicalPath && task.handle != null) {
            task.moving = true
            task.handle?.moveStorage(target.absolutePath)
        }
        if (task.folder.canonicalPath == target.canonicalPath) task.destination = null
        task.paused = false; task.cancelled = false; task.complete = allWantedFilesPresent(task)
        LibraryStore.updateDownload(context, task.title, task.source, "正在下载所选文件", task.done, task.total)
        resume(task); persist()
    }
    fun pause(task: TorrentTask) {
        task.paused = true; task.engine?.pause(); task.status = "已暂停，缓存保留"; persist(); schedule()
    }
    private fun resumeEngine(task: TorrentTask) {
        if (task.stopping || task.clearing || task.moving) return
        task.lastPayloadAt = System.currentTimeMillis()
        task.engine?.resume()
        task.handle?.let { handle ->
            handle.resume()
            handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL)
            handle.forceDHTAnnounce()
        }
        applyPriorities(task)
    }
    fun resume(task: TorrentTask) {
        if (!serviceRunning) {
            val service = Intent(context, DownloadService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
        }
        if (task.clearing) return
        task.cancelled = false; task.paused = false
        if (task.stopping || task.moving) { task.queued = true; persist(); return }
        val active = TaskLanePolicy.activeCount(tasks.values.filter { it !== task }, task.download)
        val limit = if (task.download) SettingsStore.maxDownloads(context) else SettingsStore.maxCaches(context)
        task.queued = !task.complete && active >= limit
        if (!task.queued) resumeEngine(task)
        schedule(); persist()
    }
    fun deleteFiles(task: TorrentTask, indices: Set<Int>) {
        if (task.stopping || task.clearing || task.viewers > 0) return
        val files = task.files.filter { it.index in indices }
        task.paused = true; task.stopping = true; task.handle = null
        task.job?.cancel()
        scope.launch {
            try {
                task.job?.join()
                withContext(Dispatchers.IO) {
                    closeEngine(task)
                    for (file in files) {
                        val path = File(task.folder, file.path).canonicalFile
                        require(path.path.startsWith(task.folder.canonicalPath + File.separator))
                        require(!path.exists() || path.delete()) { "删除失败：${file.path}" }
                    }
                }
                task.deletedFiles = task.deletedFiles + indices
                task.selected = task.selected - indices
                if (task.playingIndex in indices) { task.playingIndex = -1; task.streaming = false }
                task.files = task.files.map { if (it.index in indices) it.copy(done = 0) else it }
                LibraryStore.removeFileReferences(context, task.id, indices)
                task.status = "所选文件已删除，其他文件保留；任务已暂停"
            } catch (error: Exception) { task.status = "删除失败：${error.localizedMessage}" }
            task.stopping = false; persist(); schedule()
        }
    }
    fun cancel(task: TorrentTask, delete: Boolean = false) {
        task.cancelled = true; task.paused = true; task.status = if (delete) "正在清理任务文件…" else "已取消，已下载文件保留"
        if (task.stopping) return
        task.stopping = true
        task.clearing = delete
        task.handle = null
        task.job?.cancel()
        scope.launch {
            try {
            task.job?.join()
            withContext(Dispatchers.IO) {
                closeEngine(task)
                if (delete) {
                    val folders = listOfNotNull(task.folder, task.destination).map { it.canonicalFile }.distinct()
                    for (folder in folders) {
                        require(folder.name == task.id && folder.parentFile?.name == "FreeMangnetPlayerTasks")
                        require(!folder.exists() || folder.deleteRecursively()) { "任务文件删除失败：$folder" }
                    }
                }
            }
            if (delete) { tasks.remove(task.id); LibraryStore.removeDownload(context, task.source); LibraryStore.removeTaskCaches(context, task.id) }
            task.stopping = false; task.clearing = false; persist(); schedule()
            } catch (error: Exception) {
                task.stopping = false; task.clearing = false
                task.status = "清理失败：${error.localizedMessage}"
                persist()
            }
        }
        persist()
    }
    private fun closeEngine(task: TorrentTask) {
        val engine = task.engine
        task.handle = null; task.engine = null
        engine?.listener = null
        synchronized(task.nativeLock) { runCatching { engine?.stop() } }
    }
    fun clear(download: Boolean): Int {
        val values = tasks.values.filter { it.download == download && it.viewers == 0 }.toList()
        values.forEach { cancel(it, true) }
        return tasks.values.count { it.download == download && it.viewers > 0 }
    }
    private fun trimCaches() {
        val values = tasks.values.filter { !it.download && !it.retainedCache && !it.clearing }.sortedBy { it.lastUsed }
        var excess = values.size - SettingsStore.maxCaches(context)
        for (task in values) if (excess > 0 && task.viewers == 0) { cancel(task, true); excess-- }
    }
    fun persist() {
        if (!initialized) return
        val rows = JSONArray()
        tasks.values.filter { it.download && !it.clearing }.forEach { LibraryStore.updateDownload(context, it.title, it.source, it.status, it.done, it.total) }
        tasks.values.filter { !it.clearing }.forEach { rows.put(JSONObject().put("id", it.id).put("source", it.source).put("title", it.title)
            .put("folder", it.folder.absolutePath).put("destination", it.destination?.absolutePath ?: "").put("download", it.download).put("paused", it.paused)
            .put("files", JSONArray().also { a -> it.files.forEach { f -> a.put(JSONObject().put("index", f.index).put("path", f.path).put("size", f.size).put("done", f.done)) } }).put("deletedFiles", JSONArray(it.deletedFiles.toList())).put("retainedCache", it.retainedCache).put("cancelled", it.cancelled).put("complete", it.complete).put("selected", JSONArray(it.selected.toList())).put("playing", it.playingIndex)) }
        SettingsStore.prefs(context).edit().putString("torrent-tasks", rows.toString()).apply()
    }
    fun stopAll(reason: String = "后台服务已停止，缓存保留") {
        val values = tasks.values.toList()
        values.forEach { it.paused = true; it.engine?.pause(); it.job?.cancel(); it.status = reason }
        persist()
        scope.launch { values.forEach { it.job?.join(); withContext(Dispatchers.IO) { closeEngine(it) } } }
    }
    private fun fetchMetadata(task: TorrentTask): ByteArray? = runCatching {
        val hash = task.id
        val exactSource = Regex("(?i)[?&]xs=([^&]+)").find(task.source)?.groupValues?.get(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        val connection = java.net.URL(exactSource ?: "https://itorrents.net/torrent/${hash.uppercase()}.torrent").openConnection() as java.net.HttpURLConnection
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
            info.bencode(trackers)
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
