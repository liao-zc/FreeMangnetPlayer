from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

main = read("app/src/main/java/com/omniplayer/MainActivity.kt")
search = read("app/src/main/java/com/omniplayer/magnet/MagnetSearchUi.kt")
history = read("app/src/main/java/com/omniplayer/magnet/HistoryStore.kt")
player = read("app/src/main/java/com/omniplayer/magnet/PlayerActivity.kt")
live = read("app/src/main/java/com/omniplayer/live/IptvActivity.kt")
layouts = [read(f"app/src/main/res/layout/{name}") for name in ("player_controls.xml", "player_overlay_controls.xml")]

checks = {
    "bottom navigation omits duplicate search entry": 'label = { Text("搜索") })' not in main and 'selectedTab == "搜索" || selectedTab == "搜索结果"' not in main,
    "download and cache share one task page": '"任务" -> Column' in main and 'FilterChip(selected = taskTab ==' in main and '缓存 (${caches.size})' in main,
    "search history icon and recent query picker": 'Icons.Default.History' in search and 'recentSearches.take(50)' in search,
    "search action is icon only": 'IconButton(onClick = onSearch' in search and 'Text("搜索", style = MaterialTheme.typography.titleMedium)' not in search,
    "search results use source pages without app-side ten-item paging": 'localPage' not in search and 'drop((localPage - 1) * 10).take(10)' not in search,
    "search results deduplicate and show magnet links": 'distinctBy { magnetIdentity(it.magnet) }' in search and 'Text(item.magnet' in search,
    "search size sorting exists": '"大小" -> results.sortedWith(compareByDescending<MagnetResult> { it.bytes }' in search and '"大小"' in search,
    "search playback and task histories are independently managed": 'val sections = listOf("搜索", "播放", "任务")' in history and 'HistoryStore.clear(c,"下载")' in history and 'Text("复制")' in history and 'HistoryStore.remove(c,recordKind,entry.source)' in history,
    "previous cached/downloaded file control is connected": 'playPreviousStored()' in player and 'R.id.player_previous).setOnClickListener' in player and all('android:id="@+id/player_previous"' in x for x in layouts),
    "cache range has no separate textual duration line": 'Text(CacheCoverage.describe(cacheRanges' not in player and all('CacheCoverageView' in x and 'player_cache_ranges' not in x for x in layouts),
    "seekbar remains active for duration and dragging": 'it.setDuration(duration); it.setPosition(current.currentPosition)' in player and 'this@PlayerActivity.player?.seekTo(position)' in player,
    "fullscreen transition refreshes time and progress controls": 'window.decorView.post { refreshProgress() }' in player and 'player_duration)?.text = formatTime(duration)' in player,
    "live channel chooser uses full loaded playlist from favorites": 'IptvSession.channels = channels' in live and 'Text("电视直播", fontWeight = FontWeight.Bold)' in live,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + "\t" + name)
out = ROOT / "manual-shots" / "recent-requirements-verification.json"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps([{"check": name, "passed": ok} for name, ok in checks.items()], ensure_ascii=False, indent=2), encoding="utf-8")
if failed:
    raise SystemExit("Failed checks: " + "; ".join(failed))
