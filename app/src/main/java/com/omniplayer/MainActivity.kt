package com.omniplayer

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class SearchProvider(
    val name: String,
    val homepage: String,
    val queryUrl: String? = null
)


private data class MagnetResult(val title: String, val magnet: String, val bytes: Long = 0, val sizeText: String = "", val type: String = "视频", val heat: String = "", val publishedAt: String = "", val format: String = "")

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
private const val PREFS_NAME = "player-settings"

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private var magnetWebView: WebView? = null
    private var activeQuery = ""
    private var activeProvider = ""
    private var searchPageUrl = ""
    private var firstPageLoad = true
    private var searchBridge: MagnetSearchBridge? = null
    private var searchNeedsFormSubmit = false
    private var navigateHome: (() -> Unit)? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigateHome?.invoke()
    }

    override fun onResume() {
        super.onResume()
        PendingUpdateInstaller.resume(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TorrentTasks.initialize(this)
        val inbound = intent?.data?.takeIf { it.scheme.equals("magnet", true) }?.toString()?.let(::normalizeMagnetUri)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val debug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val requestedEngine = if (debug) intent.getStringExtra("test-engine") else null
        val requestedQuery = if (debug) intent.getStringExtra("test-query") else null
        setContent {
            val initialCanvas = DisplaySettings.background(this@MainActivity)
            AppTheme(colorScheme = lightColorScheme(
                primary = teal,
                secondary = Color(0xFF00A89B),
                background = initialCanvas,
                surface = Color.White,
                onBackground = ink,
                onSurface = ink
            )) {
                val canvas = DisplaySettings.background(this@MainActivity)
                var availableUpdate by remember { mutableStateOf<AppUpdater.Release?>(null) }
                val updateScope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    val updatePrefs = SettingsStore.prefs(this@MainActivity)
                    while (true) {
                        if (updatePrefs.getBoolean("auto-update-check", false) && !UpdatePromptGate.promptedThisProcess) {
                            runCatching { withContext(Dispatchers.IO) { AppUpdater.latest(this@MainActivity) } }
                                .onSuccess { release ->
                                    if (release != null) {
                                        UpdatePromptGate.promptedThisProcess = true
                                        availableUpdate = release
                                    }
                                }
                        }
                        delay(30 * 60 * 1000L)
                    }
                }
                var keyword by remember { mutableStateOf(requestedQuery.orEmpty()) }
                var magnet by remember { mutableStateOf("") }
                var searchProviders by remember { mutableStateOf(SearchEngineStore.load(this@MainActivity)) }
                var selectedTab by remember { mutableStateOf(intent.getStringExtra("open-tab") ?: if (inbound != null || !requestedQuery.isNullOrBlank()) "搜索" else "门户") }
                SideEffect { navigateHome = { selectedTab = "门户" } }
                var showEngineMenu by remember { mutableStateOf(false) }
                var showCustomDialog by remember { mutableStateOf(false) }
                var customName by remember { mutableStateOf(prefs.getString("custom-name", "自定义搜索") ?: "自定义搜索") }
                var customUrl by remember { mutableStateOf(prefs.getString("custom-url", "") ?: "") }
                val storedSearchName = requestedEngine ?: prefs.getString("search-name", null)
                val storedProvider = searchProviders.firstOrNull { it.name == storedSearchName }
                val storedCustomIsValid = storedSearchName == customName && customUrl.startsWith("https://") ||
                    storedSearchName == customName && customUrl.startsWith("http://")
                var selectedName by remember { mutableStateOf(storedProvider?.name ?: if (storedCustomIsValid) customName else SearchEngineStore.defaultName()) }
                var selectedUrl by remember { mutableStateOf(storedProvider?.homepage ?: if (storedCustomIsValid) customUrl else searchProviders.firstOrNull { it.name == SearchEngineStore.defaultName() }?.homepage.orEmpty()) }
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
                var completionTicket by remember { mutableIntStateOf(0) }
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
                val magnetSection = selectedTab != "门户"

                BackHandler(enabled = selectedTab == "搜索结果") {
                    selectedTab = "搜索"
                }
                BackHandler(enabled = selectedTab == "门户") { finish() }
                BackHandler(enabled = selectedTab == "历史") { selectedTab = "播放" }
                BackHandler(enabled = selectedTab in setOf("搜索", "播放", "下载", "收藏", "缓存")) { selectedTab = "门户" }

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

                fun syncSearchResultSizes() {
                    results.forEachIndexed { index, result ->
                        val hash = Regex("(?i)xt=urn:btih:([0-9a-f]{40})").find(result.magnet)?.groupValues?.get(1)?.lowercase() ?: return@forEachIndexed
                        val total = TorrentTasks.tasks[hash]?.files?.sumOf { it.size } ?: 0L
                        val files = TorrentTasks.tasks[hash]?.files.orEmpty()
                        val parsedFormat = torrentResourceFormat(files, result.magnet)
                        if (total > 0L || parsedFormat.isNotBlank()) results[index] = result.copy(
                            bytes = total.takeIf { it > 0L } ?: result.bytes,
                            sizeText = if (total > 0L) formatLibraryBytes(total) else result.sizeText,
                            format = parsedFormat.ifBlank { result.format }
                        )
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            reloadLibrary()
                            if (requestedEngine == null) {
                            searchProviders = SearchEngineStore.load(this@MainActivity)
                            selectedName = prefs.getString("search-name", SearchEngineStore.defaultName()) ?: SearchEngineStore.defaultName()
                            selectedUrl = searchProviders.firstOrNull { it.name == selectedName }?.homepage ?: prefs.getString("search-url", "") ?: ""
                            }
                            customName = prefs.getString("custom-name", "自定义搜索") ?: "自定义搜索"
                            customUrl = prefs.getString("custom-url", "") ?: ""
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(Unit) { while (true) { reloadLibrary(); syncSearchResultSizes(); delay(1500) } }

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
                    val provider = searchProviders.firstOrNull { it.name == selectedName }
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
                LaunchedEffect(searchState, results.toList(), pageNumber, nextPage, sourcePages, searchComplete) {
                    if (debug && searchTrigger > 0) {
                        val rows = JSONArray()
                        results.forEach { rows.put(JSONObject().put("title", it.title).put("magnet", it.magnet).put("bytes",it.bytes).put("sizeText",it.sizeText).put("type",it.type).put("heat",it.heat).put("publishedAt",it.publishedAt).put("format",it.format)) }
                        java.io.File(filesDir, "search-check.json").writeText(JSONObject().put("engine", activeProvider)
                            .put("page", pageNumber).put("totalPages",sourcePages).put("complete",searchComplete).put("next", nextPage).put("query", activeQuery).put("state", searchState).put("message", searchMessage).put("results", rows).toString())
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
                                    val existing = searchProviders.indexOfFirst { it.name == name }
                                    val endpoint = url.substringBefore("%s").trimEnd('?', '&', '=')
                                    val row = SearchProvider(name, endpoint.ifBlank { url }, url.takeIf { it.contains("%s") })
                                    val changed = searchProviders.toMutableList()
                                    if (existing >= 0) changed[existing] = row else if (changed.size < 10) changed.add(row) else error = "最多支持 10 个搜索引擎，请先编辑或删除一个"
                                    if (error.isBlank()) runCatching {
                                        SearchEngineStore.save(this@MainActivity, changed, name)
                                        searchProviders = changed
                                        selectedName = name
                                        selectedUrl = row.homepage
                                        showCustomDialog = false
                                    }.onFailure { error = "无法保存搜索引擎：${it.localizedMessage}" }
                                } else error = "地址需要以 http:// 或 https:// 开头"
                            }) { Text("保存并使用") }
                        },
                        dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("取消") } }
                    )
                }

                Scaffold(
                    containerColor = canvas,
                    topBar = {
                        if (magnetSection) TopAppBar(
                            title = { Text(if (selectedTab == "搜索结果") "搜索结果" else "磁力播放") },
                            navigationIcon = { IconButton(onClick = { if (selectedTab == "搜索结果") selectedTab = "搜索" else selectedTab = "门户" }) { Icon(Icons.Default.ArrowBack, if (selectedTab == "搜索结果") "返回搜索" else "返回主页") } },
                            actions = { TextButton(
                                onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java).putExtra("section", "magnet")) },
                                modifier = Modifier.semantics { contentDescription = "磁播模块设置" }
                            ) { Text("设置") } }
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Color.White) {
                            NavigationBarItem(
                                selected = selectedTab == "门户",
                                onClick = { selectedTab = "门户" },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("首页") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == "设置",
                                onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java).putExtra("section", "menu")) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("设置") }
                            )
                            if (magnetSection) {
                                NavigationBarItem(selected = selectedTab == "搜索" || selectedTab == "搜索结果", onClick = { selectedTab = "搜索" }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("搜索") })
                                NavigationBarItem(selected = selectedTab == "播放" || selectedTab == "缓存", onClick = { selectedTab = "播放"; reloadLibrary() }, icon = { Icon(Icons.Default.PlayArrow, null) }, label = { Text("播放") })
                                NavigationBarItem(selected = selectedTab == "下载", onClick = { selectedTab = "下载"; reloadLibrary() }, icon = { Icon(Icons.Default.Download, null) }, label = { Text("下载") })
                                NavigationBarItem(selected = selectedTab == "收藏", onClick = { selectedTab = "收藏"; reloadLibrary() }, icon = { Icon(Icons.Default.Bookmarks, null) }, label = { Text("收藏") })
                            }

                        }
                    }
                ) { insets ->
                    Box(Modifier.fillMaxSize().background(canvas).padding(insets)) {
                        when (selectedTab) {
                            "门户" -> Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Spacer(Modifier.height(22.dp))
                                Text("OmniPlayer", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = teal)
                                Text("万能播放器", style = MaterialTheme.typography.titleMedium, color = ink)
                                Spacer(Modifier.height(12.dp))
                                Card(Modifier.fillMaxWidth().height(148.dp).clickable { startActivity(Intent(this@MainActivity, IptvActivity::class.java)) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Row(Modifier.fillMaxSize().padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                        Text("📺", fontSize = 38.sp)
                                        Column { Text("电视直播", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("频道直播 · 收藏常看频道", style = MaterialTheme.typography.bodyMedium) }
                                    }
                                }
                                Card(Modifier.fillMaxWidth().height(148.dp).clickable { selectedTab = "搜索" }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Row(Modifier.fillMaxSize().padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                        Text("🧲", fontSize = 38.sp)
                                        Column { Text("磁力播放", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("搜索 · 解析 · 下载 · 播放", style = MaterialTheme.typography.bodyMedium) }
                                    }
                                }
                            }
                            "搜索结果" -> SearchResultsScreen(
                                page = pageNumber,
                                totalPages=sourcePages,
                                knownPages=pageUrls.keys.toSet(),
                                onJump={ number -> changePage(number,pageUrls[number].orEmpty()) },
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
                                Row { TextButton(onClick={selectedTab="历史"}) { Text("历史记录") }; TextButton(onClick = { selectedTab = "缓存"; reloadLibrary() }) { Text("缓存视频 (${caches.count { it.unplayableReason.isBlank() }})") } }
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
                                builtIns = searchProviders,
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
                                            val identity=magnetIdentity(link.orEmpty())
                                            val index=results.indexOfFirst { magnetIdentity(it.magnet)==identity }
                                            if(index>=0) { val old=results[index];val foundBytes=row.optLong("bytes");val foundSize=row.optString("sizeText");results[index]=old.copy(title=row.optString("title").takeIf { it.isNotBlank() && !it.startsWith("magnet:",true) } ?: old.title,bytes=foundBytes.takeIf { it>0L } ?: old.bytes,sizeText=foundSize.takeIf { it.isNotBlank() } ?: old.sizeText,type=row.optString("type","视频"),heat=row.optString("heat").ifBlank { old.heat },publishedAt=row.optString("publishedAt").ifBlank { old.publishedAt },format=row.optString("format").ifBlank { old.format }) }
                                        }
                                    } }, onPagination={ row -> runOnUiThread {
                                        if(debug) java.io.File(filesDir,"search-scan.json").writeText(row.toString())
                                        if(generation==searchTrigger && !searchComplete) {
                                            scannerReady=row.optBoolean("complete");if(row.optInt("totalPages")>0) sourcePages=row.getInt("totalPages")
                                            row.optJSONArray("pages")?.let { array -> for(i in 0 until array.length()) {val link=array.getJSONObject(i);pageUrls[link.getInt("page")]=link.getString("url") } }
                                        }
                                     } }, onMagnet = { title, link ->
                                        runOnUiThread {
                                            if (generation == searchTrigger && !searchComplete && selectedTab == "搜索结果" && isRelevantSearchTitle(activeQuery, title.ifBlank { magnetTitle(link) })) {
                                                val name = title.ifBlank { magnetTitle(link) }
                                                if (results.none { magnetIdentity(it.magnet) == magnetIdentity(link) }) results.add(MagnetResult(name, link, format = detectResourceFormat(name, link)))
                                                searchState = "results"
                                                searchMessage = ""
                                            }
                                        }
                                    }, onPage = { url, next, pending, failures -> runOnUiThread {
                                        if (generation == searchTrigger && !searchComplete) {
                                            if (url.startsWith("http")) { pageUrls[pageNumber] = url; currentSearchUrl = url }
                                            nextPage = next
                                            searchMessage = if(pending>0) "搜索中" else if(failures>0) "搜索未完成" else ""
                                            val ticket=++completionTicket
                                            if(pending==0 && scannerReady && !searchNeedsFormSubmit) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                if(ticket==completionTicket && generation==searchTrigger && !searchComplete) {
                                                    searchComplete=true;searchState=if(failures>0) "timeout" else if(results.isEmpty()) "empty" else "results"
                                                }
                                            },1000)

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
                                            if (searchProviders.firstOrNull { it.name == activeProvider }?.homepage?.contains("mg.dobt.cc", true) == true) view?.evaluateJavascript("JSON.stringify(Array.from(document.querySelectorAll('iframe[src]')).map(x=>x.src).find(x=>/^https?:/.test(x))||'')") { value ->
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
                availableUpdate?.let { update ->
                    AlertDialog(
                        onDismissRequest = { availableUpdate = null },
                        title = { Text("发现 OmniPlayer 新版本 ${update.version}") },
                        text = { Text("更新包下载后会打开 Android 系统安装确认页。安装需要系统确认；完成后会尝试重新打开应用。应用设置和私有缓存会保留。") },
                        confirmButton = { TextButton(onClick = {
                            updateScope.launch(Dispatchers.IO) { runCatching { AppUpdater.enqueue(this@MainActivity, update) } }
                            availableUpdate = null
                        }) { Text("下载并更新") } },
                        dismissButton = { TextButton(onClick = { availableUpdate = null }) { Text("稍后") } }
                    )
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
    val context = LocalContext.current
    var sortMenu by remember { mutableStateOf(false) }
    var sortMode by remember(keyword, engine) { mutableStateOf("匹配度") }
    var jumpDialog by remember { mutableStateOf(false) }
    var jumpPage by remember { mutableStateOf("") }
    var jumpError by remember { mutableStateOf("") }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    var localPage by remember(keyword, engine, page) { mutableIntStateOf(1) }
    val sortedResults = remember(results.toList(), sortMode) {
        when (sortMode) {
            "时间" -> results.sortedWith(compareByDescending<MagnetResult> { parsePublishedDate(it.publishedAt) }.thenByDescending { it.title.length })
            "热度" -> results.sortedWith(compareByDescending<MagnetResult> { parseHeat(it.heat) }.thenBy { it.title })
            else -> results.sortedWith(compareByDescending<MagnetResult> { item -> keyword.lowercase().split(' ').count { word -> word.isNotBlank() && item.title.lowercase().contains(word) } }.thenByDescending { it.title.length })
        }
    }
    val localPageCount = ((sortedResults.size + 9) / 10).coerceAtLeast(1)
    LaunchedEffect(localPageCount) { if (localPage > localPageCount) localPage = localPageCount }
    val visibleResults = sortedResults.drop((localPage - 1) * 10).take(10)
    val canGoPrevious = localPage > 1 || page > 1
    val canGoNext = localPage < localPageCount || nextAvailable
    LaunchedEffect(page, keyword) { list.scrollToItem(0) }
    Column(Modifier.fillMaxSize().background(DisplaySettings.background(context)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (localPage > 1) localPage-- else onPrevious() }, enabled = canGoPrevious, modifier = Modifier.size(48.dp).semantics { contentDescription = "上一页" }) { Icon(Icons.Default.KeyboardArrowLeft, "上一页") }
            Text("第 $localPage / $localPageCount 页${if (totalPages > 0) " · 源站 $page / $totalPages" else ""}", style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { jumpPage = ""; jumpError = ""; jumpDialog = true }.semantics { contentDescription = "跳转页面；当前第 $page 页" })
            Box {
                IconButton(onClick = { sortMenu = true }, modifier = Modifier.semantics { contentDescription = "排序：$sortMode" }) { Icon(Icons.Default.Sort, "排序") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf("匹配度", "时间", "热度").forEach { option -> DropdownMenuItem(
                        text = { Text(if (sortMode == option) "✓ $option" else option) }, onClick = { sortMode = option; sortMenu = false }) }
                }
            }
            IconButton(onClick = { if (localPage < localPageCount) localPage++ else onNext() }, enabled = canGoNext, modifier = Modifier.size(48.dp).semantics { contentDescription = "下一页" }) { Icon(Icons.Default.ArrowForward, "下一页") }
        }
        if (state == "loading" || message == "搜索中") LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state in listOf("error", "timeout") || message == "搜索未完成") Text("搜索未完成", color = MaterialTheme.colorScheme.error)
        if (results.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(visibleResults, key = { magnetIdentity(it.magnet) }) { item -> MagnetResultCard(item, onParse, onDownload) }
            }
        } else if (state != "loading") {
            Text(if (state == "empty") "没有搜索结果" else "搜索未完成", Modifier.padding(16.dp))
            Button(onClick = onRetry) { Text("重试搜索") }
        } else TextButton(onClick = onCancel) { Text("取消搜索并返回") }
    }
    if (jumpDialog) {
        val requester = remember { androidx.compose.ui.focus.FocusRequester() }
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) { delay(100); requester.requestFocus(); keyboard?.show() }
        AlertDialog(onDismissRequest = { jumpDialog = false }, title = { Text("跳转搜索页") },
            text = { Column {
                OutlinedTextField(jumpPage, { jumpPage = it.filter(Char::isDigit).take(6) }, label = { Text("输入页码") }, singleLine = true,
                    modifier = Modifier.focusRequester(requester), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                if (jumpError.isNotBlank()) Text(jumpError, color = MaterialTheme.colorScheme.error)
                Text("结果页 1–$localPageCount；源站页：${knownPages.sorted().joinToString().ifBlank { "暂无" }}", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = {
                val target = jumpPage.toIntOrNull()
                if (target != null && target in 1..localPageCount) { localPage = target; jumpDialog = false }
                else if (target != null && target in knownPages) { onJump(target); jumpDialog = false }
                else jumpError = "请输入当前结果页范围内的页码，或已发现的源站页码"
            }) { Text("跳转") } },
            dismissButton = { TextButton(onClick = { jumpDialog = false }) { Text("取消") } })
    }
}

private fun parseHeat(value: String): Double = Regex("[0-9]+(?:\\.[0-9]+)?").find(value)?.value?.toDoubleOrNull() ?: -1.0
private fun parsePublishedDate(value: String): Long = runCatching {
    val m = Regex("(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})").find(value) ?: return@runCatching 0L
    val (y, mo, d) = m.destructured
    java.time.LocalDate.of(y.toInt(), mo.toInt(), d.toInt()).toEpochDay()
}.getOrDefault(0L)
@Composable
private fun MagnetResultCard(item: MagnetResult, onParse: (MagnetResult) -> Unit, onDownload: (MagnetResult) -> Unit) {
    val c=androidx.compose.ui.platform.LocalContext.current
    val textStyle=MaterialTheme.typography.bodyMedium.copy(fontSize=(14*DisplaySettings.scale(c)).sp,lineHeight=(16.8f*DisplaySettings.scale(c)).sp,color=Color(0xFF333333))
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text("${item.title.ifBlank { "待获取文件名" }} · ${item.format.ifBlank { detectResourceFormat(item.title, item.magnet).ifBlank { "格式待解析" } }}",style=textStyle)
            Text("${item.sizeText.ifBlank { if(item.bytes>0) formatLibraryBytes(item.bytes) else "大小 —" }} · ${item.format.ifBlank { detectResourceFormat(item.title, item.magnet).ifBlank { "格式 —" } }} · ${item.publishedAt.ifBlank { "时间 —" }}",style=textStyle)
            Row {
                TextButton(onClick={onParse(item)}) { Text("播放",style=textStyle) }
                TextButton(onClick={onDownload(item)}) { Text("下载 / 缓存",style=textStyle) }
                TextButton(onClick={LibraryStore.addFavorite(c,item.title,item.magnet)}) { Text("收藏",style=textStyle) }
            }
        }
    }
}

