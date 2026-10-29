from pathlib import Path
root=Path('app/src/main/java/com/freemangnetplayer')
p=root/'TorrentTasks.kt';s=p.read_text(encoding='utf-8')
s=s.replace('        val task = tasks[id] ?: run {', '        require(tasks[id]?.clearing != true) { "当前资源正在清理，请稍后重试解析" }\n        val task = tasks[id] ?: run {')
s=s.replace('if (task.cancelled && !task.stopping && !task.clearing)', 'if (task.cancelled && !task.clearing)')
s=s.replace('        if (download) {\n', '        if (task.paused && task.files.isEmpty() && !task.clearing) { task.paused = false; task.queued = true }\n        if (download) {\n',1)
s=s.replace('    private fun resumeEngine(task: TorrentTask) {', '    private fun resumeEngine(task: TorrentTask) {\n        if (task.stopping || task.clearing || task.moving) return')
s=s.replace('        task.paused = false\n        val active', '''        if (task.clearing) return
        task.cancelled = false; task.paused = false
        if (task.stopping || task.moving) { task.queued = true; persist(); return }
        val active''')
p.write_text(s,encoding='utf-8')
p=root/'PlayerActivity.kt';s=p.read_text(encoding='utf-8')
s=s.replace('        task?.let { TorrentTasks.releaseOtherTemporaryTasks(it) }', '        task?.let { TorrentTasks.releaseOtherTemporaryTasks(it) }')
s=s.replace('        autoPlay = savedInstanceState?.getBoolean("autoplay") ?: true', '        if (requested) task?.let { if (it.paused || it.cancelled) TorrentTasks.resume(it) }\n        autoPlay = savedInstanceState?.getBoolean("autoplay") ?: true')
s=s.replace('        task?.let { current ->\n            if (current.selectionPaused)', '        task?.let { current ->\n            if (play && (current.paused || current.cancelled)) TorrentTasks.resume(current)\n            if (current.selectionPaused)')
p.write_text(s,encoding='utf-8')
p=root/'SettingsActivity.kt';s=p.read_text(encoding='utf-8')
s=s.replace('超出并发数的任务排队；缓存达到上限时清除最早使用且未打开的缓存任务。正在播放的任务会保留。', '超出并发数的任务排队；缓存上限限制新增主动缓存。切换资源会清理临时播放数据，主动缓存和下载文件保留。')
s=s.replace('缓存任务：${TorrentTasks.tasks.values.count { !it.download && !it.clearing }}', '已保存缓存：${TorrentTasks.tasks.values.count { !it.download && it.retainedCache && !it.clearing }} · 临时播放：${TorrentTasks.tasks.values.count { !it.download && !it.retainedCache && !it.clearing }}')
p.write_text(s,encoding='utf-8')
