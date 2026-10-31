package com.omniplayer.magnet
import com.omniplayer.core.*
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LibraryEntry(
    val title: String,
    val source: String,
    val status: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val position: Long = 0,
    val duration: Long = 0,
    val unplayableReason: String = ""
)

object LibraryStore {
    private const val PREFS = "player-settings"
    private const val DOWNLOADS = "download-entries"
    private const val FAVORITES = "favorite-entries"
    private const val PLAYBACKS = "playback-entries"

    @Synchronized
    fun readDownloads(context: Context): List<LibraryEntry> = read(context, DOWNLOADS)

    @Synchronized
    fun readFavorites(context: Context): List<LibraryEntry> = read(context, FAVORITES)

    @Synchronized
    fun readPlaybacks(context: Context): List<LibraryEntry> = read(context, PLAYBACKS)

    @Synchronized
    fun playbackPosition(context: Context, taskId: String, fileIndex: Int): Long {
        return playback(context, taskId, fileIndex)?.position?.coerceAtLeast(0) ?: 0L
    }

    @Synchronized
    fun playbackDuration(context: Context, taskId: String, fileIndex: Int): Long =
        playback(context, taskId, fileIndex)?.duration?.coerceAtLeast(0) ?: 0L

    private fun playback(context: Context, taskId: String, fileIndex: Int): LibraryEntry? {
        val idPattern = Regex("(?i)btih:${Regex.escape(taskId)}(?:[&#]|$)")
        return read(context, PLAYBACKS).firstOrNull { entry ->
            idPattern.containsMatchIn(entry.source) &&
                Regex("(?i)(?:[?&])so=$fileIndex(?:&|$)").containsMatchIn(entry.source)
        }
    }

    @Synchronized
    fun addPlayback(context: Context, title: String, source: String, position: Long = 0, duration: Long = 0) {
        HistoryStore.record(context,"播放",HistoryEntry(title,source,System.currentTimeMillis(),position=position,duration=duration))
        val values = read(context, PLAYBACKS).toMutableList()
        values.removeAll { it.source == source }
        val warning = taskFor(source)?.unplayableReason.orEmpty()
        values.add(0, LibraryEntry(title.ifBlank { "磁力资源" }, source, warning.ifBlank { "最近播放" },position=position,duration=duration,unplayableReason=warning))
        write(context, PLAYBACKS, values.take(100))
    }

    @Synchronized
    fun addFavorite(context: Context, title: String, source: String) {
        val values = read(context, FAVORITES).toMutableList()
        val existing = values.indexOfFirst { it.source == source }
            val warning = taskFor(source)?.unplayableReason.orEmpty()
            val entry = LibraryEntry(title.ifBlank { "磁力资源" }, source, warning.ifBlank { "已收藏" }, unplayableReason = warning)
        if (existing >= 0) values[existing] = entry else values.add(0, entry)
        write(context, FAVORITES, values)
    }

    @Synchronized
    fun updateDownload(context: Context, title: String, source: String, status: String, downloaded: Long = 0, total: Long = 0) {
        val hash = Regex("(?i)btih:([a-f0-9]{40})").find(source)?.groupValues?.get(1)?.lowercase()
        val task = TorrentTasks.tasks[hash]
        if (task != null && task.files.isNotEmpty()) {
            val rows = read(context, DOWNLOADS).filterNot { it.source.contains("btih:${task.id}", true) }.toMutableList()
            task.files.filter { it.index in task.selected && it.index !in task.deletedFiles }.forEach { file ->
                val warning = task.unplayableReason
                rows.add(LibraryEntry(file.path.substringAfterLast('/'), StoredVideos.fileSource(task, file), warning.ifBlank { status }, file.done, file.size, unplayableReason = warning))
            }
            write(context, DOWNLOADS, rows); return
        }
        val values = read(context, DOWNLOADS).toMutableList()
        val existing = values.indexOfFirst { it.source == source }
        val entry = LibraryEntry(title.ifBlank { "磁力资源" }, source, status, downloaded.coerceAtLeast(0), total.coerceAtLeast(0))
        if (existing >= 0) values[existing] = entry else values.add(0, entry)
        write(context, DOWNLOADS, values)
    }

