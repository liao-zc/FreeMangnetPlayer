package com.freemangnetplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = SettingsStore.prefs(this)
        TorrentTasks.initialize(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF087F79))) {
                var download by remember { mutableStateOf(SettingsStore.root(this, true).absolutePath) }
                var cache by remember { mutableStateOf(SettingsStore.root(this, false).absolutePath) }
                var downloads by remember { mutableStateOf(SettingsStore.maxDownloads(this).toString()) }
                var caches by remember { mutableStateOf(SettingsStore.maxCaches(this).toString()) }
                var customName by remember { mutableStateOf(prefs.getString("custom-name", "自定义搜索") ?: "自定义搜索") }
                var customUrl by remember { mutableStateOf(prefs.getString("custom-url", "") ?: "") }
                var engine by remember { mutableStateOf(prefs.getString("search-name", "clg.im") ?: "clg.im") }
                var message by remember { mutableStateOf("") }
                var menu by remember { mutableStateOf(false) }
                var clearType by remember { mutableStateOf<Boolean?>(null) }
                var clearingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
                var waitingForClear by remember { mutableStateOf(false) }
                var skipped by remember { mutableIntStateOf(0) }
                val pendingClear = TorrentTasks.tasks.values.count { it.id in clearingIds && it.clearing }
                val failedClear = TorrentTasks.tasks.values.count { it.id in clearingIds && it.status.startsWith("清理失败") }
                LaunchedEffect(waitingForClear, pendingClear, failedClear) {
                    if (waitingForClear && pendingClear == 0) {
                        message = if (failedClear > 0) "清理结束，$failedClear 个任务清理失败，请在任务页查看原因" else "清理完成；$skipped 个正在打开的任务已保留"
                        waitingForClear = false
                    }
                }
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { finish() }, modifier = Modifier.height(52.dp)) { Text("返回首页") }
                        Text("个性化设置", style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("存储位置", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(download, { download = it }, label = { Text("下载目录（绝对路径）") }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "下载目录输入框" })
                        OutlinedTextField(cache, { cache = it }, label = { Text("缓存目录（绝对路径）") }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "缓存目录输入框" })
                        Text("可填写应用有权写入的真实路径，例如下列目录的子目录。保存时实际创建测试文件。Android 的公共目录受系统权限限制，不能填写 content:// 地址。修改目录用于新任务，旧任务保留原地址。", style = MaterialTheme.typography.bodySmall)
                        getExternalFilesDirs(null).filterNotNull().forEach { base ->
                            OutlinedButton(onClick = { download = "${base.absolutePath}/MyDownloads"; cache = "${base.absolutePath}/MyCache" }, modifier = Modifier.fillMaxWidth()) { Text("使用 ${base.absolutePath}") }
                        }
                        OutlinedTextField(downloads, { downloads = it }, label = { Text("最大同时下载数量（1–8，含缓存）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(caches, { caches = it }, label = { Text("最大缓存任务数量（1–100）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("超出并发数的任务排队；缓存达到上限时清除最早使用且未打开的缓存任务。正在播放的任务会保留。", style = MaterialTheme.typography.bodySmall)
                        Text("搜索引擎", style = MaterialTheme.typography.titleMedium)
                        Box {
                            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("默认搜索引擎：$engine") }
                            DropdownMenu(menu, { menu = false }) {
                                builtInProviders.forEach { provider -> DropdownMenuItem(text = { Text(provider.name) }, onClick = { engine = provider.name; menu = false }) }
                                DropdownMenuItem(text = { Text("自定义：$customName") }, onClick = { engine = customName; menu = false })
                            }
                        }
                        OutlinedTextField(customName, { customName = it }, label = { Text("自定义引擎名称") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(customUrl, { customUrl = it }, label = { Text("自定义地址，%s 为关键词") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            runCatching {
                                val d = SettingsStore.validateRoot(download)
                                val c = SettingsStore.validateRoot(cache)
                                val md = downloads.toInt(); val mc = caches.toInt()
                                require(md in 1..8 && mc in 1..100) { "任务数量超出允许范围" }
                                val provider = builtInProviders.firstOrNull { it.name == engine }
                                require(provider != null || customUrl.startsWith("https://") || customUrl.startsWith("http://")) { "自定义地址需以 http:// 或 https:// 开头" }
                                prefs.edit().putString("download-path", d.absolutePath).putString("cache-path", c.absolutePath)
                                    .putInt("max-downloads", md).putInt("max-caches", mc)
                                    .putString("custom-name", customName).putString("custom-url", customUrl)
                                    .putString("search-name", provider?.name ?: customName).putString("search-url", provider?.homepage ?: customUrl).apply()
                                TorrentTasks.schedule()
                                message = "设置已保存，存储目录已通过写入检查"
                            }.onFailure { message = "保存失败：${it.localizedMessage}" }
                        }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("保存设置") }
                        Text(message)
                        Text("下载任务：${TorrentTasks.tasks.values.count { it.download && !it.clearing }} · 缓存任务：${TorrentTasks.tasks.values.count { !it.download && !it.clearing }}")
                        OutlinedButton(onClick = { clearType = false }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("一键清空缓存") }
                        OutlinedButton(onClick = { clearType = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("一键清空下载") }
                        Text("清空会停止对应任务并删除其专属目录中的文件。正在打开的资源请先返回；其他文件不受影响。", style = MaterialTheme.typography.bodySmall)
                    }
                    if (clearType != null) AlertDialog(onDismissRequest = { clearType = null }, title = { Text("确认清空${if (clearType == true) "下载" else "缓存"}？") },
                        text = { Text("会永久删除对应任务的文件。") },
                        confirmButton = { TextButton(onClick = { clearingIds = TorrentTasks.tasks.values.filter { it.download == clearType && it.viewers == 0 }.map { it.id }.toSet()
                            skipped = TorrentTasks.clear(clearType!!); clearType = null; message = "正在清理…"; waitingForClear = true }) { Text("确认清空") } },
                        dismissButton = { TextButton(onClick = { clearType = null }) { Text("取消") } })
                }
            }
        }
    }
}