private fun magnetTitle(magnet: String): String = runCatching {
    val dn = Regex("(?:[?&])dn=([^&]+)", RegexOption.IGNORE_CASE).find(magnet)?.groupValues?.get(1)
    if (dn.isNullOrBlank()) "磁力资源" else java.net.URLDecoder.decode(dn, "UTF-8")
}.getOrDefault("磁力资源")

internal fun magnetIdentity(magnet: String): String {
    val normalized = normalizeMagnetUri(magnet).orEmpty()
    val hash = Regex("(?i)xt=urn:btih:([0-9a-f]{40})").find(normalized)?.groupValues?.get(1)
    return hash?.lowercase() ?: normalized.ifBlank { magnet.trim() }
}

internal fun isRelevantSearchTitle(keyword: String, title: String): Boolean {
    val query = keyword.lowercase().trim()
    val candidate = title.lowercase().trim()
    if (query.isBlank() || candidate.isBlank() || candidate == "待获取文件名" || candidate == "磁力资源") return false
    fun compact(value: String) = value.replace(Regex("[^\\p{IsHan}a-z0-9]+"), "")
    if (compact(candidate).contains(compact(query))) return true
    val queryTerms = Regex("[\\p{IsHan}]+|[a-z0-9]+", RegexOption.IGNORE_CASE).findAll(query).map { it.value }.filter { it.length > 1 }.toList()
    if (queryTerms.isEmpty()) return false
    val titleTerms = Regex("[\\p{IsHan}]+|[a-z0-9]+", RegexOption.IGNORE_CASE).findAll(candidate).map { it.value }.toList()
    val hits = queryTerms.count { term -> titleTerms.any { it.contains(term) || term.contains(it) && it.length > 2 } }
    return hits > 0 && hits * 2 >= queryTerms.size
}

