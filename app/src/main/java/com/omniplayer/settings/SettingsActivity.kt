package com.omniplayer.settings
import com.omniplayer.core.*
import com.omniplayer.live.*
import com.omniplayer.magnet.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    private var attemptedPendingUpdate = false
    private var directorySelected: ((String) -> Unit)? = null
    private val directoryPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        val folder = uri?.let { DirectoryPicker.resolve(this, it) }
        if (folder != null) directorySelected?.invoke(folder.absolutePath)
        else if (uri != null) android.widget.Toast.makeText(this, "请选择设备共享存储中的本地文件夹", android.widget.Toast.LENGTH_LONG).show()
    }
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

    private fun chooseStorageDirectory(onSelected: (String) -> Unit, confirmPicker: () -> Unit) {
        directorySelected = onSelected
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            android.widget.Toast.makeText(this, "为让 BT 引擎写入所选目录，请在系统页面允许 OmniPlayer 管理文件；返回后再点一次选择目录。", android.widget.Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            return
        }
        confirmPicker()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TorrentTasks.initialize(this)
        val prefs = SettingsStore.prefs(this)
        setContent {
            AppTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF087F79))) {
                var updateRelease by remember { mutableStateOf<AppUpdater.Release?>(null) }
                var updateStatus by remember { mutableStateOf("") }
                var checkingUpdate by remember { mutableStateOf(false) }
                var autoUpdate by remember { mutableStateOf(prefs.getBoolean("auto-update-check", false)) }
                val updateScope = rememberCoroutineScope()
                val entrySection = intent.getStringExtra("section") ?: "menu"
                var section by remember { mutableStateOf(entrySection) }
                BackHandler(enabled = section != entrySection) { section = "menu" }
                var download by remember { mutableStateOf(SettingsStore.root(this, true).absolutePath) }
                var cache by remember { mutableStateOf(SettingsStore.root(this, false).absolutePath) }
                var downloads by remember { mutableStateOf(SettingsStore.maxDownloads(this).toString()) }
                var caches by remember { mutableStateOf(SettingsStore.maxCaches(this).toString()) }
                var providers by remember { mutableStateOf(SearchEngineStore.load(this)) }
                var customName by remember { mutableStateOf(prefs.getString("custom-name", "自定义搜索") ?: "自定义搜索") }
                var customUrl by remember { mutableStateOf(prefs.getString("custom-url", "") ?: "") }
                var engine by remember { mutableStateOf(prefs.getString("search-name", "clg.im") ?: "clg.im") }
                var message by remember { mutableStateOf("") }
                var directoryMessage by remember { mutableStateOf("") }
                var showDirectoryPickerPrompt by remember { mutableStateOf(false) }
                var updateDownloadPercent by remember { mutableStateOf<Int?>(null) }
                var updateDownloadDetail by remember { mutableStateOf("") }
                var deferInstallPrompt by remember { mutableStateOf(false) }
                var menu by remember { mutableStateOf(false) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
                        providers = SearchEngineStore.load(this@SettingsActivity)
                        engine = prefs.getString("search-name", SearchEngineStore.defaultName()) ?: SearchEngineStore.defaultName()
                    } }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        val state = prefs.getString("update-state", "") ?: ""
                        val downloaded = prefs.getLong("update-bytes", 0L)
                        val total = prefs.getLong("update-total", 0L)
                        updateDownloadPercent = when { state == "ready" -> 100; total > 0 -> (downloaded * 100 / total).toInt().coerceIn(0, 99); else -> null }
                        updateDownloadDetail = when (state) {
                            "downloading" -> "正在下载更新：${bytesForUpdate(downloaded)} / ${if (total > 0) bytesForUpdate(total) else "计算大小中"}"
                            "queued" -> "更新已排队，准备继续下载。"
                            "paused" -> "更新已暂停，已下载内容会保留。"
                            "failed" -> "下载中断，已保留已下载内容，可继续更新。"
                            "ready" -> "下载完成，正在等待系统安装确认。"
                            else -> ""
                        }
                        kotlinx.coroutines.delay(750)
                    }
                }
                LaunchedEffect(updateDownloadDetail) { if (updateDownloadDetail != "下载完成，正在等待系统安装确认。") deferInstallPrompt = false }
                val pkg = packageManager.getPackageInfo(packageName, 0)

                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (section == entrySection) finish() else section = "menu" }) { Icon(Icons.Default.ArrowBack, if (section == entrySection) "返回" else "返回设置分类") }
                        Text(when(section) { "magnet" -> "磁播设置"; "general" -> "通用设置"; "about" -> "软件说明"; else -> "设置" }, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                        if (section == "menu") TextButton(onClick = { section = "about" }) { Text("软件更新") }
                        TextButton(onClick = { finish() }) { Text("首页") }
                    }
                    when (section) {
                        "menu" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingCategory("直播设置", "直播源管理", onClick = { startActivity(Intent(this@SettingsActivity, IptvSettingsActivity::class.java)) })
                            SettingCategory("磁播设置", "存储位置 · 搜索源 · 并发任务", onClick = { section = "magnet" })
                            SettingCategory("通用设置", "字体 · 背景颜色", onClick = { section = "general" })
                            SettingCategory("软件说明", "版本与更新时间", onClick = { section = "about" })
                        }
                        "general" -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            FontSettings { fontPicker.launch(arrayOf("font/*", "application/octet-stream", "application/x-font-ttf", "application/x-font-opentype")) }
                            HorizontalDivider()
                            BackgroundSettings()
                        }
                        "about" -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("OmniPlayer", style = MaterialTheme.typography.headlineSmall)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("自动检查并提醒更新", style = MaterialTheme.typography.titleMedium)
                                    Text("每次启动最多提醒一次；关闭后仍可手动检查。", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = autoUpdate, onCheckedChange = {
                                    autoUpdate = it
                                    prefs.edit().putBoolean("auto-update-check", it).apply()
                                })
                            }
                            OutlinedButton(enabled = !checkingUpdate, onClick = {
                                checkingUpdate = true
                                updateStatus = "正在检查更新…"
                                updateScope.launch {
                                    val result = runCatching { withContext(Dispatchers.IO) { AppUpdater.latest(this@SettingsActivity) } }
                                    checkingUpdate = false
                                    result.onSuccess { release ->
                                        updateRelease = release
                                        updateStatus = if (release == null) "已是最新版本，或暂无可用更新。" else "发现版本 ${release.version}。"
                                    }.onFailure { updateStatus = "检查失败：${it.localizedMessage}" }
                                }
                            }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (checkingUpdate) "检查中…" else "立即检查更新") }
                            Text(updateStatus, style = MaterialTheme.typography.bodySmall)
                            if (updateDownloadDetail.isNotBlank()) {
                                Text(updateDownloadDetail, style = MaterialTheme.typography.bodySmall)
                                val progress = updateDownloadPercent
                                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                                else LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                                if (progress != null) Text("${progress}%", style = MaterialTheme.typography.labelSmall)
                                val updateState = prefs.getString("update-state", "") ?: ""
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (updateState == "downloading" || updateState == "queued")
                                        OutlinedButton(onClick = { AppUpdater.pause(this@SettingsActivity) }) { Text("暂停更新") }
                                    if (updateState == "paused" || updateState == "failed")
                                        Button(onClick = { AppUpdater.resume(this@SettingsActivity) }) { Text("继续更新") }
                                    if (updateState == "ready")
                                        Button(onClick = { prefs.getString("pending-update-apk", null)?.let { AppUpdater.install(this@SettingsActivity, java.io.File(it)) } }) { Text("立即安装") }
                                }
                            }
                            updateRelease?.let { release -> AlertDialog(
                                onDismissRequest = { updateRelease = null },
                                title = { Text("更新到 ${release.version}？") },
                                text = { Text(buildString { append("更新包下载后会打开系统安装确认；原有设置与缓存会保留。"); if (release.notes.isNotBlank()) { append("\n\n更新说明：\n"); append(release.notes.take(1800)) } }) },
                                confirmButton = { TextButton(onClick = {
                                    updateScope.launch(Dispatchers.IO) {
                                        runCatching { AppUpdater.enqueue(this@SettingsActivity, release) }
                                            .onSuccess { withContext(Dispatchers.Main) { updateStatus = "更新包已加入下载，完成后会提示安装。" } }
                                            .onFailure { withContext(Dispatchers.Main) { updateStatus = "下载失败：${it.localizedMessage}" } }
                                    }
                                    updateRelease = null
                                }) { Text("下载并更新") } },
                                dismissButton = { TextButton(onClick = { updateRelease = null }) { Text("取消") } }
                            ) }
                            if (updateDownloadDetail == "下载完成，正在等待系统安装确认。" && !deferInstallPrompt) AlertDialog(
                                onDismissRequest = { deferInstallPrompt = true },
                                title = { Text("下载完成") },
                                text = { Text("更新安装包已准备好。现在打开系统安装确认吗？") },
                                confirmButton = { TextButton(onClick = { prefs.getString("pending-update-apk", null)?.let { AppUpdater.install(this@SettingsActivity, java.io.File(it)) } }) { Text("立即安装") } },
                                dismissButton = { TextButton(onClick = { deferInstallPrompt = true }) { Text("稍后") } }
                            )
                            Text("版本：${pkg.versionName}")
                            Text("电视直播与磁力播放工具", style = MaterialTheme.typography.bodyMedium)
                        }
                        "magnet" -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("存储位置", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(download, { download = it }, label = { Text("下载目录（绝对路径）") }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "下载目录输入框" })
                            OutlinedButton(onClick = { chooseStorageDirectory({ download = it; directoryMessage = "已选择下载目录" }) { showDirectoryPickerPrompt = true } }, modifier = Modifier.fillMaxWidth()) { Text("选择下载目录…") }
                            OutlinedTextField(cache, { cache = it }, label = { Text("缓存目录（绝对路径）") }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "缓存目录输入框" })
                            OutlinedButton(onClick = { chooseStorageDirectory({ cache = it; directoryMessage = "已选择缓存目录" }) { showDirectoryPickerPrompt = true } }, modifier = Modifier.fillMaxWidth()) { Text("选择缓存目录…") }
                            Text(directoryMessage, style = MaterialTheme.typography.bodySmall)
                            if (showDirectoryPickerPrompt) AlertDialog(
                                onDismissRequest = { showDirectoryPickerPrompt = false },
                                title = { Text("选择存储文件夹") },
                                text = { Text("即将打开系统文件夹选择器。点“取消”或按系统返回键可回到设置，不会修改当前路径。") },
                                confirmButton = { TextButton(onClick = { showDirectoryPickerPrompt = false; directoryPicker.launch(null) }) { Text("继续选择") } },
                                dismissButton = { TextButton(onClick = { showDirectoryPickerPrompt = false }) { Text("取消并返回") } }
                            )
                            Text("可填写应用有权写入的真实路径。修改目录用于新任务，旧任务保留原地址。", style = MaterialTheme.typography.bodySmall)
                            getExternalFilesDirs(null).filterNotNull().forEach { base ->
                                OutlinedButton(onClick = { download = "${base.absolutePath}/MyDownloads"; cache = "${base.absolutePath}/MyCache" }, modifier = Modifier.fillMaxWidth()) { Text("使用 ${base.absolutePath}") }
                            }
                            Text("最大任务数", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(downloads, { downloads = it }, label = { Text("最大同时下载数量（1–8，不含缓存）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(caches, { caches = it }, label = { Text("最大缓存任务数量（1–100，单独计算）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text("默认搜索引擎", style = MaterialTheme.typography.titleMedium)
                            Box {
                                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("默认搜索引擎：$engine") }
                                DropdownMenu(menu, { menu = false }) {
                                    providers.forEach { provider -> DropdownMenuItem(text = { Text(provider.name) }, onClick = { engine = provider.name; menu = false }) }
                                }
                            }
                            OutlinedButton(onClick = { startActivity(Intent(this@SettingsActivity, SearchEngineSettingsActivity::class.java)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("编辑 / 删除搜索引擎（${providers.size}/10）") }
                            Button(onClick = {
                                runCatching {
                                    val d = SettingsStore.validateRoot(download)
                                    val c = SettingsStore.validateRoot(cache)
                                    val md = downloads.toInt(); val mc = caches.toInt()
                                    require(md in 1..8 && mc in 1..100) { "任务数量超出允许范围" }
                                    val provider = providers.firstOrNull { it.name == engine } ?: error("请选择有效的搜索引擎")
                                    prefs.edit().putString("download-path", d.absolutePath).putString("cache-path", c.absolutePath)
                                        .putInt("max-downloads", md).putInt("max-caches", mc)
                                        .putString("search-name", provider.name).putString("search-url", provider.homepage).apply()
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

    override fun onResume() {
        super.onResume()
        PendingUpdateInstaller.resume(this)
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

private fun bytesForUpdate(value: Long): String = when {
    value >= 1024L * 1024 * 1024 -> "${"%.1f".format(value / (1024.0 * 1024 * 1024))} GB"
    value >= 1024L * 1024 -> "${"%.1f".format(value / (1024.0 * 1024))} MB"
    value >= 1024L -> "${"%.1f".format(value / 1024.0)} KB"
    else -> "$value B"
}
