package com.freemangnetplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private data class SearchProvider(
    val name: String,
    val homepage: String,
    val queryUrl: String? = null
)

private data class MagnetResult(val title: String, val magnet: String)

private val builtInProviders = listOf(
    SearchProvider("clg.im", "https://clg.im/"),
    SearchProvider("BT搜索（dobt）", "https://mg.dobt.cc/"),
    SearchProvider("BD电影首发站", "https://www.bdflixs.com/", "https://www.bdflixs.com/?s=%s"),
    SearchProvider("Pomo", "https://pomo.mom/", "https://pomo.mom/?keyword=%s"),
    SearchProvider("美剧粉", "https://www.mjf2020.com/", "https://www.mjf2020.com/ss/?s=%s"),
    SearchProvider("高清族", "https://hdzu.org/", "https://hdzu.org/movie/filter?act=title&wd=%s"),
    SearchProvider("磁力狗", "https://www.cld123.com/"),
    SearchProvider("聚合吧", "https://www.juhebd.com/", "https://www.juhebd.com/q/index----?k=%s"),
    SearchProvider("磁力搜索", "https://clg63.top/"),
    SearchProvider("Seedog", "https://seedog.cc/"),
    SearchProvider("BT世界", "https://www.btsj6.com/"),
    SearchProvider("影视时光", "https://www.ldysg.win/")
)

private val ink = Color(0xFF14202B)
private val teal = Color(0xFF087F79)
private val canvas = Color(0xFFF3F8FA)
private const val PREFS_NAME = "player-settings"

