package com.omniplayer.live
import com.omniplayer.core.*
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class IptvSource(val name: String, val url: String)
data class IptvChannel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String,
    val id: String,
    val legacyId: String = ""
)
data class IptvWatch(val channel: IptvChannel, val watchedAt: Long)

object IptvSession {
    @Volatile var channels: List<IptvChannel> = emptyList()
}

object IptvStore {
    const val DEFAULT_URL = "https://testingcf.jsdelivr.net/gh/liao-zc/iptv-live@main/IPTV_ALL.m3u"
    const val IPTV_ORG_URL = "https://iptv-org.github.io/iptv/index.m3u"
    private const val PREFS = "iptv-player"
    private const val FAVORITE_ROWS = "favorite-channels"
    private const val WATCH_ROWS = "watch-history"

    fun watchHistory(context: Context): List<IptvWatch> = runCatching {
        val rows = JSONArray(context.getSharedPreferences(PREFS, 0).getString(WATCH_ROWS, "[]"))
        buildList { for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { row ->
            val channel = row.toChannel()
            if (channel.url.startsWith("http://") || channel.url.startsWith("https://")) add(IptvWatch(channel, row.optLong("watchedAt")))
        } }.sortedByDescending { it.watchedAt }
    }.getOrDefault(emptyList())

    fun recordWatch(context: Context, channel: IptvChannel) {
        val rows = (watchHistory(context).filterNot { it.channel.id == channel.id } + IptvWatch(channel, System.currentTimeMillis()))
            .sortedByDescending { it.watchedAt }.take(100)
        val json = JSONArray(); rows.forEach { json.put(it.channel.toJson().put("watchedAt", it.watchedAt)) }
        context.getSharedPreferences(PREFS, 0).edit().putString(WATCH_ROWS, json.toString()).apply()
    }

    fun removeWatchHistory(context: Context, ids: Set<String>) {
        val rows = JSONArray(); watchHistory(context).filterNot { it.channel.id in ids }.forEach { rows.put(it.channel.toJson().put("watchedAt", it.watchedAt)) }
        context.getSharedPreferences(PREFS, 0).edit().putString(WATCH_ROWS, rows.toString()).apply()
    }

    fun sources(context: Context): List<IptvSource> {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val rows = runCatching { JSONArray(prefs.getString("sources", "[]")) }.getOrDefault(JSONArray())
        val saved = buildList { for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { add(IptvSource(it.optString("name"), it.optString("url"))) } }
        if (!prefs.getBoolean("default-sources-v2-migrated", false)) {
            val migrated = (saved + listOf(IptvSource("IPTV_ALL", DEFAULT_URL), IptvSource("IPTV Org", IPTV_ORG_URL)))
                .distinctBy { it.url.trim().lowercase() }
            val editor = prefs.edit().putBoolean("default-sources-v2-migrated", true).putString("selected-source", "IPTV_ALL")
            if (saved.isNotEmpty()) {
                val json = JSONArray()
                migrated.forEach { json.put(JSONObject().put("name", it.name).put("url", it.url)) }
                editor.putString("sources", json.toString())
            }
            editor.apply()
            return migrated
        }
        return saved.ifEmpty { listOf(IptvSource("IPTV_ALL", DEFAULT_URL), IptvSource("IPTV Org", IPTV_ORG_URL)) }
    }

    fun saveSources(context: Context, sources: List<IptvSource>) {
        val rows = JSONArray()
        sources.filter { it.name.isNotBlank() && (it.url.startsWith("https://", true) || it.url.startsWith("http://", true) || it.url.startsWith("file:", true)) }.forEach { rows.put(JSONObject().put("name", it.name.trim()).put("url", it.url.trim())) }
        context.getSharedPreferences(PREFS, 0).edit().putString("sources", rows.toString()).apply()
    }

    fun selectedSource(context: Context): String? = context.getSharedPreferences(PREFS, 0).getString("selected-source", null)
    fun selectSource(context: Context, name: String) { context.getSharedPreferences(PREFS, 0).edit().putString("selected-source", name).apply() }

