package com.freemangnetplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LibraryEntry(
    val title: String,
    val source: String,
    val status: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0
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
    fun addPlayback(context: Context, title: String, source: String) {
        val values = read(context, PLAYBACKS).toMutableList()
        values.removeAll { it.source == source }
        values.add(0, LibraryEntry(title.ifBlank { "磁力资源" }, source, "最近播放"))
        write(context, PLAYBACKS, values.take(100))
    }

    @Synchronized
    fun addFavorite(context: Context, title: String, source: String) {
        val values = read(context, FAVORITES).toMutableList()
        val existing = values.indexOfFirst { it.source == source }
        val entry = LibraryEntry(title.ifBlank { "磁力资源" }, source, "已收藏")
        if (existing >= 0) values[existing] = entry else values.add(0, entry)
        write(context, FAVORITES, values)
    }

    @Synchronized
    fun updateDownload(context: Context, title: String, source: String, status: String, downloaded: Long = 0, total: Long = 0) {
        val values = read(context, DOWNLOADS).toMutableList()
        val existing = values.indexOfFirst { it.source == source }
        val entry = LibraryEntry(title.ifBlank { "磁力资源" }, source, status, downloaded.coerceAtLeast(0), total.coerceAtLeast(0))
        if (existing >= 0) values[existing] = entry else values.add(0, entry)
        write(context, DOWNLOADS, values)
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
                totalBytes = item.optLong("total")
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
                .put("total", entry.totalBytes))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, array.toString()).apply()
    }
}
