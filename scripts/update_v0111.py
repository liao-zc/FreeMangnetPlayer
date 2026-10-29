"""Apply the reviewed v0.1.11 changes using the plaintext-capable Python process."""
from pathlib import Path

def edit(name, old, new):
    path = Path(name)
    text = path.read_text(encoding='utf-8-sig')
    assert old in text, (name, old[:80])
    path.write_text(text.replace(old, new), encoding='utf-8')

base = 'app/src/main/java/com/freemangnetplayer/'
main = base + 'MainActivity.kt'
edit(main, '''        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSettings, modifier = Modifier.height(48.dp)) { Text("设置") }
        }
''', '')
edit(main, '''                                label = { Text("收藏") }
                            )
''', '''                                label = { Text("收藏") }
                            )
                            NavigationBarItem(
                                selected = false,
                                onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("设置") }
                            )
''')
edit(main, '    onSettings: () -> Unit,\n', '')
edit(main, '                                onSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },\n', '')
path = Path(main)
s = path.read_text(encoding='utf-8')
start = s.index('internal fun normalizeMagnetUri(')
end = s.index('\nprivate fun formatLibraryBytes', start)
s = s[:start] + '''/** Accept a pasted URL, HTML entity or encoded URL; canonicalize base32 BTIH. */
internal fun normalizeMagnetUri(raw: String): String? = runCatching {
    var decoded = Regex("&#(?:x([0-9a-fA-F]+)|(\\\\d+));", RegexOption.IGNORE_CASE)
        .replace(raw.replace("&amp;", "&", true)) { match ->
            val code = match.groups[1]?.value?.toInt(16) ?: match.groups[2]!!.value.toInt()
            if (Character.isValidCodePoint(code)) String(Character.toChars(code)) else ""
        }
    if (!decoded.contains("magnet:?", true)) decoded = java.net.URLDecoder.decode(decoded, "UTF-8")
    val start = decoded.indexOf("magnet:?", ignoreCase = true)
    if (start < 0) return null
    val candidate = decoded.substring(start).substringBefore('"').substringBefore('<').substringBefore('>')
        .substringBefore('\\n').substringBefore('\\r').trim().trimEnd('*', '_', ')', ']', ' ')
    val parameters = candidate.substringAfter('?').split('&').mapNotNull { parameter ->
        val key = parameter.substringBefore('=').lowercase()
        val value = parameter.substringAfter('=', "")
        if (key in setOf("xt", "dn", "tr", "xs", "as", "ws", "x.pe") && value.isNotBlank()) key to value else null
    }
    val xt = parameters.firstNotNullOfOrNull { (key, value) ->
        if (key != "xt") null else java.net.URLDecoder.decode(value, "UTF-8")
            .let { Regex("(?i)^urn:btih:([0-9a-f]{40}|[a-z2-7]{32})").find(it)?.groupValues?.get(1) }
    } ?: return null
    val hash = if (xt.length == 40) xt.lowercase() else {
        var bits = 0; var accumulator = 0
        val bytes = java.io.ByteArrayOutputStream()
        for (letter in xt.uppercase()) {
            accumulator = (accumulator shl 5) or "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(letter)
            bits += 5
            if (bits >= 8) { bits -= 8; bytes.write((accumulator shr bits) and 255) }
        }
        bytes.toByteArray().joinToString("") { "%02x".format(it) }
    }
    "magnet:?xt=urn:btih:$hash" + parameters.filter { it.first != "xt" }
        .joinToString("") { (key, value) -> "&$key=$value" }
}.getOrNull()
''' + s[end:]
path.write_text(s, encoding='utf-8')

session = 'app/src/main/java/com/masterwok/simpletorrentandroid/TorrentSession.kt'
edit(session, '                URLDecoder.decode(magnetUrl, "utf-8")', '                magnetUrl // Native parser decodes parameters; decoding the full URL corrupts trackers.')
edit(session, '    val isRunning: Boolean get() = sessionManager.isRunning', '''    val isRunning: Boolean get() = sessionManager.isRunning
    val dhtNodes: Long get() = sessionManager.dhtNodes()
    val actualDownloadLimit: Int get() = sessionManager.downloadRateLimit()
    val actualUploadLimit: Int get() = sessionManager.uploadRateLimit()
    @Volatile var networkMessage: String = ""

    /** HTTP metadata is a parallel fallback, never a prerequisite for P2P startup. */
    fun supplyMetadata(handle: TorrentHandle, bytes: ByteArray) {
        if (!handle.isValid || handle.torrentFile() != null) return
        val info = TorrentInfo.bdecode(bytes)
        if (info.infoHash() != handle.infoHash()) return
        sessionManager.remove(handle)
        sessionManager.download(info, torrentSessionOptions.downloadLocation)
    }''')
edit(session, '                    AlertType.ADD_TORRENT ->', '''                    AlertType.TRACKER_ERROR, AlertType.LISTEN_FAILED -> torrentSession.get()?.networkMessage = alert.toString()
                    AlertType.ADD_TORRENT ->''')

tasks = base + 'TorrentTasks.kt'
edit(tasks, '    var rate by mutableIntStateOf(0)', '''    var rate by mutableIntStateOf(0)
    var payloadRate by mutableIntStateOf(0)
    var payloadBytes by mutableLongStateOf(0)
    var dhtNodes by mutableLongStateOf(0)
    var seeds by mutableIntStateOf(0)
    var connectionCount by mutableIntStateOf(0)
    var networkMessage by mutableStateOf("")
    var downloadLimit by mutableIntStateOf(0)
    var uploadLimit by mutableIntStateOf(0)
    val startedAt = System.currentTimeMillis()
    var lastDiscovery = 0L''')
