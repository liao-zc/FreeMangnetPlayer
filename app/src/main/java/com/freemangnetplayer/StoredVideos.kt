package com.freemangnetplayer

import android.content.Context

object StoredVideos {
    fun fileSource(task: TorrentTask, file: TaskFile): String =
        task.source.split('&').filterNot { it.startsWith("so=", true) || it.startsWith("dn=", true) }.joinToString("&") +
            "&so=${file.index}&dn=${java.net.URLEncoder.encode(file.path.substringAfterLast('/'), "UTF-8") }"

    fun sync(context: Context, task: TorrentTask) {
        for(kind in listOf("??","??")) HistoryStore.update(context,kind,HistoryEntry(task.title,"magnet:?xt=urn:btih:${task.id}",0,task.status,done=task.done,size=task.total))
        if (!task.retainedCache || task.clearing || task.stopping) return
        if (task.files.isEmpty()) {
            LibraryStore.saveCache(context, LibraryEntry(task.title, task.source, "缓存任务，打开后恢复文件列表"))
            return
        }
        LibraryStore.removeCachePlaceholder(context, task.source)
        for (file in task.files.filter { it.video && it.index in task.selected && it.index !in task.deletedFiles }) {
            LibraryStore.saveCache(context, LibraryEntry(file.path.substringAfterLast('/'), fileSource(task, file),
                if (file.done >= file.size) "缓存完成" else "缓存中，可继续播放", file.done, file.size))
        }
    }

    fun queue(current: TorrentTask): List<Pair<TorrentTask, TaskFile>> = TorrentTasks.tasks.values
        .filter { it === current || it.retainedCache || it.download }
        .flatMap { task -> task.files.filter { file ->
            file.video && file.index !in task.deletedFiles &&
                file.done > 0
        }.map { task to it } }
        .sortedWith(compareBy<Pair<TorrentTask, TaskFile>> { it.first !== current }.thenBy { it.second.path })

    fun delete(context: Context, kind: String, sources: Set<String>, deleteData: Boolean): String? {
        val grouped = sources.groupBy { source -> Regex("(?i)btih:([a-f0-9]{40})").find(source)?.groupValues?.get(1)?.lowercase() }
        if (deleteData && grouped.keys.any { id -> TorrentTasks.tasks[id]?.let { it.stopping || it.clearing || it.moving } == true }) return "文件正在移动或清理，请稍后重试"
        if (deleteData && grouped.keys.any { (TorrentTasks.tasks[it]?.viewers ?: 0) > 0 }) return "请先退出所选视频的播放器，再删除文件"
        if (deleteData) for ((id, links) in grouped) {
            val task = TorrentTasks.tasks[id] ?: continue
            val indices = links.mapNotNull { Regex("[?&]so=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }.toSet()
            if (indices.isEmpty()) TorrentTasks.cancel(task, true) else TorrentTasks.deleteFiles(task, indices)
        }
        LibraryStore.removeEntries(context, kind, sources)
        return null
    }
}
