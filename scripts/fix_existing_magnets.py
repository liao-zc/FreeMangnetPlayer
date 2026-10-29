from pathlib import Path
p = Path('app/src/main/java/com/freemangnetplayer/TorrentTasks.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('class TorrentTask(val id: String, val source: String,', 'class TorrentTask(val id: String, var source: String,')
old = '''        task.lastUsed = System.currentTimeMillis()
        if (task.cancelled'''
new = '''        // Reopening the same hash must not discard newly pasted trackers/peers.
        if (source.startsWith("magnet:?", true) && task.source.startsWith("magnet:?", true)) {
            val incoming = source.substringAfter('?').split('&').filter {
                it.startsWith("tr=", true) || it.startsWith("x.pe=", true) || it.startsWith("ws=", true)
            }
            val original = task.source.substringAfter('?').split('&').toSet()
            val additional = incoming.filter { it !in original }.distinct()
            if (additional.isNotEmpty()) {
                task.source += additional.joinToString("") { "&$it" }
                task.handle?.let { handle ->
                    additional.filter { it.startsWith("tr=", true) }.forEach {
                        runCatching { handle.addTracker(AnnounceEntry(java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8"))) }
                    }
                    connectExplicitPeers(task, handle)
                    handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL); handle.forceDHTAnnounce()
                }
            }
        }
        task.lastUsed = System.currentTimeMillis()
        if (task.cancelled'''
assert old in s
s = s.replace(old, new)
start = s.index('                            // jlibtorrent 1.2 does not import')
end = s.index('                            setMetadataPriorities', start)
block = s[start:end]
s = s[:start] + '                            connectExplicitPeers(task, handle)\n' + s[end:]
insert = s.index('    private fun setMetadataPriorities')
s = s[:insert] + '    private fun connectExplicitPeers(task: TorrentTask, handle: TorrentHandle) {\n' + block + '    }\n' + s[insert:]
p.write_text(s, encoding='utf-8')
p = Path('scripts/verify_performance.py')
s = p.read_text(encoding='utf-8')
needle = "    report.append({'check': 'return-during-plain-magnet-discovery', 'passed': True})"
assert needle in s
s = s.replace(needle, needle + '''
    paste(fixture['direct_magnet'])
    find_and_tap('解析资源')
    enriched = wait(lambda s: s.get('id') == fixture['id'] and s.get('metadata'))
    assert '&x.pe=' in enriched['source'], enriched
    screenshot('perf-10b-reopen-with-new-peer.png')
    report.append({'check': 'existing-hash-merges-new-network-parameters', 'passed': True, 'state': enriched})
    find_and_tap('返回搜索')
''')
p.write_text(s, encoding='utf-8')
p = Path('scripts/build_performance_docs.py')
s = p.read_text(encoding='utf-8')
s = s.replace("'return-during-plain-magnet-discovery']", "'return-during-plain-magnet-discovery', 'existing-hash-merges-new-network-parameters']")
s = s.replace("    'return-during-plain-magnet-discovery': '节点发现过程中返回首页',", "    'return-during-plain-magnet-discovery': '节点发现过程中返回首页',\n    'existing-hash-merges-new-network-parameters': '同一哈希重新粘贴时合并新增节点/Tracker 并解析',")
s = s.replace('6. 原会话桥', '6. 同一哈希旧任务原先会丢弃新粘贴链接中的 Tracker 和节点地址；现在合并这些参数并应用到活动会话，避免再次粘贴仍沿用缺失节点的旧链接。\n7. 原会话桥')
p.write_text(s, encoding='utf-8')
print('Preserved newly pasted network parameters on existing tasks')