edit(tasks, '''    private val trackers = listOf("udp://tracker.opentrackr.org:1337/announce", "https://tracker.tamersunion.org:443/announce", "udp://open.stealth.si:80/announce")''', '''    private val trackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce", "https://tracker.tamersunion.org:443/announce",
        "udp://open.stealth.si:80/announce", "udp://tracker.torrent.eu.org:451/announce",
        "udp://exodus.desync.com:6969/announce", "udp://open.demonii.com:1337/announce",
        "https://tracker.gbitt.info/announce", "http://tracker.opentrackr.org:1337/announce")''')
edit(tasks, '''                        shouldStream = false, streamBufferSize = 4, enableLogging = false, dhtNodeMinimum = 0)''', '''                        shouldStream = false, streamBufferSize = 4, enableLogging = false, dhtNodeMinimum = 0,
                        downloadRateLimit = 0, uploadRateLimit = 0, connectionsLimit = 300)''')
edit(tasks, '                    options.settingsPack.cacheSize(0)', '''                    // 16 MiB per active session; queue limits already bound memory usage.
                    options.settingsPack.cacheSize(1024)
                    options.settingsPack.listenInterfaces("0.0.0.0:0,[::]:0")
                    options.settingsPack.setInteger(settings_pack.int_types.connection_speed.swigValue(), 30)
                    for (flag in listOf(settings_pack.bool_types.enable_dht, settings_pack.bool_types.enable_lsd,
                        settings_pack.bool_types.enable_upnp, settings_pack.bool_types.enable_natpmp,
                        settings_pack.bool_types.enable_incoming_tcp, settings_pack.bool_types.enable_outgoing_tcp,
                        settings_pack.bool_types.enable_incoming_utp, settings_pack.bool_types.enable_outgoing_utp)) {
                        options.settingsPack.setBoolean(flag.swigValue(), true)
                    }''')
edit(tasks, '                    options.settingsPack.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), false)\n', '')
edit(tasks, '                            task.handle = engine.retainHandle(handle)', '''                            task.handle = engine.retainHandle(handle)
                            handle.setDownloadLimit(0); handle.setUploadLimit(0)''')
edit(tasks, '''                    val bytes = metadataFile.takeIf { it.exists() }?.readBytes()
                        ?: if (!task.source.contains("&tr=", true) && task.id.length == 40) fetchMetadata(task.id) else null''', '''                    val bytes = metadataFile.takeIf { it.exists() }?.let { file ->
                        runCatching { file.readBytes().also { TorrentInfo.bdecode(it) } }.getOrElse { file.delete(); null }
                    }''')
edit(tasks, '''                var ticks = 0
''', '''                // Start DHT/trackers immediately. A blocked HTTP site must not delay them.
                val currentEngine = task.engine
                val metadataFallback = launch(Dispatchers.IO) {
                    delay(8000)
                    if (task.files.isEmpty() && task.id.length == 40 && !task.cancelled) {
                        val bytes = fetchMetadata(task.id)
                        if (bytes != null && task.engine === currentEngine && !task.cancelled) {
                            val handle = task.handle
                            if (handle != null && handle.isValid && handle.torrentFile() == null) {
                                currentEngine?.supplyMetadata(handle, bytes)
                            }
                        }
                    }
                }
                var ticks = 0
''')
edit(tasks, '                    task.peers = snapshot.second.numPeers(); task.rate = snapshot.second.downloadRate()', '''                    task.peers = snapshot.second.numPeers(); task.rate = snapshot.second.downloadRate()
                    task.payloadRate = snapshot.second.downloadPayloadRate(); task.payloadBytes = snapshot.second.totalPayloadDownload()
                    task.seeds = snapshot.second.numSeeds(); task.connectionCount = snapshot.second.numConnections()
                    task.dhtNodes = task.engine?.dhtNodes ?: 0
                    task.downloadLimit = task.engine?.actualDownloadLimit ?: 0
                    task.uploadLimit = task.engine?.actualUploadLimit ?: 0
                    task.networkMessage = task.engine?.networkMessage.orEmpty()
                    if (!task.paused && !task.queued && !task.complete && task.peers == 0 &&
                        System.currentTimeMillis() - task.lastDiscovery > 30_000) {
                        task.lastDiscovery = System.currentTimeMillis()
                        handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL); handle.forceDHTAnnounce()
                    }''')
edit(tasks, '''                        task.files.isEmpty() && ++ticks > 90 -> "未取得元数据；仍在寻找节点，可暂停或返回"''', '''                        task.files.isEmpty() && System.currentTimeMillis() - task.startedAt > 45_000 ->
                            "尚未找到提供文件信息的节点；DHT ${task.dhtNodes} 节点，可查看网络诊断或返回"''')
edit(tasks, '                        else -> "正在下载 / 缓存，可边下边播"', '''                        task.peers == 0 -> "尚无可连接资源节点，正在重新发现；应用未限速"
                        task.payloadRate < 1024 -> "已连接 ${task.peers} 节点，但有效数据供给较低；可查看网络诊断"
                        else -> "正在下载 / 缓存，可边下边播"''')
edit(tasks, '''                    if (task.complete && task.viewers == 0) break
                }
''', '''                    if (task.complete && task.viewers == 0) break
                }
                metadataFallback.cancel()
''')
edit(tasks, '''        task.streaming = true; task.playingIndex = index;''', '''        if (task.playingIndex != index) task.handle?.clearPieceDeadlines()
        task.streaming = true; task.playingIndex = index;''')
edit('app/build.gradle.kts', 'versionCode = 11; versionName = "0.1.10"', 'versionCode = 12; versionName = "0.1.11"')
print('Applied network, parsing and settings changes')
