from pathlib import Path
p=Path('app/src/main/java/com/masterwok/simpletorrentandroid/TorrentSession.kt');s=p.read_text(encoding='utf-8')
s=s.replace('    fun enableOutgoingUtp() {', '''    fun preferTcp(handle: TorrentHandle) {
        val settings = SettingsPack()
        settings.setBoolean(com.frostwire.jlibtorrent.swig.settings_pack.bool_types.enable_outgoing_utp.swigValue(), false)
        sessionManager.applySettings(settings)
        // Applying settings alone leaves an existing stalled uTP peer alive.
        handle.pause(); handle.resume()
        handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL)
        handle.forceDHTAnnounce(); reconnectKnownPeers(handle)
    }
    fun enableOutgoingUtp() {''')
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/TorrentTasks.kt');s=p.read_text(encoding='utf-8')
s=s.replace('    var lastPayloadAt =', '    var transportFallbackUsed = false\n    var transportRecoveryAt = 0L\n    var lastPayloadAt =')
s=s.replace('task.outgoingUtp = true; task.lastPayloadAt', 'task.transportFallbackUsed = false; task.transportRecoveryAt = 0L\n                    task.outgoingUtp = true; task.lastPayloadAt')
start=s.index('                    if (!task.outgoingUtp && !task.paused')
end=s.index('                    if (!task.paused && !task.queued && !task.complete && task.peers == 0',start)
s=s[:start]+'''                    if (!task.transportFallbackUsed && task.outgoingUtp && !task.paused && !task.queued && !task.complete &&
                        task.peers > 0 && System.currentTimeMillis() - task.lastPayloadAt > 10_000) {
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
'''+s[end:]
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/PlayerActivity.kt');s=p.read_text(encoding='utf-8').replace('.put("metadataReplacements",', '.put("transportFallbackUsed", current.transportFallbackUsed).put("metadataReplacements",');p.write_text(s,encoding='utf-8')
p=Path('scripts/verify_player_revision.py');s=p.read_text(encoding='utf-8').replace("recovered['restoredTrackers']>0 and recovered['outgoingUtp'] is True", "recovered['restoredTrackers']>0")
p.write_text(s,encoding='utf-8')
