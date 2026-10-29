from pathlib import Path
root=Path('app/src/main/java/com/freemangnetplayer')
p=root/'TorrentTasks.kt';s=p.read_text(encoding='utf-8')
s=s.replace('            task.cancelled = row.optBoolean("cancelled")', '''            task.cancelled = row.optBoolean("cancelled")
            if (!task.download && !task.retainedCache) {
                task.cancelled = true; task.paused = true
            }''')
s=s.replace('tasks.values.filter { !it.clearing && (it.download || it.retainedCache) }.forEach', 'tasks.values.filter { !it.clearing }.forEach')
s=s.replace('    fun retainCache(task: TorrentTask) {\n        task.retainedCache = true', '''    fun retainCache(task: TorrentTask): Boolean {
        if (!task.retainedCache && !task.download && tasks.values.count { it.retainedCache && !it.download && !it.clearing } >= SettingsStore.maxCaches(context)) return false
        task.retainedCache = true''')
s=s.replace('        if (task.paused || task.cancelled) resume(task)\n        persist()\n    }', '        if (task.paused || task.cancelled) resume(task)\n        persist()\n        return true\n    }',1)
s=s.replace('tasks.values.filter { !it.download && !it.clearing }.sortedBy', 'tasks.values.filter { !it.download && !it.retainedCache && !it.clearing }.sortedBy')
a=s.index('        scope.launch {\n            task.job?.join()',s.index('    fun switchStreamFile'))
b=s.index('\n    fun retryDiscovery',a)
block=s[a:b]
block=block.replace('        scope.launch {\n', '        scope.launch {\n            try {\n',1)
block=block.replace('            schedule()\n        }', '''            schedule()
            } catch (error: Exception) {
                task.moving = false; task.paused = true
                task.status = "切换失败：${error.localizedMessage}，可以返回或重试"
                persist()
            }
        }''')
s=s[:a]+block+s[b:]
p.write_text(s,encoding='utf-8')
p=root/'PlayerActivity.kt';s=p.read_text(encoding='utf-8')
s=s.replace('OutlinedButton(onClick = { TorrentTasks.retainCache(current); playerMessage = "已保存为缓存，切换资源后继续保留" }', '''OutlinedButton(onClick = {
                                playerMessage = if (TorrentTasks.retainCache(current)) "已保存为缓存，切换资源后继续保留" else "已达到最大缓存数量，请清理缓存或修改设置"
                                android.widget.Toast.makeText(this@PlayerActivity, playerMessage, android.widget.Toast.LENGTH_SHORT).show()
                            }''')
s=s.replace('if (attached && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {', 'if (attached && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) { runCatching {')
s=s.replace('File(filesDir, "tasks-check.json").writeText(tasks.toString())\n                                }', 'File(filesDir, "tasks-check.json").writeText(tasks.toString())\n                                }.onFailure { android.util.Log.w("FreeMangnetPlayer", "Unable to update debug state", it) } }')
p.write_text(s,encoding='utf-8')
