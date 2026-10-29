from pathlib import Path
p=Path('app/src/main/java/com/freemangnetplayer/TorrentTasks.kt');s=p.read_text(encoding='utf-8')
s=s.replace('        val connection = java.net.URL("https://itorrents.net/torrent/${hash.uppercase()}.torrent").openConnection()', '''        val exactSource = Regex("(?i)[?&]xs=([^&]+)").find(task.source)?.groupValues?.get(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        val connection = java.net.URL(exactSource ?: "https://itorrents.net/torrent/${hash.uppercase()}.torrent").openConnection()''')
s=s.replace('        // Reopening the same hash', '''        Regex("(?i)[?&]so=(\\\\d+)(?:&|$)").find(source)?.groupValues?.get(1)?.toIntOrNull()?.let {
            task.selected = setOf(it); task.playingIndex = it
        }
        // Reopening the same hash''')
s=s.replace('val selected = task.selected.ifEmpty', 'val selected = task.selected.filter { it in rows.indices }.toSet().ifEmpty')
s=s.replace('if (task.selected.isEmpty()) {', 'if (task.selected.none { index -> index in task.files.indices }) {')
s=s.replace('                    // Prefer TCP initially: the emulator\'s throttled uTP path can stall.\n                    // Retain incoming uTP and automatically enable outgoing uTP if TCP\n                    // fails to produce a useful connection; this is not a bandwidth cap.\n', '')
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/PlayerActivity.kt');s=p.read_text(encoding='utf-8')
s=s.replace('                                        if (file.video) TextButton(onClick', '                                        TextButton(onClick = { copyFileMagnet(current, file) }) { Text("复制磁力") }\n                                        if (file.video) TextButton(onClick')
s=s.replace('                                        if (file.video) TextButton(onClick = { switchVideo(current, file.index); fileDialog = false })', '                                        TextButton(onClick = { copyFileMagnet(current, file) }) { Text("复制磁力：${file.path.substringAfterLast(\'/\')}") }\n                                        if (file.video) TextButton(onClick = { switchVideo(current, file.index); fileDialog = false })')
s=s.replace('    private fun cycleSpeed() {', '''    private fun copyFileMagnet(current: TorrentTask, file: TaskFile) {
        val extras = current.source.substringAfter('?', "").split('&').filter {
            it.startsWith("tr=", true) || it.startsWith("x.pe=", true) || it.startsWith("ws=", true) || it.startsWith("xs=", true)
        }.distinct().joinToString("") { "&$it" }
        val link = "magnet:?xt=urn:btih:${current.id}&dn=${java.net.URLEncoder.encode(file.path.substringAfterLast('/'), "UTF-8")}&so=${file.index}$extras"
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(file.path.substringAfterLast('/'), link))
        android.widget.Toast.makeText(this, "已复制磁力链接（包含文件选择，需接收方支持）", android.widget.Toast.LENGTH_SHORT).show()
    }
    private fun cycleSpeed() {''')
s=s.replace('.put("networkMessage", current.networkMessage)', '.put("metadataReplacements", current.engine?.metadataReplacements ?: 0).put("restoredTrackers", current.engine?.restoredTrackers ?: 0).put("networkMessage", current.networkMessage)')
p.write_text(s,encoding='utf-8')
p=Path('app/build.gradle.kts');s=p.read_text(encoding='utf-8').replace('versionCode = 13','versionCode = 14').replace('versionName = "0.1.12"','versionName = "0.1.13"');p.write_text(s,encoding='utf-8')
