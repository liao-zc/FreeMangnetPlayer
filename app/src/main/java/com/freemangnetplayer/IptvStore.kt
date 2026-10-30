package com.freemangnetplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class IptvSource(val name: String, val url: String)
data class IptvChannel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String,
    val id: String
)

object IptvStore {
    const val DEFAULT_URL = "https://iptv-org.github.io/iptv/index.m3u"
    private const val PREFS = "iptv-player"

    fun sources(context: Context): List<IptvSource> {
        val rows = runCatching { JSONArray(context.getSharedPreferences(PREFS, 0).getString("sources", "[]")) }.getOrDefault(JSONArray())
        val saved = buildList { for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { add(IptvSource(it.optString("name"), it.optString("url"))) } }
        return saved.ifEmpty { listOf(IptvSource("IPTV Org", DEFAULT_URL)) }
    }

    fun saveSources(context: Context, sources: List<IptvSource>) {
        val rows = JSONArray()
        sources.filter { it.name.isNotBlank() && (it.url.startsWith("https://", true) || it.url.startsWith("http://", true)) }.forEach { rows.put(JSONObject().put("name", it.name.trim()).put("url", it.url.trim())) }
        context.getSharedPreferences(PREFS, 0).edit().putString("sources", rows.toString()).apply()
    }

    fun favorites(context: Context): Set<String> = runCatching {
        val a = JSONArray(context.getSharedPreferences(PREFS, 0).getString("favorites", "[]"))
        (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }.toSet()
    }.getOrDefault(emptySet())

    fun setFavorite(context: Context, channel: IptvChannel, favorite: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val values = favorites(context).toMutableSet()
        if (favorite) values.add(channel.id) else values.remove(channel.id)
        prefs.edit().putString("favorites", JSONArray(values.toList()).toString()).apply()
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
                val key = id.ifBlank { "$sourceName|$channelName|$line" }
                out.add(IptvChannel(channelName, line, group, logo, key))
                title = ""; group = "其他"; logo = ""; id = ""
            }
        }
        return out
    }

    private fun attr(line: String, key: String): String {
        val regex = Regex("(?:^|\\s)${Regex.escape(key)}=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.getOrNull(1).orEmpty()
    }
}
