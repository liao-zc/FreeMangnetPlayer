package com.freemangnetplayer

import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay

private data class SearchResult(val title: String, val source: String)

class SearchActivity : ComponentActivity() {
    private val results = mutableStateListOf<SearchResult>()
    private var searchWebView: WebView? = null
    private var autoSearchStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: "https://www.juhebd.com/"
        val query = intent.getStringExtra("query").orEmpty()
        val providerName = intent.getStringExtra("providerName").orEmpty()

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF087F79), background = androidx.compose.ui.graphics.Color(0xFFF3F8FA))) {
                var showLinkDialog by remember { mutableStateOf(false) }
                var showSitePage by remember { mutableStateOf(false) }
                var searchTimedOut by remember { mutableStateOf(false) }
                var manualLink by remember { mutableStateOf("") }
                var linkError by remember { mutableStateOf("") }

                LaunchedEffect(query, url) {
                    delay(18_000)
                    if (results.isEmpty()) searchTimedOut = true
                }

                if (showLinkDialog) {
                    AlertDialog(
                        onDismissRequest = { showLinkDialog = false },
                        title = { Text("添加磁力资源") },
                        text = {
                            Column {
                                Text("粘贴 magnet 链接或 .torrent 文件地址。")
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = manualLink,
                                    onValueChange = { manualLink = it; linkError = "" },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("磁力链接或种子文件 URL") }
                                )
                                if (linkError.isNotBlank()) Text(linkError, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val value = manualLink.trim().replace("&amp;", "&", ignoreCase = true)
                                val magnet = value.startsWith("magnet:?", true) && Regex("(?i)(?:[?&])xt=urn:btih:").containsMatchIn(value)
                                val torrent = (value.startsWith("http://", true) || value.startsWith("https://", true)) && value.substringBefore('?').endsWith(".torrent", true)
                                if (magnet || torrent) {
                                    addResult("手动添加的资源", value)
                                    manualLink = ""
                                    showLinkDialog = false
                                } else {
                                    linkError = "链接格式无效，请检查后重试。"
                                }
                            }) { Text("添加") }
                        },
                        dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text("取消") } }
                    )
                }

                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(onClick = { finish() }) { Text("返回") }
                        Column(Modifier.weight(1f)) {
                            Text("搜索结果", style = MaterialTheme.typography.titleLarge)
                            Text("$providerName · $query", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        TextButton(onClick = { showSitePage = !showSitePage }) { Text(if (showSitePage) "资源列表" else "网页备用") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (results.isNotEmpty() && !showSitePage) {
                        Text("找到 ${results.size} 个磁力资源 · 点选后自动解析", style = MaterialTheme.typography.bodyMedium)
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                            items(results, key = { it.source }) { item ->
                                Card(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { openTorrent(item) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text(item.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(8.dp))
                                        Text(item.source, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(6.dp))
                                        Text("点选此资源开始解析  ›", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.Gray)
                                    }
                                }
                            }
                        }
                    } else if (!showSitePage) {
                        Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                if (!searchTimedOut) CircularProgressIndicator()
                                Spacer(Modifier.height(14.dp))
                                Text(if (searchTimedOut) "没有提取到可识别的磁力链接" else "正在从搜索页面提取磁力链接…")
                                Spacer(Modifier.height(6.dp))
                                Text("搜索引擎可能需要验证，或没有公开磁力地址。", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = { showSitePage = true }) { Text("打开网页备用") }
                                OutlinedButton(onClick = { showLinkDialog = true }) { Text("手动粘贴磁力") }
                            }
                        }
                    }
                    AndroidView(
                        modifier = if (showSitePage) Modifier.fillMaxSize().weight(1f) else Modifier.size(1.dp),
                        factory = { context ->
                            WebView(context).apply {
                                searchWebView = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.setSupportMultipleWindows(false)
                                settings.loadsImagesAutomatically = true
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                                addJavascriptInterface(ResultBridge(), "FreeMagnetResults")
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                                        handleNavigation(request.url.toString())

                                    @Deprecated("Deprecated in Android")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                                        handleNavigation(url.orEmpty())

                                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                        super.onPageFinished(view, pageUrl)
                                        injectResultCollector(view)
                                        if (query.isNotBlank() && !autoSearchStarted) {
                                            autoSearchStarted = true
                                            injectSearch(view, query)
                                        }
                                    }
                                }
                                loadUrl(url)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun handleNavigation(rawUrl: String): Boolean {
        val value = rawUrl.trim().replace("&amp;", "&", ignoreCase = true)
        if (value.startsWith("magnet:", true)) {
            addResult("磁力资源", value)
            return true
        }
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            val path = value.substringBefore('?').substringBefore('#')
            if (path.endsWith(".torrent", true)) {
                openTorrent(SearchResult(path.substringAfterLast('/'), value))
                return true
            }
            return false // Keep all web navigation in this WebView.
        }
        return true // Never hand search-page links to another app/browser.
    }

    private fun injectSearch(view: WebView?, query: String) {
        val quoted = JSONObject.quote(query)
        val script = """(function(){var q=$quoted,n=0,t=setInterval(function(){var a=[...document.querySelectorAll('input[type=search],input[name=q],input[name=k],input[name=keyword],input[name=title],input[type=text]')].find(x=>x.offsetWidth>0&&!x.disabled);if(a){clearInterval(t);var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(a,q);a.dispatchEvent(new Event('input',{bubbles:true}));a.dispatchEvent(new Event('change',{bubbles:true}));var f=a.form;if(f){f.requestSubmit?f.requestSubmit():f.submit();}else{a.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));}}if(++n>30)clearInterval(t);},400);})()"""
        view?.evaluateJavascript(script, null)
    }

    private fun injectResultCollector(view: WebView?) {
        val script = """(function(){
            if(window.__freeMagnetObserver)return;
            function scan(){
              try {
                document.querySelectorAll('a[target=_blank]').forEach(a=>a.target='_self');
                var rows=[...document.querySelectorAll('a[href^="magnet:"],a[href^="MAGNET:"],[data-magnet]')].map(a=>{
                  var m=a.getAttribute('data-magnet')||a.href||'';
                  var t=(a.innerText||a.getAttribute('title')||'').trim();
                  if(!t){try{t=new URL(m).searchParams.get('dn')||'磁力资源'}catch(e){t='磁力资源'}}
                  return {title:t,source:m};
                }).filter(x=>x.source.toLowerCase().startsWith('magnet:'));
                window.FreeMagnetResults.onResults(JSON.stringify(rows));
              }catch(e){}
            }
            scan(); window.__freeMagnetObserver=new MutationObserver(scan);
            window.__freeMagnetObserver.observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['href','data-magnet']});
            window.open=function(u){if(u)location.href=u;return null;};
        })()"""
        view?.evaluateJavascript(script, null)
    }

    private fun addResult(title: String, source: String) {
        val normalized = source.trim().replace("&amp;", "&", ignoreCase = true)
        if (normalized.isBlank() || results.any { it.source == normalized }) return
        results.add(0, SearchResult(title.ifBlank { "磁力资源" }, normalized))
    }

    private fun openTorrent(result: SearchResult) {
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("source", result.source)
            .putExtra("title", result.title)
            .putExtra("mode", "parse"))
    }

    inner class ResultBridge {
        @JavascriptInterface
        fun onResults(json: String) {
            runOnUiThread {
                try {
                    val rows = JSONArray(json)
                    for (i in 0 until rows.length()) {
                        val row = rows.optJSONObject(i) ?: continue
                        addResult(row.optString("title"), row.optString("source"))
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun onDestroy() {
        searchWebView?.apply {
            stopLoading()
            removeJavascriptInterface("FreeMagnetResults")
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        searchWebView = null
        super.onDestroy()
    }
}
