package com.omniplayer.magnet
import com.omniplayer.core.*
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

internal data class SearchProvider(
    val name: String,
    val homepage: String,
    val queryUrl: String? = null
)


internal data class MagnetResult(val title: String, val magnet: String, val bytes: Long = 0, val sizeText: String = "", val type: String = "视频", val heat: String = "", val publishedAt: String = "", val format: String = "")

internal val builtInProviders = listOf(
    SearchProvider("clg.im", "https://clg.im/"),
    SearchProvider("BT搜索（dobt）", "https://mg.dobt.cc/"),
    SearchProvider("BD电影首发站", "https://www.bdflixs.com/", "https://www.bdflixs.com/?s=%s"),
    SearchProvider("Pomo", "https://pomo.mom/", "https://pomo.mom/?keyword=%s"),
    SearchProvider("美剧粉", "https://www.mjf2020.com/", "https://www.mjf2020.com/ss/?s=%s"),
    SearchProvider("高清族", "https://hdzu.org/", "https://hdzu.org/movie/filter?act=title&wd=%s")
)

private val teal = Color(0xFF087F79)

@Composable
internal fun SearchHome(
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
internal fun SearchResultsScreen(
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
            else -> results.sortedByDescending { item -> keyword.lowercase().split(' ').count { word -> word.isNotBlank() && item.title.lowercase().contains(word) } }
        }
    }
    val localPageCount = ((sortedResults.size + 9) / 10).coerceAtLeast(1)
    LaunchedEffect(localPageCount) { if (localPage > localPageCount) localPage = localPageCount }
    val visibleResults = sortedResults.drop((localPage - 1) * 10).take(10)
    val canGoPrevious = localPage > 1 || page > 1
    val canGoNext = localPage < localPageCount || nextAvailable
    LaunchedEffect(page, keyword, localPage) { list.scrollToItem(0) }
    Column(Modifier.fillMaxSize().background(DisplaySettings.background(context)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (localPage > 1) localPage-- else onPrevious() }, enabled = canGoPrevious, modifier = Modifier.size(48.dp).semantics { contentDescription = "上一页" }) { Icon(Icons.Default.KeyboardArrowLeft, "上一页") }
            Text("第 $localPage / $localPageCount 页${if (totalPages > 0) " · 源站第 $page / $totalPages 页" else ""}", style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { jumpPage = ""; jumpError = ""; jumpDialog = true }.semantics { contentDescription = "跳转结果页；当前第 $localPage 页，共 $localPageCount 页" })
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
                Text("结果页范围：1–$localPageCount", style = MaterialTheme.typography.bodySmall)
                Text("源站页：", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    knownPages.sorted().forEach { sourcePage -> TextButton(onClick = { onJump(sourcePage); jumpDialog = false }, modifier = Modifier.semantics { contentDescription = "源站第 $sourcePage 页" }) { Text("$sourcePage") } }
                }
            } },
            confirmButton = { TextButton(onClick = {
                val target = jumpPage.toIntOrNull()
                if (target != null && target in 1..localPageCount) { localPage = target; jumpDialog = false }
                else jumpError = "请输入当前结果页范围内的页码，或点击下方源站页码"
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

internal fun magnetTitle(magnet: String): String = runCatching {
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
