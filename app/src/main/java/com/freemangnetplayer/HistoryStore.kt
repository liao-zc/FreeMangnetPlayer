package com.freemangnetplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class HistoryEntry(val title: String, val source: String, val time: Long,
    val status: String = "", val position: Long = 0, val duration: Long = 0, val done: Long = 0, val size: Long = 0)

object HistoryStore {
    val kinds = listOf("搜索", "播放", "下载", "缓存")
    private fun key(kind: String) = "history-$kind"
    @Synchronized fun read(c: Context, kind: String): List<HistoryEntry> = runCatching {
        val a=JSONArray(SettingsStore.prefs(c).getString(key(kind),"[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            HistoryEntry(it.getString("title"),it.getString("source"),it.getLong("time"),it.optString("status"),it.optLong("position"),it.optLong("duration"),it.optLong("done"),it.optLong("size"))
        }.sortedByDescending { it.time }
    }.getOrDefault(emptyList())
    private fun write(c: Context, kind: String, rows: List<HistoryEntry>) {
        val a=JSONArray();rows.forEach { a.put(JSONObject().put("title",it.title).put("source",it.source).put("time",it.time).put("status",it.status).put("position",it.position).put("duration",it.duration).put("done",it.done).put("size",it.size)) }
        SettingsStore.prefs(c).edit().putString(key(kind),a.toString()).apply()
    }
    @Synchronized fun record(c: Context, kind: String, entry: HistoryEntry) {
        val limit=when(kind) { "搜索" -> 50; "播放" -> 100; "下载" -> 50; else -> 30 }
        write(c,kind,(listOf(entry)+read(c,kind).filter { it.source!=entry.source }).sortedByDescending { it.time }.take(limit))
    }
    @Synchronized fun update(c: Context, kind: String, entry: HistoryEntry) {
        val old=read(c,kind).firstOrNull { it.source==entry.source } ?: return
        val value=entry.copy(time=old.time)
        if(value!=old) record(c,kind,value)
    }
    @Synchronized fun clear(c: Context, kind: String?) { if(kind==null) kinds.forEach { clear(c,it) } else write(c,kind,emptyList()) }
    @Synchronized fun remove(c: Context, kind: String, source: String) = write(c,kind,read(c,kind).filter { it.source!=source })
}

@Composable internal fun HistoryScreen(onBack: () -> Unit, onOpen: (String, HistoryEntry) -> Unit) {
    val c=LocalContext.current;var kind by remember { mutableStateOf("搜索") };var revision by remember { mutableIntStateOf(0) };var clear by remember { mutableStateOf(false) };var all by remember { mutableStateOf(false) }
    val rows=remember(kind,revision) { HistoryStore.read(c,kind) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row { TextButton(onClick=onBack) { Text("‹ 返回") };Text("历史记录",style=MaterialTheme.typography.headlineSmall) }
        Row { HistoryStore.kinds.forEach { type -> TextButton(onClick={kind=type;revision++}) { Text(if(kind==type) "✓ $type" else type) } } }
        Row { TextButton(onClick={ all=false;clear=true }) { Text("清空${kind}记录") };TextButton(onClick={all=true;clear=true}) { Text("清空全部记录") } }
        if(rows.isEmpty()) Text("暂无${kind}记录")
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { items(rows,key={it.source}) { entry -> Card {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(entry.title);Text(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.CHINA).format(java.util.Date(entry.time)),style=MaterialTheme.typography.bodySmall)
                if(kind=="播放") Text("进度 ${historyTime(entry.position)} / ${historyTime(entry.duration)}")
                if(kind=="缓存" || kind=="下载") Text("${entry.status} · ${if(entry.size>0) (entry.done*100/entry.size).coerceIn(0,100).toString()+"%" else "等待元数据"}")
                Row { TextButton(onClick={onOpen(kind,entry)}) { Text(if(kind=="搜索") "再次搜索" else "打开") };TextButton(onClick={HistoryStore.remove(c,kind,entry.source);revision++}) { Text("删除记录") } }
            }
        } } }
    }
    if(clear) AlertDialog(onDismissRequest={clear=false},title={Text(if(all) "清空全部历史？" else "清空${kind}历史？")},text={Text("仅删除行为记录，保留下载、缓存文件和收藏。")},confirmButton={TextButton(onClick={HistoryStore.clear(c,if(all)null else kind);revision++;clear=false}) { Text("确认清空") }},dismissButton={TextButton(onClick={clear=false}) { Text("取消") }})
}
internal fun historyTime(value: Long): String = "%02d:%02d:%02d".format(value.coerceAtLeast(0)/3600000,value.coerceAtLeast(0)/60000%60,value.coerceAtLeast(0)/1000%60)
