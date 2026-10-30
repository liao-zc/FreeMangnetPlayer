package com.freemangnetplayer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

object SearchEngineStore {
    private const val KEY = "search-engines-v1"
    private const val DEFAULT_NAME = "BD电影首发站"

    fun defaultName() = DEFAULT_NAME

    @Synchronized
    fun load(context: Context): List<SearchProvider> {
        val prefs = SettingsStore.prefs(context)
        val raw = prefs.getString(KEY, null)
        if (raw != null) {
            val loaded = runCatching {
                val rows = JSONArray(raw)
                (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let { row ->
                    SearchProvider(row.optString("name"), row.optString("homepage"), row.optString("queryUrl").takeIf(String::isNotBlank))
                } }
            }.getOrDefault(emptyList()).filter(::valid).take(10)
            if (loaded.isNotEmpty()) return loaded
        }
        val legacyName = prefs.getString("custom-name", "")?.trim().orEmpty()
        val legacyUrl = prefs.getString("custom-url", "")?.trim().orEmpty()
        val migrated = (builtInProviders + if (legacyName.isNotBlank() && (legacyUrl.startsWith("https://") || legacyUrl.startsWith("http://")))
            listOf(SearchProvider(legacyName, legacyUrl, legacyUrl.takeIf { it.contains("%s") })) else emptyList()).distinctBy { it.name }.take(10)
        save(context, migrated, DEFAULT_NAME)
        return migrated
    }

    @Synchronized
    fun save(context: Context, engines: List<SearchProvider>, defaultName: String) {
        require(engines.size in 1..10) { "搜索引擎数量需为 1–10 个" }
        require(engines.all(::valid)) { "每个引擎都需要名称和有效的 HTTP(S) 地址" }
        require(engines.map { it.name.trim().lowercase() }.distinct().size == engines.size) { "引擎名称不能重复" }
        val rows = JSONArray()
        engines.forEach { rows.put(JSONObject().put("name", it.name.trim()).put("homepage", it.homepage.trim()).put("queryUrl", it.queryUrl?.trim().orEmpty())) }
        val selected = engines.firstOrNull { it.name == defaultName } ?: engines.first()
        SettingsStore.prefs(context).edit().putString(KEY, rows.toString())
            .putString("search-name", selected.name).putString("search-url", selected.homepage).apply()
    }

    private fun valid(engine: SearchProvider) = engine.name.isNotBlank() &&
        (engine.homepage.startsWith("https://", true) || engine.homepage.startsWith("http://", true)) &&
        (engine.queryUrl.isNullOrBlank() || engine.queryUrl.startsWith("https://", true) || engine.queryUrl.startsWith("http://", true))
}

class SearchEngineSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = SettingsStore.prefs(this)
        setContent {
            AppTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF087F79))) {
                val engines = remember { mutableStateListOf<SearchProvider>().apply { addAll(SearchEngineStore.load(this@SearchEngineSettingsActivity)) } }
                var defaultName by remember { mutableStateOf(prefs.getString("search-name", SearchEngineStore.defaultName()) ?: SearchEngineStore.defaultName()) }
                var message by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { finish() }) { Text("‹ 返回磁播设置") }
                        Text("搜索引擎管理", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("可编辑或删除当前引擎，最多 10 个。搜索地址模板可用 %s 表示关键词；留空时会尝试填写网页搜索框。", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        if (engines.size >= 10) message = "最多只能配置 10 个搜索引擎"
                        else {
                            val number = engines.size + 1
                            engines.add(SearchProvider("自定义引擎 $number", "https://example.com/", "https://example.com/?q=%s"))
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("添加搜索引擎（${engines.size}/10）") }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        engines.forEachIndexed { index, row ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { defaultName = row.name }) {
                                        RadioButton(defaultName == row.name, { defaultName = row.name })
                                        Text(if (defaultName == row.name) "默认 · ${row.name}" else row.name, Modifier.weight(1f))
                                        TextButton(onClick = {
                                            if (engines.size <= 1) message = "至少保留一个搜索引擎"
                                            else { engines.removeAt(index); if (defaultName == row.name) defaultName = engines.first().name }
                                        }) { Text("删除") }
                                    }
                                    OutlinedTextField(row.name, { engines[index] = engines[index].copy(name = it); if (defaultName == row.name) defaultName = it }, label = { Text("引擎名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(row.homepage, { engines[index] = engines[index].copy(homepage = it) }, label = { Text("网站首页地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(row.queryUrl.orEmpty(), { engines[index] = engines[index].copy(queryUrl = it.ifBlank { null }) }, label = { Text("搜索地址模板（%s 代表关键词，可留空）") }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                    Button(onClick = {
                        runCatching { SearchEngineStore.save(this@SearchEngineSettingsActivity, engines.toList(), defaultName); message = "已保存 ${engines.size} 个搜索引擎，默认：$defaultName" }
                            .onFailure { message = "保存失败：${it.localizedMessage}" }
                    }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("保存搜索引擎") }
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
