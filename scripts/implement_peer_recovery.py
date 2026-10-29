from pathlib import Path

p=Path('app/src/main/java/com/masterwok/simpletorrentandroid/TorrentSession.kt')
s=p.read_text(encoding='utf-8')
s=s.replace('    /** HTTP metadata is a parallel fallback, never a prerequisite for P2P startup. */', '''    @Volatile var metadataReplacements = 0
    @Volatile var restoredTrackers = 0
    private val knownPeers = java.util.concurrent.ConcurrentHashMap<String, TcpEndpoint>()
    fun reconnectKnownPeers(handle: TorrentHandle) {
        knownPeers.values.take(100).forEach { endpoint -> runCatching { handle.swig().connect_peer(endpoint.swig()) } }
    }
    fun discoverPeers(handle: TorrentHandle) {
        val hash = handle.infoHash()
        sessionManager.dhtGetPeers(hash, 5)?.forEach { endpoint -> knownPeers[endpoint.toString()] = endpoint }
        if (handle.isValid) reconnectKnownPeers(handle)
    }
    /** HTTP metadata is a parallel fallback, never a prerequisite for P2P startup. */''')
s=s.replace('        sessionManager.remove(handle)\n        sessionManager.download(info, torrentSessionOptions.downloadLocation)', '''        // 1.2 bindings cannot set metadata in place. Preserve tracker URLs/tiers
        // and connected endpoints BEFORE replacing the owning handle.
        val oldTrackers = handle.trackers()
        oldTrackers.forEach { info.addTracker(it.url(), it.tier()) }
        handle.peerInfo().forEach { peer -> runCatching {
            val endpoint = peer.ip()
            knownPeers[endpoint] = TcpEndpoint(endpoint.substringBeforeLast(':').removePrefix("[").removeSuffix("]"), endpoint.substringAfterLast(':').toInt())
        } }
        restoredTrackers = oldTrackers.size
        metadataReplacements++
        sessionManager.remove(handle)
        sessionManager.download(info, torrentSessionOptions.downloadLocation, null, null, knownPeers.values.toList())''')
s=s.replace('        listener?.onAddTorrent(\n', '        reconnectKnownPeers(torrentHandle)\n        listener?.onAddTorrent(\n')
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/TorrentTasks.kt');s=p.read_text(encoding='utf-8')
s=s.replace('                    // Prefer TCP initially:', '                    // Both transports must be available immediately on real mobile networks.\n                    // Prefer TCP initially:')
s=s.replace('options.settingsPack.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), false)','options.settingsPack.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)')
s=s.replace('task.outgoingUtp = false; task.lastPayloadAt', 'task.outgoingUtp = true; task.lastPayloadAt')
s=s.replace('                    delay(8000)', '                    delay(3000)')
s=s.replace('val bytes = fetchMetadata(task.id)', 'val bytes = fetchMetadata(task)')
s=s.replace('                                currentEngine?.supplyMetadata(handle, bytes)', '''                                synchronized(task.nativeLock) {
                                    if (handle.isValid && handle.torrentFile() == null) {
                                        task.handle = null
                                        currentEngine?.supplyMetadata(handle, bytes)
                                    }
                                }''')
s=s.replace('                var ticks = 0', '''                val peerDiscovery = launch(Dispatchers.IO) {
                    delay(6000)
                    while (isActive && task.engine === currentEngine && !task.cancelled) {
                        if (!task.paused && !task.queued && !task.complete && task.payloadRate < 1024) {
                            task.handle?.takeIf { it.isValid }?.let { handle -> runCatching { currentEngine?.discoverPeers(handle) } }
                        }
                        delay(20_000)
                    }
                }
                var ticks = 0''')
s=s.replace('                metadataFallback.cancel()', '                metadataFallback.cancel(); peerDiscovery.cancel()')
s=s.replace('System.currentTimeMillis() - task.lastDiscovery > 30_000', 'System.currentTimeMillis() - task.lastDiscovery > 15_000')
s=s.replace('                        handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL); handle.forceDHTAnnounce()\n                    }\n                    task.status', '                        handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL); handle.forceDHTAnnounce()\n                        connectExplicitPeers(task, handle); task.engine?.reconnectKnownPeers(handle)\n                    }\n                    task.status')
s=s.replace('private fun fetchMetadata(hash: String): ByteArray? = runCatching {', 'private fun fetchMetadata(task: TorrentTask): ByteArray? = runCatching {\n        val hash = task.id')
# Existing provider URL below is replaced with the BEP exact-source if supplied.
start=s.index('    private fun fetchMetadata(');sub=s[start:];print(sub[:900])
p.write_text(s,encoding='utf-8')
