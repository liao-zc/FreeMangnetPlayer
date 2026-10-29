from pathlib import Path
p=Path('app/src/main/java/com/freemangnetplayer/MainActivity.kt');s=p.read_text(encoding='utf-8')
s=s.replace('private data class MagnetResult', 'private data class SearchPage(val label: String, val url: String)\n\nprivate data class MagnetResult',1)
s=s.replace('val results = remember { mutableStateListOf<MagnetResult>() }','''val results = remember { mutableStateListOf<MagnetResult>() }
                var pageNumber by remember { mutableIntStateOf(1) }
                var nextPage by remember { mutableStateOf("") }
                val pageUrls = remember { mutableStateMapOf<Int, String>() }
                val pageCache = remember { mutableStateMapOf<Int, List<MagnetResult>>() }
                val pageNext = remember { mutableStateMapOf<Int, String>() }''',1)
s=s.replace('                    results.clear()\n                    selectedTab', '                    pageNumber = 1; nextPage = ""; pageUrls.clear(); pageCache.clear(); pageNext.clear()\n                    results.clear()\n                    selectedTab',1)
marker='                LaunchedEffect(Unit) { if (!requestedQuery.isNullOrBlank()) beginSearch() }'
insert='''                fun changePage(number: Int, url: String) {
                    if (url.isBlank() || number < 1) return
                    pageCache[pageNumber] = results.toList(); pageNext[pageNumber] = nextPage
                    pageNumber = number; nextPage = pageNext[number].orEmpty()
                    results.clear(); results.addAll(pageCache[number].orEmpty())
                    pageUrls[number] = url
                    currentSearchUrl = url; searchPageUrl = url
                    firstPageLoad = false; searchNeedsFormSubmit = false
                    searchState = if (results.isEmpty()) "loading" else "results"
                    searchMessage = "正在读取第 $number 页…"
                    searchTrigger++
                }

'''
assert marker in s;s=s.replace(marker,insert+marker)
s=s.replace('LaunchedEffect(searchState, results.size)', 'LaunchedEffect(searchState, results.size, pageNumber, nextPage)')
s=s.replace('.put("query", activeQuery).put("state", searchState)', '.put("page", pageNumber).put("next", nextPage).put("query", activeQuery).put("state", searchState)',1)
s=s.replace('                                keyword = activeQuery,','''                                page = pageNumber,
                                nextAvailable = nextPage.isNotBlank(),
                                onPrevious = { changePage(pageNumber - 1, pageUrls[pageNumber - 1].orEmpty()) },
                                onNext = { changePage(pageNumber + 1, nextPage) },
                                keyword = activeQuery,''',1)
start=s.index('                                    searchBridge = MagnetSearchBridge {');end=s.index('                                    settings.javaScriptEnabled',start)
old=s[start:end];new=old.replace('searchBridge = MagnetSearchBridge { title, link ->','searchBridge = MagnetSearchBridge(onMagnet = { title, link ->').rstrip()
# Close the first lambda, then add navigation metadata callback tied to renderer generation.
new=new[:-1]+'''}, onPage = { url, next, pending, failures -> runOnUiThread {
                                        if (generation == searchTrigger) {
                                            if (url.startsWith("http")) { pageUrls[pageNumber] = url; currentSearchUrl = url }
                                            nextPage = next
                                            searchMessage = if (pending > 0) "正在读取 $pending 个详情页面…" else if (failures > 0) "$failures 个详情页面读取失败，可重试本页或查看网页" else "本页已读取完成"
                                            if (pending == 0 && results.isEmpty() && searchState != "error") searchState = "empty"
                                        }
                                    } })
'''
s=s[:start]+new+s[end:]
s=s.replace('private class MagnetSearchBridge(private val onMagnet: (String, String) -> Unit)', '''private class MagnetSearchBridge(
    private val onMagnet: (String, String) -> Unit,
    private val onPage: (String, String, Int, Int) -> Unit = { _, _, _, _ -> }
)''')
s=s.replace('    @JavascriptInterface\n    fun onResults', '''    @JavascriptInterface
    fun onPageInfo(json: String) {
        runCatching { val value = JSONObject(json); onPage(value.optString("url"), value.optString("next"), value.optInt("pending"), value.optInt("failures")) }
    }
    @JavascriptInterface
    fun onResults''',1)
s=s.replace('private fun SearchResultsScreen(\n', '''private fun SearchResultsScreen(
    page: Int,
    nextAvailable: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
''',1)
s=s.replace('        Spacer(Modifier.height(12.dp))\n        when {\n            results.isNotEmpty()', '''        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPrevious, enabled = page > 1, modifier = Modifier.weight(1f).height(52.dp)) { Text("上一页") }
            Text("第 $page 页")
            Button(onClick = onNext, enabled = nextAvailable, modifier = Modifier.weight(1f).height(52.dp)) { Text("下一页") }
        }
        Text(message, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        if (!nextAvailable && state != "loading") Text("当前页面未提供下一页链接；可查看原网页确认是否有更多结果。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) { Text("查看本页原网页 / 验证") }
        when {
            results.isNotEmpty()''',1)
s=s.replace('Text("找到 ${results.size} 条可识别磁力链接"', 'Text("本页 ${results.size} 条磁力链接（不限制结果数量）"',1)
s=s.replace('LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp))', 'LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp))',1)
p.write_text(s,encoding='utf-8')