    @Synchronized
    fun removeDownload(context: Context, source: String) {
        write(context, DOWNLOADS, read(context, DOWNLOADS).filter { it.source != source })
    }

    @Synchronized
    fun readCaches(context: Context): List<LibraryEntry> = read(context, "cache-entries").sortedByDescending { it.time }
    @Synchronized
    fun saveCache(context: Context, entry: LibraryEntry) {
        if (readCaches(context).any { it == entry }) return
        val rows = readCaches(context).filter { it.source != entry.source }.toMutableList()
        val previous=readCaches(context).firstOrNull { it.source==entry.source }; rows.add(0, entry.copy(time=previous?.time ?: entry.time)); write(context, "cache-entries", rows)
    }
    @Synchronized
    fun removeEntries(context: Context, kind: String, sources: Set<String>) {
        val key = when (kind) { "缓存" -> "cache-entries"; "下载" -> DOWNLOADS; "收藏" -> FAVORITES; else -> PLAYBACKS }
        write(context, key, read(context, key).filter { it.source !in sources })
    }
    @Synchronized
    fun removeFileReferences(context: Context, id: String, indices: Set<Int>) {
        for (key in listOf("cache-entries", DOWNLOADS, PLAYBACKS, FAVORITES)) write(context, key, read(context, key).filterNot {
            it.source.contains("btih:$id", true) && Regex("[?&]so=(\\d+)").find(it.source)?.groupValues?.get(1)?.toIntOrNull() in indices
        })
    }

    @Synchronized
    fun removeTaskCaches(context: Context, id: String) {
        write(context, "cache-entries", readCaches(context).filterNot { it.source.contains("btih:$id", true) })
    }
    @Synchronized
    fun markUnplayable(context: Context, task: TorrentTask, reason: String) {
        for (key in listOf(DOWNLOADS, FAVORITES, PLAYBACKS, "cache-entries")) {
            val rows = read(context, key).map { entry ->
                if (entry.source.contains("btih:${task.id}", true)) entry.copy(status = reason, unplayableReason = reason) else entry
            }
            write(context, key, rows)
        }
    }

    @Synchronized
    fun clearUnplayable(context: Context, task: TorrentTask) {
        for (key in listOf(DOWNLOADS, FAVORITES, PLAYBACKS, "cache-entries")) {
            val rows = read(context, key).map { entry ->
                if (entry.source.contains("btih:${task.id}", true)) entry.copy(status = "已重新尝试连接", unplayableReason = "") else entry
            }
            write(context, key, rows)
        }
    }

    private fun taskFor(source: String): TorrentTask? {
        val hash = Regex("(?i)btih:([a-f0-9]{40})").find(source)?.groupValues?.get(1)?.lowercase() ?: return null
        return TorrentTasks.tasks[hash]
    }
    @Synchronized
    fun removeCachePlaceholder(context: Context, source: String) {
        val rows = readCaches(context)
        if (rows.any { it.source == source && it.totalBytes == 0L }) write(context, "cache-entries", rows.filterNot { it.source == source && it.totalBytes == 0L })
    }
    private fun read(context: Context, key: String): List<LibraryEntry> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            LibraryEntry(
                title = item.optString("title"),
                source = item.optString("source"),
                status = item.optString("status"),
                downloadedBytes = item.optLong("downloaded"),
                totalBytes = item.optLong("total"), time=item.optLong("time",0), position=item.optLong("position"), duration=item.optLong("duration")
                , unplayableReason = item.optString("unplayableReason")
            ).takeIf { it.source.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, key: String, values: List<LibraryEntry>) {
        val array = JSONArray()
        values.forEach { entry ->
            array.put(JSONObject()
                .put("title", entry.title)
                .put("source", entry.source)
                .put("status", entry.status)
                .put("downloaded", entry.downloadedBytes)
                .put("total", entry.totalBytes).put("time",entry.time).put("position",entry.position).put("duration",entry.duration)
                .put("unplayableReason", entry.unplayableReason))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, array.toString()).apply()
        if(key==DOWNLOADS || key=="cache-entries") values.forEach { entry -> HistoryStore.update(context,if(key==DOWNLOADS) "下载" else "缓存", HistoryEntry(entry.title,entry.source,entry.time,entry.status,done=entry.downloadedBytes,size=entry.totalBytes)) }
    }
}
