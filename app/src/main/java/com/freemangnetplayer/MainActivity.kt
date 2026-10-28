package com.freemangnetplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private data class SearchProvider(val name: String, val homepage: String)

private val searchProviders = listOf(
    SearchProvider("聚合吧", "https://www.juhebd.com/"),
    SearchProvider("磁力狗", "https://www.cld123.com/"),
    SearchProvider("磁力搜索", "https://clg63.top/"),
    SearchProvider("Seedog", "https://seedog.cc/"),
    SearchProvider("BT世界", "https://www.btsj6.com/"),
    SearchProvider("影视时光", "https://www.ldysg.win/")
)

private const val PREFS_NAME = "player-settings"
private const val PREF_PROVIDER = "search-provider"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inbound = intent?.data?.takeIf { it.scheme.equals("magnet", true) }?.toString()
        if (inbound != null) {
            openTorrent(inbound, "play")
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF44D7B6), background = Color(0xFF101419), surface = Color(0xFF191F26)
            )) {
                var keyword by remember { mutableStateOf("") }
                var providerIndex by remember { mutableIntStateOf(
                    searchProviders.indexOfFirst { it.name == prefs.getString(PREF_PROVIDER, "聚合吧") }.coerceAtLeast(0)
                ) }
                var showSettings by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf("") }
                val focusManager = LocalFocusManager.current

                if (showSettings) {
                    AlertDialog(
                        onDismissRequest = { showSettings = false },
                        title = { Text("搜索引擎设置") },
                        text = {
                            Column {
                                Text("选择默认搜索来源。搜索网址由应用配置管理。", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                searchProviders.forEachIndexed { index, provider ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { providerIndex = index },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = providerIndex == index, onClick = { providerIndex = index })
                                        Text(provider.name)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                prefs.edit().putString(PREF_PROVIDER, searchProviders[providerIndex].name).apply()
                                showSettings = false
                            }) { Text("完成") }
                        }
                    )
                }

                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("FreeMangnetPlayer", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF44D7B6))
                                Text("搜索、播放与缓存", color = Color.LightGray)
                            }
                            TextButton(onClick = { showSettings = true }) { Text("设置") }
                        }

                        Spacer(Modifier.height(20.dp))
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = { keyword = it; error = "" },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("输入关键词") },
                            placeholder = { Text("搜索影片或资源名称") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                launchSearch(keyword, providerIndex) { error = it }
                            })
                        )
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                launchSearch(keyword, providerIndex) { error = it }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) { Text("搜索") }
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.weight(1f))
                        Text("搜索结果在应用内打开。仅下载和播放你有权使用的内容。", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    private fun launchSearch(keyword: String, providerIndex: Int, showError: (String) -> Unit) {
        if (keyword.isBlank()) {
            showError("请输入关键词")
            return
        }
        val provider = searchProviders[providerIndex]
        startActivity(Intent(this, SearchActivity::class.java)
            .putExtra("providerName", provider.name)
            .putExtra("url", provider.homepage)
            .putExtra("query", keyword.trim()))
    }

    private fun openTorrent(value: String, mode: String) {
        val normalized = value.trim().replace("&amp;", "&", ignoreCase = true)
        if (!normalized.startsWith("magnet:?") && !normalized.endsWith(".torrent", true)) return
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("source", normalized)
            .putExtra("mode", mode))
    }
}