class MainActivity : ComponentActivity() {
    private var magnetWebView: WebView? = null
    private var activeQuery = ""
    private var activeProvider = ""
    private var searchPageUrl = ""
    private var firstPageLoad = true
    private var searchBridge: MagnetSearchBridge? = null
    private var searchNeedsFormSubmit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inbound = intent?.data?.takeIf { it.scheme.equals("magnet", true) }?.toString()?.let(::normalizeMagnetUri)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = teal,
                secondary = Color(0xFF00A89B),
                background = canvas,
                surface = Color.White,
                onBackground = ink,
                onSurface = ink
            )) {
                var keyword by remember { mutableStateOf("") }
                var magnet by remember { mutableStateOf("") }
                var selectedTab by remember { mutableStateOf("搜索") }
                var showEngineMenu by remember { mutableStateOf(false) }
                var showCustomDialog by remember { mutableStateOf(false) }
                var customName by remember { mutableStateOf(prefs.getString("custom-name", "自定义搜索") ?: "自定义搜索") }
                var customUrl by remember { mutableStateOf(prefs.getString("custom-url", "") ?: "") }
                var selectedName by remember { mutableStateOf(prefs.getString("search-name", builtInProviders[0].name) ?: builtInProviders[0].name) }
                var selectedUrl by remember { mutableStateOf(prefs.getString("search-url", builtInProviders[0].homepage) ?: builtInProviders[0].homepage) }
                var error by remember { mutableStateOf("") }
                var searchState by remember { mutableStateOf("idle") }
                var searchMessage by remember { mutableStateOf("") }
                var searchTrigger by remember { mutableIntStateOf(0) }
                var currentSearchUrl by remember { mutableStateOf("") }
                val results = remember { mutableStateListOf<MagnetResult>() }
                val downloads = remember { mutableStateListOf<LibraryEntry>() }
                val favorites = remember { mutableStateListOf<LibraryEntry>() }
                val focus = LocalFocusManager.current
                val lifecycleOwner = LocalLifecycleOwner.current

                fun reloadLibrary() {
                    downloads.clear()
                    downloads.addAll(LibraryStore.readDownloads(this@MainActivity))
                    favorites.clear()
                    favorites.addAll(LibraryStore.readFavorites(this@MainActivity))
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) reloadLibrary()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(searchTrigger) {
                    if (searchTrigger == 0) return@LaunchedEffect
                    delay(25_000)
                    if (searchState == "loading") {
                        searchState = "timeout"
                        searchMessage = "搜索超过 25 秒仍未返回。你可以重试或打开搜索网站继续查找。"
                    }
                }

                fun beginSearch() {
                    val q = keyword.trim()
                    if (q.isEmpty()) {
                        error = "先输入要搜索的关键词"
                        return
                    }
                    focus.clearFocus()
                    error = ""
                    results.clear()
                    searchState = "loading"
                    searchMessage = "正在连接 $selectedName…"
                    activeQuery = q
                    activeProvider = selectedName
                    val provider = builtInProviders.firstOrNull { it.name == selectedName }
                    val template = provider?.queryUrl ?: selectedUrl.takeIf { it.contains("%s") }
                    currentSearchUrl = template?.replace("%s", URLEncoder.encode(q, "UTF-8")) ?: selectedUrl
                    searchNeedsFormSubmit = template == null
                    searchPageUrl = currentSearchUrl
                    firstPageLoad = true
                    searchTrigger++
                }

                if (showCustomDialog) {
                    AlertDialog(
                        onDismissRequest = { showCustomDialog = false },
                        title = { Text("自定义搜索引擎") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(customName, { customName = it }, label = { Text("引擎名称") }, singleLine = true)
                                OutlinedTextField(customUrl, { customUrl = it }, label = { Text("搜索网站地址") }, singleLine = true)
                                Text("填写搜索地址；用 %s 代表关键词。没有 %s 时会尝试自动填写网页搜索框。", style = MaterialTheme.typography.bodySmall)
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

                Scaffold(
                    containerColor = canvas,
                    bottomBar = {
                        NavigationBar(containerColor = Color.White) {
                            NavigationBarItem(
                                selected = selectedTab == "搜索",
                                onClick = { selectedTab = "搜索" },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("搜索") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == "下载",
                                onClick = { selectedTab = "下载"; reloadLibrary() },
                                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                                label = { Text("下载") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == "收藏",
                                onClick = { selectedTab = "收藏"; reloadLibrary() },
                                icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                                label = { Text("收藏") }
                            )
                        }
                    }
                ) { insets ->
                    Box(Modifier.fillMaxSize().padding(insets)) {
                        when (selectedTab) {
                            "下载" -> LibraryScreen(
                                title = "下载任务",
                                entries = downloads,
                                emptyText = "这里会显示你实际开始的下载任务。",
                                onOpen = { openTorrent(it, "parse") },
                                onDownload = { openTorrent(it, "download") }
                            )
                            "收藏" -> LibraryScreen(
                                title = "我的收藏",
                                entries = favorites,
                                emptyText = "播放资源时点“收藏”，下次可从这里打开。",
                                onOpen = { openTorrent(it, "parse") },
                                onDownload = { openTorrent(it, "download") }
                            )
                            else -> SearchHome(
                                keyword = keyword,
                                onKeywordChange = { keyword = it; error = "" },
                                onSearch = ::beginSearch,
                                selectedName = selectedName,
                                showEngineMenu = showEngineMenu,
                                onEngineMenu = { showEngineMenu = it },
                                builtIns = builtInProviders,
                                onSelectEngine = { provider ->
                                    selectedName = provider.name
                                    selectedUrl = provider.homepage
                                    prefs.edit().putString("search-name", provider.name).putString("search-url", provider.homepage).apply()
                                    showEngineMenu = false
                                },
                                onCustomEngine = { showEngineMenu = false; showCustomDialog = true },
                                searchState = searchState,
                                searchMessage = searchMessage,
                                results = results,
                                onOpenWeb = {
                                    val url = currentSearchUrl.ifBlank { selectedUrl }
                                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                },
                                onCancelSearch = {
                                    searchState = "idle"
                                    searchMessage = ""
                                    results.clear()
                                    magnetWebView?.stopLoading()
                                },
                                onParse = { openTorrent(it, "parse") },
                                onDownload = { openTorrent(it, "download") },
                                magnet = magnet,
                                onMagnetChange = { magnet = it; error = "" },
                                onParseMagnet = {
                                    val value = magnet.trim().replace("&amp;", "&", true)
                                    val normalized = normalizeMagnetUri(value)
                                    if (normalized != null) openTorrent(MagnetResult(magnetTitle(normalized), normalized), "parse")
                                    else error = "请粘贴包含 btih 信息的完整 magnet 链接。"
                                },
                                error = error
                            )
                        }

                        AndroidView(
                            modifier = Modifier.size(1.dp),
                            factory = { context ->
                                WebView(context).apply {
                                    magnetWebView = this
                                    searchBridge = MagnetSearchBridge { title, link ->
                                        runOnUiThread {
                                            if (results.size < 60 && results.none { it.magnet == link }) {
                                                results.add(MagnetResult(title.ifBlank { magnetTitle(link) }, link))
                                                searchState = "results"
                                                searchMessage = ""
                                            }
                                        }
                                    }
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.javaScriptCanOpenWindowsAutomatically = false
                                    settings.setSupportMultipleWindows(false)
                                    settings.loadsImagesAutomatically = false
                                    settings.blockNetworkImage = true
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
                                    addJavascriptInterface(searchBridge!!, "FreeMagnetResults")
                                    webChromeClient = WebChromeClient()
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                                            val url = request.url.toString()
                                            if (url.startsWith("magnet:", true)) {
                                                val normalized = normalizeMagnetUri(url)
                                                if (normalized != null) {
                                                    runOnUiThread { openTorrent(MagnetResult(magnetTitle(normalized), normalized), "parse") }
                                                }
                                                return true
                                            }
                                            return false
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            scanForMagnets(view, activeQuery)
                                            if (firstPageLoad && searchNeedsFormSubmit) {
                                                firstPageLoad = false
                                                injectSearch(view, activeQuery)
                                            } else if (searchState == "loading") {
                                                searchState = "empty"
                                                searchMessage = "页面已返回，但暂未提取到 magnet 链接。"
                                            }
                                        }

                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                runOnUiThread {
                                                    searchState = "error"
                                                    searchMessage = "搜索网站连接失败。检查网络，或更换搜索引擎后重试。"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )

                        LaunchedEffect(searchTrigger) {
                            if (searchTrigger == 0) return@LaunchedEffect
                            delay(100)
                            magnetWebView?.stopLoading()
                            magnetWebView?.loadUrl(searchPageUrl)
                        }
                    }
                }

                if (error.isNotBlank() && selectedTab != "搜索") {
                    LaunchedEffect(error) { error = "" }
                }
            }
        }
        inbound?.let { openTorrent(MagnetResult(magnetTitle(it), it), "parse") }
    }

    private fun injectSearch(view: WebView?, query: String) {
        val quoted = JSONObject.quote(query)
        val script = """(function(){var q=$quoted,n=0,t=setInterval(function(){var a=[...document.querySelectorAll('input[type=search],input[name=q],input[name=k],input[name=keyword],input[name=title],input[type=text]')].find(x=>x.offsetWidth>0&&!x.disabled);if(a){clearInterval(t);var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(a,q);a.dispatchEvent(new Event('input',{bubbles:true}));a.dispatchEvent(new Event('change',{bubbles:true}));var b=a.form&&a.form.querySelector('button[type=submit],input[type=submit],button[lay-submit]');if(b)b.click();else if(a.form){a.form.requestSubmit?a.form.requestSubmit():a.form.submit();}else a.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));}if(++n>50)clearInterval(t);},250);})()"""
        view?.evaluateJavascript(script, null)
    }

    private fun scanForMagnets(view: WebView?, query: String) {
        val quotedQuery = JSONObject.quote(query)
        val script = """(function(){try{var query=$quotedQuery;function decode(s){var x=document.createElement('textarea');x.innerHTML=s||'';return x.value.replace(/&amp;/g,'&').trim()}function clean(s){s=(s||'').replace(/\s+/g,' ').trim();return /^(下载|磁力|magnet|复制|播放)(链接)?$/i.test(s)?'':s}function emit(u,t,f){u=decode(u);if(!/^magnet:\?/i.test(u))return;var dn=/(?:[?&])dn=([^&]+)/i.exec(u),name='';if(dn){try{name=decodeURIComponent(dn[1].replace(/\+/g,'%20'))}catch(e){name=dn[1]}}var title=clean(t)||clean(name)||clean(f)||'磁力资源';window.FreeMagnetResults.onResults(JSON.stringify([{title:title,source:u}]))}function parse(doc,fallback){var html=doc.documentElement?doc.documentElement.outerHTML:'',re=/magnet:\?[^\"'<>\s]+/ig,m;while((m=re.exec(html))!==null){var raw=m[0],a=[...doc.querySelectorAll('a[href],[data-magnet]')].find(function(x){return (x.getAttribute('href')||x.getAttribute('data-magnet')||'').indexOf(raw)>=0});var label=a?(a.innerText||a.title||''):'';if(!label&&a&&a.parentElement)label=a.parentElement.innerText||'';emit(raw,label,fallback)}doc.querySelectorAll('a[href^=\"magnet:\"],a[href^=\"MAGNET:\"],[data-magnet]').forEach(function(a){var label=a.innerText||a.title||'';if(!label&&a.parentElement)label=a.parentElement.innerText||'';emit(a.getAttribute('data-magnet')||a.getAttribute('href')||'',label,fallback)})}function scan(){parse(document,'');if(query.length<2)return;if(!window.__magnetCrawled)window.__magnetCrawled=new Set();var links=[...document.querySelectorAll('a[href]')].filter(function(a){try{var u=new URL(a.href,location.href),card=a.closest('article,li,.item,.video-item,.module-item,.movie-item,.post'),text=((a.innerText||a.title||'')+' '+(card?card.innerText:'')).replace(/\s+/g,' ');if(u.origin!==location.origin||u.href===location.href||window.__magnetCrawled.has(u.href)||text.toLowerCase().indexOf(query.toLowerCase())<0)return false;window.__magnetCrawled.add(u.href);return true}catch(e){return false}}).slice(0,12);links.forEach(function(a){var card=a.closest('article,li,.item,.video-item,.module-item,.movie-item,.post'),title=clean(a.innerText||a.title||'')||(card?clean(card.innerText):'');fetch(a.href,{credentials:'include'}).then(function(r){return r.text()}).then(function(t){parse(new DOMParser().parseFromString(t,'text/html'),title)}).catch(function(){})})}scan();if(!window.__magnetWatcher){window.__magnetWatcher=new MutationObserver(scan);window.__magnetWatcher.observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['href','data-magnet']});setTimeout(function(){window.__magnetWatcher.disconnect();window.__magnetWatcher=null},30000)}}catch(e){}})()"""
        view?.evaluateJavascript(script, null)
    }
    private fun openTorrent(item: MagnetResult, mode: String) {
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("source", item.magnet)
            .putExtra("title", item.title)
            .putExtra("mode", mode))
    }

    private fun isMagnet(value: String) = normalizeMagnetUri(value) != null

    override fun onDestroy() {
        magnetWebView?.apply {
            stopLoading()
            removeJavascriptInterface("FreeMagnetResults")
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        magnetWebView = null
        super.onDestroy()
    }
}

private class MagnetSearchBridge(private val onMagnet: (String, String) -> Unit) {
    @JavascriptInterface
    fun onResults(json: String) {
        runCatching {
            val rows = JSONArray(json)
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val link = normalizeMagnetUri(row.optString("source"))
                if (link != null) {
                    onMagnet(row.optString("title"), link)
                }
            }
        }
    }
}

@Composable
private fun SearchHome(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    selectedName: String,
    showEngineMenu: Boolean,
    onEngineMenu: (Boolean) -> Unit,
    builtIns: List<SearchProvider>,
    onSelectEngine: (SearchProvider) -> Unit,
    onCustomEngine: () -> Unit,
    searchState: String,
    searchMessage: String,
    results: SnapshotStateList<MagnetResult>,
    onOpenWeb: () -> Unit,
    onCancelSearch: () -> Unit,
    onParse: (MagnetResult) -> Unit,
    onDownload: (MagnetResult) -> Unit,
    magnet: String,
    onMagnetChange: (String) -> Unit,
    onParseMagnet: () -> Unit,
    error: String
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(teal).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.weight(1f).heightIn(min = 58.dp).semantics { contentDescription = "搜索关键词" },
                placeholder = { Text("输入影片或资源名称") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
            )
            Button(onClick = onSearch, modifier = Modifier.height(58.dp).semantics { contentDescription = "执行关键词搜索" }, contentPadding = PaddingValues(horizontal = 16.dp)) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("搜索", style = MaterialTheme.typography.titleMedium)
            }
            Box {
                IconButton(onClick = { onEngineMenu(true) }, modifier = Modifier.size(54.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "选择搜索引擎", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                DropdownMenu(expanded = showEngineMenu, onDismissRequest = { onEngineMenu(false) }) {
                    DropdownMenuItem(text = { Text("搜索引擎：$selectedName") }, onClick = { onEngineMenu(false) }, enabled = false)
                    builtIns.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(if (selectedName == provider.name) "✓  ${provider.name}" else provider.name) },
                            onClick = { onSelectEngine(provider) }
                        )
                    }
                    DropdownMenuItem(text = { Text("自定义搜索引擎…") }, onClick = onCustomEngine)
                }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("磁力搜索结果", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(selectedName, color = teal, style = MaterialTheme.typography.labelLarge)
                    }
                    when {
                        results.isNotEmpty() -> {
                            Text("找到 ${results.size} 条可识别磁力链接。", style = MaterialTheme.typography.bodyMedium)
                            results.forEach { item ->
                                MagnetResultCard(item, onParse, onDownload)
                            }
                        }
                        searchState == "loading" -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                                Text(searchMessage.ifBlank { "正在搜索…" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(onClick = onCancelSearch, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("取消搜索，返回首页") }
                        }
                        searchState in listOf("empty", "timeout", "error") -> {
                            Text(searchMessage, color = Color(0xFF4E5C65), style = MaterialTheme.typography.bodyMedium)
                            Text("部分搜索站会拦截自动访问，或只返回详情页；可在网页中手动复制 magnet 链接。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = onCancelSearch, modifier = Modifier.weight(1f).height(56.dp)) { Text("返回搜索") }
                                Button(onClick = onOpenWeb, modifier = Modifier.weight(1f).height(56.dp)) { Text("打开搜索网站") }
                            }
                        }
                        else -> Text("输入关键词后，搜索结果会显示在这里。", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("粘贴磁力链接", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = magnet,
                        onValueChange = onMagnetChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp).semantics { contentDescription = "磁力链接输入框" },
                        label = { Text("magnet 链接") },
                        placeholder = { Text("magnet:?xt=urn:btih:…") },
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(onClick = onParseMagnet, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("解析资源", style = MaterialTheme.typography.titleMedium) }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MagnetResultCard(item: MagnetResult, onParse: (MagnetResult) -> Unit, onDownload: (MagnetResult) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F8FA)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title.ifBlank { magnetTitle(item.magnet) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(item.magnet, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = Color(0xFF53646D))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onParse(item) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("解析 / 播放") }
                Button(onClick = { onDownload(item) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("下载 / 缓存") }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    title: String,
    entries: List<LibraryEntry>,
    emptyText: String,
    onOpen: (MagnetResult) -> Unit,
    onDownload: (MagnetResult) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Text(emptyText, Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries, key = { it.source }) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (entry.status.isNotBlank()) Text(entry.status, style = MaterialTheme.typography.bodySmall, color = teal)
                            if (entry.totalBytes > 0) {
                                LinearProgressIndicator(
                                    progress = { (entry.downloadedBytes.toFloat() / entry.totalBytes).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("${formatLibraryBytes(entry.downloadedBytes)} / ${formatLibraryBytes(entry.totalBytes)}", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { onOpen(MagnetResult(entry.title, entry.source)) }, modifier = Modifier.weight(1f).height(50.dp)) { Text("打开") }
                                Button(onClick = { onDownload(MagnetResult(entry.title, entry.source)) }, modifier = Modifier.weight(1f).height(50.dp)) { Text("继续下载") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun magnetTitle(magnet: String): String = runCatching {
    val dn = Regex("(?:[?&])dn=([^&]+)", RegexOption.IGNORE_CASE).find(magnet)?.groupValues?.get(1)
    if (dn.isNullOrBlank()) "磁力资源" else java.net.URLDecoder.decode(dn, "UTF-8")
}.getOrDefault("磁力资源")

private fun normalizeMagnetUri(raw: String): String? = runCatching {
    val decoded = Regex("&#(?:x([0-9a-fA-F]+)|(\\d+));", RegexOption.IGNORE_CASE).replace(raw.trim().replace("&amp;", "&", true)) { match ->
        val code = match.groups[1]?.value?.toInt(16) ?: match.groups[2]?.value?.toInt() ?: return@replace match.value
        code.toChar().toString()
    }.trim().trimEnd('*', '_', ' ')
    val match = Regex("(?i)([?&]xt=urn:btih:)([0-9a-f]{40}|[a-z2-7]{32})").find(decoded) ?: return null
    val prefix = decoded.substring(0, match.range.first) + match.groupValues[1]
    val suffix = decoded.substring(match.range.last + 1)
    val validSuffix = if (suffix.startsWith("&")) suffix else ""
    "$prefix${match.groupValues[2]}$validSuffix"
}.getOrNull()

private fun formatLibraryBytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "%.1f KB".format(value / 1024f)
    value < 1024L * 1024 * 1024 -> "%.1f MB".format(value / (1024f * 1024f))
    else -> "%.1f GB".format(value / (1024f * 1024f * 1024f))
}
