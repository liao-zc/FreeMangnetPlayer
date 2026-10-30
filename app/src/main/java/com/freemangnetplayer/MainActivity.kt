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
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class SearchProvider(
    val name: String,
    val homepage: String,
    val queryUrl: String? = null
)

private data class SearchPage(val label: String, val url: String)

private data class MagnetResult(val title: String, val magnet: String, val bytes: Long = 0, val sizeText: String = "", val type: String = "视频", val heat: String = "")

val builtInProviders = listOf(
    SearchProvider("clg.im", "https://clg.im/"),
    SearchProvider("BT搜索（dobt）", "https://mg.dobt.cc/"),
    SearchProvider("BD电影首发站", "https://www.bdflixs.com/", "https://www.bdflixs.com/?s=%s"),
    SearchProvider("Pomo", "https://pomo.mom/", "https://pomo.mom/?keyword=%s"),
    SearchProvider("美剧粉", "https://www.mjf2020.com/", "https://www.mjf2020.com/ss/?s=%s"),
    SearchProvider("高清族", "https://hdzu.org/", "https://hdzu.org/movie/filter?act=title&wd=%s")
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
        TorrentTasks.initialize(this)
        val inbound = intent?.data?.takeIf { it.scheme.equals("magnet", true) }?.toString()?.let(::normalizeMagnetUri)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val debug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val requestedEngine = if (debug) intent.getStringExtra("test-engine") else null
        val requestedQuery = if (debug) intent.getStringExtra("test-query") else null
        setContent {
            AppTheme(colorScheme = lightColorScheme(
                primary = teal,
                secondary = Color(0xFF00A89B),
                background = canvas,
                surface = Color.White,
                onBackground = ink,
                onSurface = ink
            )) {
                var keyword by remember { mutableStateOf(requestedQuery.orEmpty()) }
                var magnet by remember { mutableStateOf("") }
                var selectedTab by remember { mutableStateOf(intent.getStringExtra("open-tab") ?: "搜索") }
                var showEngineMenu by remember { mutableStateOf(false) }
                var showCustomDialog by remember { mutableStateOf(false) }
                var customName by remember { mutableStateOf(prefs.getString("custom-name", "自定义搜索") ?: "自定义搜索") }
                var customUrl by remember { mutableStateOf(prefs.getString("custom-url", "") ?: "") }
                val storedSearchName = requestedEngine ?: prefs.getString("search-name", null)
                val storedProvider = builtInProviders.firstOrNull { it.name == storedSearchName }
                val storedCustomIsValid = storedSearchName == customName && customUrl.startsWith("https://") ||
                    storedSearchName == customName && customUrl.startsWith("http://")
                var selectedName by remember { mutableStateOf(storedProvider?.name ?: if (storedCustomIsValid) customName else builtInProviders[0].name) }
                var selectedUrl by remember { mutableStateOf(storedProvider?.homepage ?: if (storedCustomIsValid) customUrl else builtInProviders[0].homepage) }
                var error by remember { mutableStateOf("") }
                var searchState by remember { mutableStateOf("idle") }
                var searchMessage by remember { mutableStateOf("") }
                var searchTrigger by remember { mutableIntStateOf(0) }
                var showSearchBrowser by remember { mutableStateOf(false) }
                var currentSearchUrl by remember { mutableStateOf("") }
                val results = remember { mutableStateListOf<MagnetResult>() }
                var pageNumber by remember { mutableIntStateOf(1) }
                var sourcePages by remember { mutableIntStateOf(0) }
                var searchComplete by remember { mutableStateOf(false) }
                var scannerReady by remember { mutableStateOf(false) }
                var nextPage by remember { mutableStateOf("") }
                val pageUrls = remember { mutableStateMapOf<Int, String>() }
                val pageCache = remember { mutableStateMapOf<Int, List<MagnetResult>>() }
                val pageNext = remember { mutableStateMapOf<Int, String>() }
                val downloads = remember { mutableStateListOf<LibraryEntry>() }
                val favorites = remember { mutableStateListOf<LibraryEntry>() }
                val caches = remember { mutableStateListOf<LibraryEntry>() }
                val playbacks = remember { mutableStateListOf<LibraryEntry>() }
                val focus = LocalFocusManager.current
                val lifecycleOwner = LocalLifecycleOwner.current

                BackHandler(enabled = selectedTab == "搜索结果") {
                    selectedTab = "搜索"
                }

                fun reloadLibrary() {
                    TorrentTasks.tasks.values.forEach { StoredVideos.sync(this@MainActivity, it) }
                    caches.clear(); caches.addAll(LibraryStore.readCaches(this@MainActivity))
                    downloads.clear()
                    downloads.addAll(LibraryStore.readDownloads(this@MainActivity))
                    favorites.clear()
                    favorites.addAll(LibraryStore.readFavorites(this@MainActivity))
                    playbacks.clear()
                    playbacks.addAll(LibraryStore.readPlaybacks(this@MainActivity))
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            reloadLibrary()
                            selectedName = prefs.getString("search-name", "clg.im") ?: "clg.im"
                            selectedUrl = prefs.getString("search-url", "https://clg.im/") ?: "https://clg.im/"
                            customName = prefs.getString("custom-name", "自定义搜索") ?: "自定义搜索"
                            customUrl = prefs.getString("custom-url", "") ?: ""
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(Unit) { while (true) { reloadLibrary(); delay(1500) } }

                LaunchedEffect(searchTrigger) {
                    if (searchTrigger == 0) return@LaunchedEffect
                    delay(40_000)
                    if (!searchComplete && searchState !in listOf("idle","empty","error")) {
                        searchComplete = true
                        magnetWebView?.evaluateJavascript("window.__fmpStopped=true",null)
                        searchState = "timeout"
                        searchMessage = "搜索未完成"
                    }
                }

                fun beginSearch() {
                    val q = keyword.trim()
                    if (q.isEmpty()) {
                        error = "先输入要搜索的关键词"
                        return
                    }
                    HistoryStore.record(this@MainActivity,"搜索",HistoryEntry(q,q,System.currentTimeMillis()))
                    focus.clearFocus()
                    error = ""
                    sourcePages=0;searchComplete=false;scannerReady=false;pageNumber = 1; nextPage = ""; pageUrls.clear(); pageCache.clear(); pageNext.clear()
                    results.clear()
                    selectedTab = "搜索结果"
                    searchState = "loading"
                    searchMessage = "搜索中"
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

                fun changePage(number: Int, url: String) {
                    if (url.isBlank() || number < 1) return
                    pageCache[pageNumber] = results.toList(); pageNext[pageNumber] = nextPage
                    pageNumber = number; nextPage = pageNext[number].orEmpty()
                    results.clear(); results.addAll(pageCache[number].orEmpty())
                    pageUrls[number] = url
                    currentSearchUrl = url; searchPageUrl = url
                    firstPageLoad = false; searchNeedsFormSubmit = false
                    searchState = if (results.isEmpty()) "loading" else "results"
                    searchComplete=false;scannerReady=false;searchMessage = "搜索中"
                    searchTrigger++
                }

                LaunchedEffect(Unit) { if (!requestedQuery.isNullOrBlank()) beginSearch() }
                LaunchedEffect(searchState, results.size, pageNumber, nextPage) {
                    if (debug && searchTrigger > 0) {
                        val rows = JSONArray()
                        results.forEach { rows.put(JSONObject().put("title", it.title).put("magnet", it.magnet)) }
                        java.io.File(filesDir, "search-check.json").writeText(JSONObject().put("engine", activeProvider)
                            .put("page", pageNumber).put("next", nextPage).put("query", activeQuery).put("state", searchState).put("message", searchMessage).put("results", rows).toString())
                    }
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
                                selected = selectedTab == "播放",
                                onClick = { selectedTab = "播放"; reloadLibrary() },
                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                label = { Text("播放") }
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
                            NavigationBarItem(
                                selected = false,
                                onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("设置") }
                            )
                        }
                    }
                ) { insets ->
                    Box(Modifier.fillMaxSize().background(canvas).padding(insets)) {
                        when (selectedTab) {
                            "搜索结果" -> SearchResultsScreen(
                                page = pageNumber,
                                totalPages=sourcePages,
                                onJump={ number -> changePage(number,pageUrls[number].orEmpty()) },
                                knownPages=pageUrls.keys.toSet(),
                                nextAvailable = nextPage.isNotBlank(),
                                onPrevious = { changePage(pageNumber - 1, pageUrls[pageNumber - 1].orEmpty()) },
                                onNext = { changePage(pageNumber + 1, nextPage) },
                                keyword = activeQuery,
                                engine = activeProvider,
                                state = searchState,
                                message = searchMessage,
                                results = results,
                                onBack = { magnetWebView?.stopLoading(); selectedTab = "搜索" },
                                onRetry = ::beginSearch,
                                onOpenWeb = {
                                    showSearchBrowser = true
                                },
                                onCancel = {
                                    searchState = "idle"
                                    searchMessage = ""
                                    results.clear()
                                    magnetWebView?.stopLoading()
                                    selectedTab = "搜索"
                                },
                                onParse = { openTorrent(it, "parse") },
                                onDownload = { openTorrent(it, "download") }
                            )
                            "历史" -> HistoryScreen(onBack={ selectedTab="播放" }, onOpen={ kind,entry -> if(kind=="搜索") {keyword=entry.title;beginSearch()} else openTorrent(MagnetResult(entry.title,entry.source),"play") })
                            "播放" -> Column {
                                Row { TextButton(onClick={selectedTab="历史"}) { Text("历史记录") }; TextButton(onClick = { selectedTab = "缓存"; reloadLibrary() }) { Text("缓存视频 (${caches.size})") } }
                                ManagedLibrary("播放列表", playbacks, "播放", { openTorrent(MagnetResult(it.title, it.source), "play") }, { reloadLibrary() })
                            }
                            "缓存" -> ManagedLibrary("缓存视频", caches, "缓存", { openTorrent(MagnetResult(it.title, it.source), "play") }, { reloadLibrary() })
                            "下载" -> ManagedLibrary("下载任务", downloads, "下载", { openTorrent(MagnetResult(it.title, it.source), "play") }, { reloadLibrary() })
                            "收藏" -> ManagedLibrary("我的收藏", favorites, "收藏", { openTorrent(MagnetResult(it.title, it.source), "play") }, { reloadLibrary() })
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

                        if (searchTrigger > 0 && !showSearchBrowser) key(searchTrigger) { AndroidView(
                            // A real viewport is required for form detection. Keep the renderer
                            // outside the content area, transparent, and out of accessibility.
                            modifier = Modifier.offset(x = (-10000).dp).size(360.dp, 640.dp).alpha(0f),
                            factory = { context ->
                                val generation = searchTrigger
                                WebView(context).apply {
                                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                    magnetWebView = this
                                    searchBridge = MagnetSearchBridge(onResource={ row -> runOnUiThread {
                                        if(generation==searchTrigger && selectedTab=="搜索结果" && !searchComplete) {
                                            val link=normalizeMagnetUri(row.optString("source"))
                                            val index=results.indexOfFirst { it.magnet==link }
                                            if(index>=0) { val old=results[index];results[index]=old.copy(bytes=row.optLong("bytes"),sizeText=row.optString("sizeText"),type=row.optString("type","视频"),heat=row.optString("heat")) }
                                        }
                                    } }, onPagination={ row -> runOnUiThread {
                                        if(generation==searchTrigger && !searchComplete) {
                                            scannerReady=row.optBoolean("complete");if(row.optInt("totalPages")>0) sourcePages=row.getInt("totalPages")
                                            row.optJSONArray("pages")?.let { array -> for(i in 0 until array.length()) {val link=array.getJSONObject(i);pageUrls[link.getInt("page")]=link.getString("url") } }
                                        }
                                     } }, onMagnet = { title, link ->
                                        runOnUiThread {
                                            if (generation == searchTrigger && selectedTab == "搜索结果" && results.none { it.magnet == link }) {
                                                results.add(MagnetResult(title.ifBlank { magnetTitle(link) }, link))
                                                searchState = "results"
                                                searchMessage = ""
                                            }
                                        }
                                    }, onPage = { url, next, pending, failures -> runOnUiThread {
                                        if (generation == searchTrigger && !searchComplete) {
                                            if (url.startsWith("http")) { pageUrls[pageNumber] = url; currentSearchUrl = url }
                                            nextPage = next
                                            searchMessage = if(pending>0) "搜索中" else if(failures>0) "搜索未完成" else ""
                                            if(pending==0 && scannerReady && !searchNeedsFormSubmit) { searchComplete=true;searchState=if(failures>0) "timeout" else if(results.isEmpty()) "empty" else "results" }

                                        }
                                    } })
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.javaScriptCanOpenWindowsAutomatically = false
                                    settings.setSupportMultipleWindows(false)
                                    settings.loadsImagesAutomatically = false
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                                    addJavascriptInterface(searchBridge!!, "FreeMagnetResults")
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            if (newProgress >= 30 && generation == searchTrigger) {
                                                scanForMagnets(view, activeQuery)
                                                if (firstPageLoad && searchNeedsFormSubmit) {
                                                    view?.evaluateJavascript("Boolean(document.querySelector('input[type=search],input[type=text]'))") { value ->
                                                        if (value == "true" && firstPageLoad && generation == searchTrigger) {
                                                            firstPageLoad = false
                                                            injectSearch(view, activeQuery)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                                            val url = request.url.toString()
                                            if (url.startsWith("magnet:", true)) {
                                                val normalized = normalizeMagnetUri(url)
                                                if (normalized != null) {
                                                    searchBridge?.onResults(org.json.JSONArray().put(org.json.JSONObject().put("title", magnetTitle(normalized)).put("source", normalized)).toString())
                                                }
                                                return true
                                            }
                                            return false
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            if (generation != searchTrigger) return
                                            // Perform the same ordinary confirmation click as the
                                            // independent browser test. Interactive challenges stay
                                            // accessible through the native browser dialog.
                                            view?.evaluateJavascript("(function(){var b=document.querySelector('.challenge-c');if(b){b.click();return true}return false})()") { value ->
                                                if (value == "true" && generation == searchTrigger) searchMessage = "网站要求确认访问，正在等待验证返回…"
                                            }
                                            // Wrapper sites host the actual search page in an iframe.
                                            if (activeProvider == builtInProviders[1].name) view?.evaluateJavascript("JSON.stringify(Array.from(document.querySelectorAll('iframe[src]')).map(x=>x.src).find(x=>/^https?:/.test(x))||'')") { value ->
                                                val frame = runCatching { JSONArray("[$value]").optString(0) }.getOrDefault("")
                                                if (generation == searchTrigger && frame.isNotBlank() && frame != url) {
                                                    firstPageLoad = true
                                                    searchNeedsFormSubmit = true
                                                    view.loadUrl(frame)
                                                }
                                            }
                                            scanForMagnets(view, activeQuery)
                                            if (firstPageLoad && searchNeedsFormSubmit) {
                                                view?.evaluateJavascript("Boolean(document.querySelector('input[type=search],input[type=text]'))") { value ->
                                                    if (value == "true" && generation == searchTrigger) {
                                                        firstPageLoad = false
                                                        injectSearch(view, activeQuery)
                                                    }
                                                }
                                            } else if (searchState == "loading") {
                                                searchMessage = "搜索中"
                                            }
                                        }

                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                runOnUiThread {
                                                    searchState = "error"
                                                    searchComplete=true;searchMessage = "搜索未完成"
                                                }
                                            }
                                        }
                                        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: android.webkit.WebResourceResponse?) {
                                            if (request?.isForMainFrame == true && generation == searchTrigger) {
                                                searchState = "error"
                                                searchComplete=true;searchMessage = "搜索未完成"
                                            }
                                        }
                                    }
                                    loadUrl(searchPageUrl)
                                }
                            },
                            onRelease = { if (magnetWebView === it) magnetWebView = null; it.stopLoading(); it.removeJavascriptInterface("FreeMagnetResults"); it.destroy() }
                        ) }

                        if (showSearchBrowser) Dialog(onDismissRequest = { showSearchBrowser = false }) {
                            Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = Color.White) {
                                Column {
                                    Button(onClick = { showSearchBrowser = false }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("关闭网页并返回搜索结果") }
                                    Text("可在此完成网站验证；磁力链接仍由应用提取。", Modifier.padding(8.dp))
                                    AndroidView(factory = { context ->
                                        WebView(context).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                                            addJavascriptInterface(MagnetSearchBridge { title, link -> runOnUiThread {
                                                if (results.none { it.magnet == link }) results.add(MagnetResult(title, link))
                                                searchState = "results"
                                            } }, "FreeMagnetResults")
                                            webViewClient = object : WebViewClient() {
                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    scanForMagnets(view, activeQuery)
                                                    if (searchNeedsFormSubmit) view?.evaluateJavascript("Boolean(document.querySelector('input[type=search],input[type=text]'))") { present ->
                                                        if (present == "true") { searchNeedsFormSubmit = false; injectSearch(view, activeQuery) }
                                                    }
                                                }
                                            }
                                            loadUrl(currentSearchUrl)
                                        }
                                    }, modifier = Modifier.weight(1f).fillMaxWidth(), onRelease = { it.destroy() })
                                }
                            }
                        }

                    }
                }

                if (error.isNotBlank() && selectedTab != "搜索") {
                    LaunchedEffect(error) { error = "" }
                }
            }
        }
        inbound?.let { openTorrent(MagnetResult(magnetTitle(it), it), intent.getStringExtra("mode") ?: "parse") }
        if (debug) intent.getStringExtra("test-torrent-url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let {
            openTorrent(MagnetResult(intent.getStringExtra("title").orEmpty(), it), intent.getStringExtra("mode") ?: "parse")
        }
    }

    private fun injectSearch(view: WebView?, query: String) {
        val quoted = JSONObject.quote(query)
        val script = """(function(){var q=$quoted,n=0,t=setInterval(function(){var a=[...document.querySelectorAll('input[type=search],input[name=q],input[name=k],input[name=keyword],input[name=title],input[type=text]')].find(x=>x.offsetWidth>0&&!x.disabled);if(a){clearInterval(t);var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(a,q);a.dispatchEvent(new Event('input',{bubbles:true}));a.dispatchEvent(new Event('change',{bubbles:true}));var b=a.form&&a.form.querySelector('button[type=submit],input[type=submit],button[lay-submit]');if(b)b.click();else if(a.form){a.form.requestSubmit?a.form.requestSubmit():a.form.submit();}else a.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));}if(++n>50)clearInterval(t);},250);})()"""
        view?.evaluateJavascript(script, null)
    }

    private fun scanForMagnets(view: WebView?, query: String) {
        val quotedQuery = JSONObject.quote(query)
        val script = assets.open("magnet-search.js").bufferedReader().use { it.readText() }.replace("__QUERY_JSON__", quotedQuery)
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

private class MagnetSearchBridge(
    private val onResource: (JSONObject) -> Unit = {},
    private val onPagination: (JSONObject) -> Unit = {},
    private val onPage: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },
    private val onMagnet: (String, String) -> Unit
) {
    @JavascriptInterface
    fun onPageInfo(json: String) {
        runCatching { val value = JSONObject(json); onPagination(value); onPage(value.optString("url"), value.optString("next"), value.optInt("pending"), value.optInt("failures")) }
    }
    @JavascriptInterface
    fun onResults(json: String) {
        runCatching {
            val rows = JSONArray(json)
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val link = normalizeMagnetUri(row.optString("source"))
                if (link != null) {
                    onMagnet(row.optString("title"), link); onResource(row)
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
private fun SearchResultsScreen(
    page: Int, totalPages: Int, knownPages: Set<Int>, onJump: (Int) -> Unit,
    nextAvailable: Boolean, onPrevious: () -> Unit, onNext: () -> Unit,
    keyword: String, engine: String, state: String, message: String, results: List<MagnetResult>,
    onBack: () -> Unit, onRetry: () -> Unit, onOpenWeb: () -> Unit, onCancel: () -> Unit,
    onParse: (MagnetResult) -> Unit, onDownload: (MagnetResult) -> Unit
) {
    var localPage by remember(page,keyword) { mutableIntStateOf(1) }
    var jump by remember { mutableStateOf("") };var jumpError by remember { mutableStateOf("") }
    var localJump by remember(page) { mutableStateOf("") }
    val localPages=((results.size+19)/20).coerceAtLeast(1);val shown=localPage.coerceAtMost(localPages)
    val list=androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(shown,page) { list.scrollToItem(0) }
    Column(Modifier.fillMaxSize().background(canvas).padding(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack) { Text("‹ 返回") };Column { Text("搜索结果",style=MaterialTheme.typography.titleLarge);Text(keyword,style=MaterialTheme.typography.bodySmall) }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick=onPrevious,enabled=page>1) { Text("上一页") }
            Text(if(totalPages>0) "第 $page / $totalPages 页" else "第 $page 页 · 已发现 ${knownPages.maxOrNull() ?: page} 页",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=onNext,enabled=nextAvailable) { Text("下一页") }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(jump,{jump=it.filter(Char::isDigit).take(6)},label={Text("跳转网站页")},singleLine=true,modifier=Modifier.weight(1f))
            TextButton(onClick={val n=jump.toIntOrNull();if(n!=null && n in knownPages) {onJump(n);jumpError=""} else jumpError="尚未取得该页地址"}) { Text("跳转") }
        }
        if(jumpError.isNotEmpty()) Text(jumpError,style=MaterialTheme.typography.bodySmall)
        if(state=="loading" || message=="搜索中") { LinearProgressIndicator(Modifier.fillMaxWidth());Text("搜索中") }
        if(state in listOf("error","timeout") || message=="搜索未完成") Text("搜索未完成",color=MaterialTheme.colorScheme.error)
        if(results.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={localPage=shown-1},enabled=shown>1) { Text("? 20 ?") }
                Text("结果 $shown / $localPages 页 · ${results.size} 条",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={localPage=shown+1},enabled=shown<localPages) { Text("? 20 ?") }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(localJump,{localJump=it.filter(Char::isDigit).take(5)},label={Text("跳转结果页（1–$localPages）")},singleLine=true,modifier=Modifier.weight(1f))
                TextButton(onClick={localJump.toIntOrNull()?.takeIf{it in 1..localPages}?.let{localPage=it}}) { Text("跳转结果页") }
            }
            LazyColumn(Modifier.weight(1f),state=list,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                items(results.drop((shown-1)*20).take(20),key={it.magnet}) { item -> MagnetResultCard(item,onParse,onDownload) }
            }
        } else if(state!="loading") {
            Text(if(state=="empty") "没有搜索结果" else "搜索未完成",Modifier.padding(16.dp))
            Button(onClick=onRetry) { Text("重试搜索") };OutlinedButton(onClick=onBack) { Text("返回首页") }
        } else TextButton(onClick=onCancel) { Text("取消搜索并返回") }
    }
}

