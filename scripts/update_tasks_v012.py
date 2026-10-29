from pathlib import Path

root = Path('app/src/main/java/com/freemangnetplayer')
p = root / 'TorrentTasks.kt'
s = p.read_text(encoding='utf-8')
s = s.replace('    var selectionPaused = false', '    var retainedCache by mutableStateOf(false)\n    val nativeLock = Any()\n    var selectionPaused = false')
s = s.replace('            task.paused = row.optBoolean("paused")', '            task.retainedCache = row.optBoolean("retainedCache", true)\n            task.paused = row.optBoolean("paused")')
s = s.replace('            task.download = true\n        }', '            task.download = true; task.retainedCache = true\n        }')
s = s.replace('        if (task.playingIndex != index) task.handle?.clearPieceDeadlines()', '        if (task.playingIndex != index) task.handle?.clearPieceDeadlines()\n        if (!task.download && !task.retainedCache) task.selected = setOf(index)')
s = s.replace('        task.download = true\n        val target', '        task.download = true; task.retainedCache = true\n        val target')
s = s.replace('.put("cancelled", it.cancelled)', '.put("retainedCache", it.retainedCache).put("cancelled", it.cancelled)')
s = s.replace('        tasks.values.filter { !it.clearing }.forEach', '        tasks.values.filter { !it.clearing && (it.download || it.retainedCache) }.forEach')
s = s.replace('                    task.folder.mkdirs()', '                    task.folder.mkdirs()\n                    task.files = emptyList()')
s = s.replace('runCatching { file.readBytes().also { TorrentInfo.bdecode(it) } }', 'runCatching { file.readBytes().also { require(TorrentInfo.bdecode(it).infoHash().toString().equals(task.id, true)) { "缓存元数据哈希不匹配" } } }')
s = s.replace('val snapshot = withContext(Dispatchers.IO) {\n                        val info', 'val snapshot = withContext(Dispatchers.IO) { synchronized(task.nativeLock) {\n                        require(handle.isValid) { "资源会话已结束" }\n                        val info')
s = s.replace('                        Triple(rows, native, File(handle.savePath()))\n                    }', '                        Triple(rows, native, File(handle.savePath()))\n                    } }')
s = s.replace('        runCatching { engine?.stop() }', '        synchronized(task.nativeLock) { runCatching { engine?.stop() } }')
insert = '''    fun retainCache(task: TorrentTask) {
        task.retainedCache = true
        if (task.paused || task.cancelled) resume(task)
        persist()
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
            task.job?.join()
            withContext(Dispatchers.IO) {
                closeEngine(task)
                val file = File(task.folder, previous.path).canonicalFile
                require(file.toPath().startsWith(task.folder.canonicalFile.toPath()))
                require(!file.exists() || file.delete()) { "无法释放上个视频的临时缓存" }
            }
            task.files = emptyList(); task.selected = setOf(index); task.playingIndex = index
            task.complete = false; task.streaming = true; task.moving = false
            task.paused = false; task.queued = true; task.job = null
            schedule()
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
'''
s = s.replace('    fun select(task:', insert + '    fun select(task:')
p.write_text(s, encoding='utf-8')

p = root / 'PlayerActivity.kt'
s = p.read_text(encoding='utf-8').replace('if (File(current.folder, file.path).exists()) runCatching', 'runCatching')
s = s.replace('TorrentFileDataSource({ task.handle }, { task.engine?.cacheFlushGeneration ?: -1L })', 'TorrentFileDataSource({ task.handle }, { task.engine?.cacheFlushGeneration ?: -1L }, task.nativeLock)')
s = s.replace('                                TextButton(onClick = { networkDialog = true })', '                                TextButton(onClick = { TorrentTasks.retryDiscovery(current) }) { Text("重试解析 / 连接") }\n                                TextButton(onClick = { networkDialog = true })')
s = s.replace('            PlayerView(context).apply {\n                setControllerLayoutId(R.layout.player_controls)', '            (android.view.LayoutInflater.from(context).inflate(R.layout.player_surface, null) as PlayerView).apply {')
p.write_text(s, encoding='utf-8')

layout = Path('app/src/main/res/layout')
(layout / 'player_surface.xml').write_text('''<androidx.media3.ui.PlayerView xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="match_parent" android:background="#000000" app:controller_layout_id="@layout/player_controls" app:animation_enabled="false" app:show_timeout="3500" app:shutter_background_color="#000000"/>''', encoding='utf-8')
p = layout / 'player_controls.xml'
s = p.read_text(encoding='utf-8').replace('exo_styled_controls_rewind','exo_icon_rewind').replace('exo_styled_controls_play','exo_icon_play').replace('exo_styled_controls_fastforward','exo_icon_fastforward')
s = s.replace('<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_gravity="bottom"', '<LinearLayout android:id="@+id/exo_bottom_bar" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_gravity="bottom"')
p.write_text(s, encoding='utf-8')
