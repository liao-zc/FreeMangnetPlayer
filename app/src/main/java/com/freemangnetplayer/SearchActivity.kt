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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

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
            MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF44D7B6))) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { finish() }) { Text("返回") }
                        Text("$providerName · $query", Modifier.padding(top = 14.dp), maxLines = 1)
                    }
                    if (results.isNotEmpty()) {
                        Text("应用内识别到的磁力资源", style = MaterialTheme.typography.titleMedium)
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(results, key = { it.source }) { item ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(item.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { openTorrent(item, "play") }) { Text("播放") }
                                            OutlinedButton(onClick = { openTorrent(item, "download") }) { Text("下载 / 缓存") }
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    } else {
                        Text("网页结果会在本页显示；识别到磁力链接后可直接播放或下载。", style = MaterialTheme.typography.bodySmall)
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize().weight(1f),
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
                openTorrent(SearchResult(path.substringAfterLast('/'), value), "download")
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

    private fun openTorrent(result: SearchResult, mode: String) {
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("source", result.source)
            .putExtra("title", result.title)
            .putExtra("mode", mode))
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
