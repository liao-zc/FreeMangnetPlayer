package com.omniplayer.core
import android.content.Context
import org.json.JSONObject

/** Save observed media headers, never infer duration or resolution from torrent size. */
internal object MediaFacts {
    fun read(c: Context,source: String): Pair<Long,Int> = runCatching {
        val row=JSONObject(SettingsStore.prefs(c).getString("media-facts","{}")).optJSONObject(source)
        (row?.optLong("duration") ?: 0L) to (row?.optInt("height") ?: 0)
    }.getOrDefault(0L to 0)
    @Synchronized fun save(c: Context,source: String,duration: Long,height: Int) {
        if(duration<=0 && height<=0)return
        val old=read(c,source);val value=(duration.takeIf { it>0 } ?: old.first) to (height.takeIf { it>0 } ?: old.second)
        if(old==value)return
        val all=runCatching { JSONObject(SettingsStore.prefs(c).getString("media-facts","{}")) }.getOrDefault(JSONObject())
        all.put(source,JSONObject().put("duration",value.first).put("height",value.second).put("time",System.currentTimeMillis()))
        if(all.length()>200) all.keys().asSequence().minByOrNull { all.getJSONObject(it).optLong("time") }?.let { all.remove(it) }
        SettingsStore.prefs(c).edit().putString("media-facts",all.toString()).apply()
    }
}