@Composable
private fun MagnetResultCard(item: MagnetResult, onParse: (MagnetResult) -> Unit, onDownload: (MagnetResult) -> Unit) {
    val c=androidx.compose.ui.platform.LocalContext.current
    val textStyle=MaterialTheme.typography.bodyMedium.copy(fontSize=(14*DisplaySettings.scale(c)).sp,lineHeight=(16.8f*DisplaySettings.scale(c)).sp,color=Color(0xFF333333))
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(item.title.ifBlank { "待获取文件名" },style=textStyle)
            Text("${item.sizeText.ifBlank { if(item.bytes>0) formatLibraryBytes(item.bytes) else "等待元数据大小" }} · ${item.type} · 热度 ${item.heat.ifBlank { "未提供评分" }}",style=textStyle)
            Row {
                TextButton(onClick={onParse(item)}) { Text("播放",style=textStyle) }
                TextButton(onClick={onDownload(item)}) { Text("下载 / 缓存",style=textStyle) }
                TextButton(onClick={LibraryStore.addFavorite(c,item.title,item.magnet)}) { Text("收藏",style=textStyle) }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    title: String,
    entries: List<LibraryEntry>,
    emptyText: String,
    openLabel: String = "打开",
    downloadLabel: String = "继续下载",
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
                                OutlinedButton(onClick = { onOpen(MagnetResult(entry.title, entry.source)) }, modifier = Modifier.weight(1f).height(50.dp)) { Text(openLabel) }
                                Button(onClick = { onDownload(MagnetResult(entry.title, entry.source)) }, modifier = Modifier.weight(1f).height(50.dp)) { Text(downloadLabel) }
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

/** Accept a pasted URL, HTML entity or encoded URL; canonicalize base32 BTIH. */
internal fun normalizeMagnetUri(raw: String): String? = runCatching {
    var decoded = Regex("&#(?:x([0-9a-fA-F]+)|(\\d+));", RegexOption.IGNORE_CASE)
        .replace(raw.replace("&amp;", "&", true)) { match ->
            val code = match.groups[1]?.value?.toInt(16) ?: match.groups[2]!!.value.toInt()
            if (Character.isValidCodePoint(code)) String(Character.toChars(code)) else ""
        }
    if (!decoded.contains("magnet:?", true)) decoded = java.net.URLDecoder.decode(decoded, "UTF-8")
    val start = decoded.indexOf("magnet:?", ignoreCase = true)
    if (start < 0) return null
    val candidate = decoded.substring(start).substringBefore('"').substringBefore('<').substringBefore('>')
        .substringBefore('\n').substringBefore('\r').trim().trimEnd('*', '_', ')', ']', ' ')
    val parameters = candidate.substringAfter('?').split('&').mapNotNull { parameter ->
        val key = parameter.substringBefore('=').lowercase()
        val value = parameter.substringAfter('=', "")
        if (key in setOf("xt", "dn", "tr", "xs", "as", "ws", "x.pe", "so") && value.isNotBlank()) key to value else null
    }
    val xt = parameters.firstNotNullOfOrNull { (key, value) ->
        if (key != "xt") null else java.net.URLDecoder.decode(value, "UTF-8")
            .let { Regex("(?i)^urn:btih:([0-9a-f]{40}|[a-z2-7]{32})").find(it)?.groupValues?.get(1) }
    } ?: return null
    val hash = if (xt.length == 40) xt.lowercase() else {
        var bits = 0; var accumulator = 0
        val bytes = java.io.ByteArrayOutputStream()
        for (letter in xt.uppercase()) {
            accumulator = (accumulator shl 5) or "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(letter)
            bits += 5
            if (bits >= 8) { bits -= 8; bytes.write((accumulator shr bits) and 255) }
        }
        bytes.toByteArray().joinToString("") { "%02x".format(it) }
    }
    "magnet:?xt=urn:btih:$hash" + parameters.filter { it.first != "xt" }
        .joinToString("") { (key, value) -> "&$key=$value" }
}.getOrNull()

private fun formatLibraryBytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "%.1f KB".format(value / 1024f)
    value < 1024L * 1024 * 1024 -> "%.1f MB".format(value / (1024f * 1024f))
    else -> "%.1f GB".format(value / (1024f * 1024f * 1024f))
}
