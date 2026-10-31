package com.omniplayer.magnet
import com.omniplayer.core.*
import android.content.Context
import org.libtorrent4j.TcpEndpoint
import org.libtorrent4j.TorrentHandle
import org.json.JSONArray
import org.json.JSONObject

/** Peers are resource-specific; arbitrary BT peers cannot serve unrelated hashes. */
internal object PeerPool {
    private val lastAttempt=mutableMapOf<String,Long>()
    private const val TTL=7L*24*60*60*1000
    @Synchronized fun capture(c: Context,id: String,handle: TorrentHandle) {
        val now=System.currentTimeMillis()
        val a=JSONArray();handle.peerInfo().take(128).forEach { peer ->
            val endpoint=peer.ip();val port=endpoint.substringAfterLast(':').toIntOrNull()
            if(port!=null && port in 1..65535) a.put(JSONObject().put("endpoint",endpoint).put("time",now))
        }
        if(a.length()>0) SettingsStore.prefs(c).edit().putString("peers-$id",a.toString()).apply()
    }
    @Synchronized fun reconnect(c: Context,id: String,handle: TorrentHandle): Int {
        val now=System.currentTimeMillis();if(now-(lastAttempt[id] ?: 0)<30000)return 0
        lastAttempt[id]=now
        val a=runCatching { JSONArray(SettingsStore.prefs(c).getString("peers-$id","[]")) }.getOrDefault(JSONArray());var added=0
        for(i in 0 until a.length()) runCatching {
            val row=a.getJSONObject(i);if(now-row.getLong("time")>TTL)return@runCatching
            val endpoint=row.getString("endpoint")
            handle.swig().connect_peer(TcpEndpoint(endpoint.substringBeforeLast(':').removePrefix("[").removeSuffix("]"),endpoint.substringAfterLast(':').toInt()).swig());added++
        }
        return added
    }
}
