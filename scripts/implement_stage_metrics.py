from pathlib import Path
p=Path('app/src/main/java/com/freemangnetplayer/TorrentTasks.kt');s=p.read_text(encoding='utf-8')
s=s.replace('    var lastDiscovery = 0L','''    var metadataAt = 0L
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
    var lastDiscovery = 0L''',1)
s=s.replace('                        task.files = snapshot.first\n','                        task.files = snapshot.first\n                        if (task.metadataAt == 0L) task.metadataAt = System.currentTimeMillis()\n',1)
s=s.replace('                    task.dhtNodes = task.engine?.dhtNodes ?: 0','''                    if (task.peers > 0 && task.peerAt == 0L) task.peerAt = System.currentTimeMillis()
                    if (task.done > 0 && task.verifiedPieceAt == 0L) task.verifiedPieceAt = System.currentTimeMillis()
                    task.dhtNodes = task.engine?.dhtNodes ?: 0''',1)
s=s.replace('fun stopAll() {','fun stopAll(reason: String = "后台服务已停止，缓存保留") {',1).replace('it.status = "后台服务已停止，缓存保留"','it.status = reason',1)
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/DownloadService.kt');s=p.read_text(encoding='utf-8').replace('        TorrentTasks.stopAll()\n        stopSelf()','''        scope.cancel()
        SettingsStore.prefs(this).edit().putString("background-service-stop-reason", "Android 15 后台下载时限已到；任务与已校验数据已保存，请打开下载列表继续").apply()
        TorrentTasks.stopAll("已暂停：后台下载时限已到，打开下载列表继续")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()''',1)
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/PlayerActivity.kt');s=p.read_text(encoding='utf-8')
s=s.replace('        media.addListener(object : Player.Listener {','        var playedOnce = false\n        media.addListener(object : Player.Listener {',1)
s=s.replace('                if (isPlaying) { playerMessage', '                if (isPlaying) { playedOnce = true; if (task.playbackAt == 0L) task.playbackAt = System.currentTimeMillis(); playerMessage',1)
s=s.replace('            override fun onPlayerError(error: PlaybackException)', '''            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player === media && playedOnce && playbackState == Player.STATE_BUFFERING) task.rebufferCount++
            }
            override fun onPlayerError(error: PlaybackException)''',1)
s=s.replace('.put("files", rows).put("decoder"', '''.put("stage", current.discoveryStage).put("metadataMilliseconds", if (current.metadataAt > 0) current.metadataAt - current.startedAt else -1)
                                        .put("peerMilliseconds", if (current.peerAt > 0) current.peerAt - current.startedAt else -1)
                                        .put("verifiedPieceMilliseconds", if (current.verifiedPieceAt > 0) current.verifiedPieceAt - current.startedAt else -1)
                                        .put("playbackMilliseconds", if (current.playbackAt > 0) current.playbackAt - current.startedAt else -1).put("rebufferCount", current.rebufferCount)
                                        .put("files", rows).put("decoder"''',1)
s=s.replace('                            Text("资源节点：','                            Text("连接阶段：${current.discoveryStage}")\n                            Text("资源节点：',1)
p.write_text(s,encoding='utf-8')
