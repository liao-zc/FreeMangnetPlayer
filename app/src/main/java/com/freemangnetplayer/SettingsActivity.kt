package com.freemangnetplayer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
    private val fontPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val temp=java.io.File(filesDir,"font-import.tmp")
            contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { output ->
                val buffer=ByteArray(8192);var total=0;while(true) { val n=input.read(buffer);if(n<0)break;total+=n;require(total<=40*1024*1024) { "字体文件过大" };output.write(buffer,0,n) }
            } }
            android.graphics.Typeface.createFromFile(temp)
            require(temp.renameTo(java.io.File(filesDir,"custom-font.ttf")))
            SettingsStore.prefs(this).edit().putString("font-face","自定义").apply()
        }.onFailure { android.widget.Toast.makeText(this,"字体导入失败：${it.localizedMessage}",android.widget.Toast.LENGTH_LONG).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TorrentTasks.initialize(this)
        val prefs = SettingsStore.prefs(this)
        setContent {
            AppTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF087F79))) {
                var section by remember { mutableStateOf("menu") }
                var download by remember { mutableStateOf(SettingsStore.root(this, true).absolutePath) }
                var cache by remember { mutableStateOf(SettingsStore.root(this, false).absolutePath) }
                var downloads by remember { mutableStateOf(SettingsStore.maxDownloads(this).toString()) }
                var caches by remember { mutableStateOf(SettingsStore.maxCaches(this).toString()) }
                var customName by remember { mutableStateOf(prefs.getString("custom-name", "自定义搜索") ?: "自定义搜索") }
                var customUrl by remember { mutableStateOf(prefs.getString("custom-url", "") ?: "") }
                var engine by remember { mutableStateOf(prefs.getString("search-name", "clg.im") ?: "clg.im") }
                var message by remember { mutableStateOf("") }
                var menu by remember { mutableStateOf(false) }
                val pkg = packageManager.getPackageInfo(packageName, 0)
                @Suppress("DEPRECATION") val legacyVersion = pkg.versionCode
                val version = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode.toString() else legacyVersion.toString()
                val updated = "2026-10-30"

                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (section == "menu") finish() else section = "menu" }) { Icon(Icons.Default.ArrowBack, if (section == "menu") "返回首页" else "返回设置分类") }
                        Text(if (section == "menu") "设置" else when(section) { "magnet" -> "磁播设置"; "general" -> "通用设置"; else -> "设置" }, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { finish() }) { Text("首页") }
                    }
                    when (section) {
                        "menu" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingCategory("直播设置", "直播源管理", onClick = { startActivity(Intent(this@SettingsActivity, IptvSettingsActivity::class.java)) })
                            SettingCategory("磁播设置", "存储位置 · 搜索源 · 并发任务", onClick = { section = "magnet" })
                            SettingCategory("通用设置", "字体 · 背景颜色 · 软件版本", onClick = { section = "general" })
                        }
                        "general" -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            FontSettings { fontPicker.launch(arrayOf("font/*", "application/octet-stream", "application/x-font-ttf", "application/x-font-opentype")) }
                            HorizontalDivider()
                            BackgroundSettings()
                            HorizontalDivider()
                            Text("软件信息", style = MaterialTheme.typography.titleMedium)
                            Text("OmniPlayer 版本：${pkg.versionName}（$version）")
                            Text("最近更新：$updated")
                        }
                        "magnet" -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("存储位置", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(download, { download = it }, label = { Text("下载目录（绝对路径）") }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "下载目录输入框" })
                            OutlinedTextField(cache, { cache = it }, label = { Text("缓存目录（绝对路径）") }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "缓存目录输入框" })
                            Text("可填写应用有权写入的真实路径。修改目录用于新任务，旧任务保留原地址。", style = MaterialTheme.typography.bodySmall)
                            getExternalFilesDirs(null).filterNotNull().forEach { base ->
                                OutlinedButton(onClick = { download = "${base.absolutePath}/MyDownloads"; cache = "${base.absolutePath}/MyCache" }, modifier = Modifier.fillMaxWidth()) { Text("使用 ${base.absolutePath}") }
                            }
                            Text("最大任务数", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(downloads, { downloads = it }, label = { Text("最大同时下载数量（1–8，不含缓存）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(caches, { caches = it }, label = { Text("最大缓存任务数量（1–100，单独计算）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                                    message = "磁播设置已保存"
                                }.onFailure { message = "保存失败：${it.localizedMessage}" }
                            }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("保存磁播设置") }
                            Text(message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingCategory(title: String, summary: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