internal fun detectResourceFormat(name: String, magnet: String): String {
    val dn = runCatching { Regex("(?:[?&])dn=([^&]+)", RegexOption.IGNORE_CASE).find(magnet)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }.orEmpty() }.getOrDefault("")
    val source = "$name $dn"
    val extensions = (MediaFormats.extensions + setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "pdf", "srt", "ass", "ssa", "vtt", "mp3", "flac", "wav", "aac", "m4a", "doc", "docx", "xls", "xlsx", "ppt", "pptx"))
        .sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    val extension = Regex("(?i)\\.($extensions)(?=$|[^A-Za-z0-9])").find(source)?.groupValues?.get(1)?.lowercase() ?: return ""
    return when {
        extension in MediaFormats.extensions -> ".$extension".uppercase()
        extension in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso") -> ".$extension".uppercase() + "（压缩包/镜像）"
        extension in setOf("srt", "ass", "ssa", "vtt") -> ".$extension".uppercase() + "（字幕）"
        extension in setOf("mp3", "flac", "wav", "aac", "m4a") -> ".$extension".uppercase() + "（音频）"
        extension == "pdf" || extension in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx") -> ".$extension".uppercase() + "（文档）"
        else -> ".$extension".uppercase() + "（可下载）"
    }
}

internal fun torrentResourceFormat(files: List<TaskFile>, magnet: String): String {
    val primary = files.filter { MediaFormats.isVideo(it.path) }.maxByOrNull { it.size }
        ?: files.maxByOrNull { it.size }
    return primary?.let { detectResourceFormat(it.path, magnet) }.orEmpty()
}

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
