package com.freemangnetplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private data class SearchProvider(val name: String, val homepage: String)

private val builtInProviders = listOf(
    SearchProvider("聚合吧", "https://www.juhebd.com/"),
    SearchProvider("磁力狗", "https://www.cld123.com/"),
    SearchProvider("磁力搜索", "https://clg63.top/"),
    SearchProvider("Seedog", "https://seedog.cc/"),
    SearchProvider("BT世界", "https://www.btsj6.com/"),
    SearchProvider("影视时光", "https://www.ldysg.win/")
)

private val ink = Color(0xFF14202B)
private val blue = Color(0xFF344AB8)
private val teal = Color(0xFF087F79)
private val canvas = Color(0xFFF3F8FA)
private const val PREFS_NAME = "player-settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inbound = intent?.data?.takeIf { it.scheme.equals("magnet", true) }?.toString()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = teal,
                secondary = blue,
                background = canvas,
                surface = Color.White,
                onBackground = ink,
                onSurface = ink
            )) {
                var keyword by remember { mutableStateOf("") }
                var magnet by remember { mutableStateOf("") }
                var showEngineMenu by remember { mutableStateOf(false) }
                var showCustomDialog by remember { mutableStateOf(false) }
                var customName by remember { mutableStateOf(prefs.getString("custom-name", "自定义搜索") ?: "自定义搜索") }
                var customUrl by remember { mutableStateOf(prefs.getString("custom-url", "") ?: "") }
                var selectedName by remember { mutableStateOf(prefs.getString("search-name", builtInProviders.first().name) ?: builtInProviders.first().name) }
                var selectedUrl by remember { mutableStateOf(prefs.getString("search-url", builtInProviders.first().homepage) ?: builtInProviders.first().homepage) }
                var error by remember { mutableStateOf("") }
                val focus = LocalFocusManager.current
                val clipboard = LocalClipboardManager.current
                val magnetFocus = remember { FocusRequester() }

                if (showCustomDialog) {
                    AlertDialog(
                        onDismissRequest = { showCustomDialog = false },
                        title = { Text("自定义搜索引擎") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(customName, { customName = it }, label = { Text("引擎名称") }, singleLine = true)
                                OutlinedTextField(customUrl, { customUrl = it }, label = { Text("搜索网站地址") }, singleLine = true)
                                Text("请输入搜索网站首页地址；应用会尝试在站内自动提交关键词。", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val url = customUrl.trim()
                                if (url.startsWith("https://") || url.startsWith("http://")) {
                                    val name = customName.trim().ifBlank { "自定义搜索" }
                                    prefs.edit().putString("custom-name", name).putString("custom-url", url)
                                        .putString("search-name", name).putString("search-url", url).apply()
                                    selectedName = name
                                    selectedUrl = url
                                    showCustomDialog = false
                                } else error = "地址需要以 http:// 或 https:// 开头"
                            }) { Text("保存并使用") }
                        },
                        dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("取消") } }
                    )
                }

                Surface(Modifier.fillMaxSize(), color = canvas) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().background(blue).padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = keyword,
                                        onValueChange = { keyword = it; error = "" },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("搜索影片或资源名称") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = {
                                            focus.clearFocus()
                                            launchSearch(keyword, selectedName, selectedUrl, { error = it })
                                        }),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedBorderColor = Color.Transparent
                                        )
                                    )
                                    TextButton(onClick = {
                                        focus.clearFocus()
                                        launchSearch(keyword, selectedName, selectedUrl, { error = it })
                                    }) { Text("⌕ 搜索", style = MaterialTheme.typography.labelLarge, color = teal) }
                                    Box {
                                        TextButton(onClick = { showEngineMenu = true }) {
                                            Text("⋮", style = MaterialTheme.typography.headlineMedium, color = ink)
                                        }
                                        DropdownMenu(expanded = showEngineMenu, onDismissRequest = { showEngineMenu = false }) {
                                            Text("选择搜索引擎", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = teal)
                                            builtInProviders.forEach { provider ->
                                                DropdownMenuItem(
                                                    text = { Text(if (selectedName == provider.name) "✓  ${provider.name}" else provider.name) },
                                                    onClick = {
                                                        selectedName = provider.name
                                                        selectedUrl = provider.homepage
                                                        prefs.edit().putString("search-name", selectedName).putString("search-url", selectedUrl).apply()
                                                        showEngineMenu = false
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(text = { Text("自定义搜索引擎…") }, onClick = {
                                                showEngineMenu = false
                                                showCustomDialog = true
                                            })
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().background(Color.White).padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text("⌂  首页", color = blue, fontWeight = FontWeight.SemiBold)
                            Text("磁力", color = Color.Gray, modifier = Modifier.clickable { magnetFocus.requestFocus() })
                            Text("导入", color = Color.Gray, modifier = Modifier.clickable {
                                magnet = clipboard.getText()?.text.orEmpty()
                                magnetFocus.requestFocus()
                            })
                        }

                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("磁力链接解析", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("输入磁力链接，解析资源后可预览、下载或播放。", color = Color(0xFF66727A))
                            OutlinedTextField(
                                value = magnet,
                                onValueChange = { magnet = it; error = "" },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).focusRequester(magnetFocus)
                                    .semantics { contentDescription = "磁力链接输入框" },
                                label = { Text("磁力链接") },
                                placeholder = { Text("输入磁力链接…") },
                                shape = RoundedCornerShape(16.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { magnet = "" }, modifier = Modifier.weight(1f).height(54.dp)) { Text("清空") }
                                Button(
                                    onClick = {
                                        focus.clearFocus()
                                        parseMagnet(magnet, { error = it })
                                    },
                                    modifier = Modifier.weight(1f).height(54.dp)
                                ) { Text("解析") }
                            }
                            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            Text("快捷入口", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            val shortcuts = listOf("下载任务", "离线下载", "电视投屏", "我的收藏")
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                userScrollEnabled = false
                            ) {
                                items(shortcuts) { title ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().height(82.dp).clickable {
                                            error = when (title) {
                                                "下载任务" -> "下载任务会在开始下载后显示。"
                                                "离线下载" -> "离线下载需要可连接的种子节点。"
                                                "电视投屏" -> "请先播放视频，再从播放器控制投屏。"
                                                else -> "收藏功能将在资源详情页添加。"
                                            }
                                        },
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(title, color = teal, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                            Text("当前搜索引擎：$selectedName", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
        inbound?.let { openTorrent(it, "parse") }
    }

    private fun launchSearch(query: String, name: String, url: String, showError: (String) -> Unit) {
        if (query.isBlank()) {
            showError("请输入关键词")
            return
        }
        startActivity(Intent(this, SearchActivity::class.java)
            .putExtra("providerName", name)
            .putExtra("url", url)
            .putExtra("query", query.trim()))
    }

    private fun parseMagnet(raw: String, showError: (String) -> Unit) {
        val value = raw.trim().replace("&amp;", "&", ignoreCase = true)
        val validMagnet = value.startsWith("magnet:?") && Regex("(?i)(?:[?&])xt=urn:btih:").containsMatchIn(value)
        val validTorrent = (value.startsWith("https://") || value.startsWith("http://")) && value.substringBefore('?').endsWith(".torrent", true)
        if (!validMagnet && !validTorrent) {
            showError("磁力链接格式无效，请粘贴完整 magnet 链接。")
            return
        }
        openTorrent(value, "parse")
    }

    private fun openTorrent(value: String, mode: String) {
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("source", value.trim().replace("&amp;", "&", ignoreCase = true))
            .putExtra("mode", mode))
    }
}
