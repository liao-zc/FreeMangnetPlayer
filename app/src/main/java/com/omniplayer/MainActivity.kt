package com.omniplayer
import com.omniplayer.core.*
import com.omniplayer.live.*
import com.omniplayer.magnet.*
import com.omniplayer.settings.*

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
import androidx.compose.foundation.horizontalScroll
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

internal val ink = Color(0xFF14202B)
internal val teal = Color(0xFF087F79)
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
                                            if(index>=0) { val old=results[index];val foundBytes=row.optLong("bytes");val foundSize=row.optString("sizeText");results[index]=old.copy(title=old.title.takeIf { it.isNotBlank() && it != "待获取文件名" } ?: row.optString("title").takeIf { it.isNotBlank() && !it.startsWith("magnet:",true) }.orEmpty(),bytes=foundBytes.takeIf { it>0L } ?: old.bytes,sizeText=foundSize.takeIf { it.isNotBlank() } ?: old.sizeText,type=row.optString("type","视频"),heat=row.optString("heat").ifBlank { old.heat },publishedAt=row.optString("publishedAt").ifBlank { old.publishedAt },format=row.optString("format").ifBlank { old.format }) }
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