    fun favoriteChannels(context: Context): List<IptvChannel> = runCatching {
        val rows = JSONArray(context.getSharedPreferences(PREFS, 0).getString(FAVORITE_ROWS, "[]"))
        buildList { for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { row ->
            val url = row.optString("url")
            if (url.startsWith("http://") || url.startsWith("https://")) add(IptvChannel(row.optString("name"), url, row.optString("group", "其他"), row.optString("logo"), row.optString("id"), row.optString("legacyId")))
        } }
    }.getOrDefault(emptyList())

    fun favorites(context: Context): Set<String> = runCatching {
        val a = JSONArray(context.getSharedPreferences(PREFS, 0).getString("favorites", "[]"))
        ((0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } + favoriteChannels(context).map { it.id }).toSet()
    }.getOrDefault(emptySet())

    fun migrateKnownFavorites(context: Context, channels: List<IptvChannel>) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val existing = favoriteChannels(context).toMutableList()
        val storedIds = JSONArray(prefs.getString("favorites", "[]"))
        val oldIds = (0 until storedIds.length()).mapNotNull { storedIds.optString(it).takeIf(String::isNotBlank) }.toMutableSet()
        oldIds.removeAll(existing.map { it.id }.toSet())
        val found = channels.filter { it.legacyId.isNotBlank() && it.legacyId in oldIds && existing.none { saved -> saved.id == it.id } }
        if (found.isNotEmpty()) {
            existing.addAll(found); oldIds.removeAll(found.map { it.legacyId }.toSet())
            val rows = JSONArray(); existing.forEach { rows.put(it.toJson()) }
            val ids = JSONArray(); (oldIds + existing.map { it.id }).distinct().forEach(ids::put)
            prefs.edit().putString(FAVORITE_ROWS, rows.toString()).putString("favorites", ids.toString()).apply()
        }
    }

    fun setFavorite(context: Context, channel: IptvChannel, favorite: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val channels = favoriteChannels(context).toMutableList()
        channels.removeAll { it.id == channel.id }
        if (favorite) channels.add(channel)
        val storedIds = JSONArray(prefs.getString("favorites", "[]"))
        val oldIds = (0 until storedIds.length()).mapNotNull { storedIds.optString(it).takeIf(String::isNotBlank) }.toMutableSet()
        oldIds.removeAll(favoriteChannels(context).map { it.id }.toSet())
        oldIds.remove(channel.legacyId)
        val rows = JSONArray(); channels.forEach { rows.put(it.toJson()) }
        val ids = JSONArray(); (oldIds + channels.map { it.id }).distinct().forEach(ids::put)
        prefs.edit().putString(FAVORITE_ROWS, rows.toString()).putString("favorites", ids.toString()).apply()
    }

    fun parseM3u(text: String, sourceName: String): List<IptvChannel> {
        val out = mutableListOf<IptvChannel>()
        var title = ""
        var group = "其他"
        var logo = ""
        var id = ""
        text.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            if (line.startsWith("#EXTINF", true)) {
                title = line.substringAfter(',', "").trim().ifBlank { "未命名频道" }
                group = attr(line, "group-title").ifBlank { "其他" }
                logo = attr(line, "tvg-logo")
                id = attr(line, "tvg-id")
            } else if (!line.startsWith('#') && (line.startsWith("http://", true) || line.startsWith("https://", true))) {
                val channelName = title.ifBlank { line.substringAfterLast('/').substringBefore('?').ifBlank { "未命名频道" } }
                val digest = MessageDigest.getInstance("SHA-256").digest("${id.ifBlank { channelName }}\u0000$line".toByteArray(Charsets.UTF_8))
                val key = digest.take(12).joinToString("") { "%02x".format(it) }
                out.add(IptvChannel(channelName, line, group, logo, key, id))
                title = ""; group = "其他"; logo = ""; id = ""
            }
        }
        return out
    }

    private fun attr(line: String, key: String): String {
        val regex = Regex("(?:^|\\s)${Regex.escape(key)}=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun IptvChannel.toJson() = JSONObject().put("id", id).put("legacyId", legacyId).put("name", name).put("url", url).put("group", group).put("logo", logo)
    private fun JSONObject.toChannel() = IptvChannel(optString("name"), optString("url"), optString("group", "其他"), optString("logo"), optString("id"), optString("legacyId"))
}
