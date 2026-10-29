from pathlib import Path
p = Path('app/src/main/java/com/freemangnetplayer/TorrentFileDataSource.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('private val flushGeneration: () -> Long)', 'private val flushGeneration: () -> Long, private val nativeLock: Any)')
a = s.index('        val handle = handleProvider()')
b = s.index('        file = RandomAccessFile', a)
block = s[a:b]
# Keep all JNI calls within the same lifetime lock as native session destruction.
s = s[:a] + '        val local = File(dataSpec.uri.path ?: throw IOException("文件地址无效"))\n        synchronized(nativeLock) {\n' + block.replace('        val local = File(dataSpec.uri.path ?: throw IOException("文件地址无效"))\n', '') + '''        }
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (!local.exists()) {
            if (handleProvider() == null || Thread.currentThread().isInterrupted) throw IOException("读取已取消")
            if (SystemClock.elapsedRealtime() >= deadline) throw IOException("尚无可播放分片，资源节点未提供数据")
            try { Thread.sleep(50) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("读取已取消", e) }
        }
''' + s[b:]
a = s.index('            val handle = handleProvider()', s.index('    override fun read'))
b = s.index('            if (SystemClock.elapsedRealtime() >= deadline)', a)
block = s[a:b].replace('                continue\n', '')
block = block.replace('            if (pendingFlush < 0 && handle.havePiece(piece))', '            if (piece !in flushed && pendingFlush < 0 && handle.havePiece(piece))')
s = s[:a] + '            synchronized(nativeLock) {\n' + block + '            }\n' + s[b:]
p.write_text(s, encoding='utf-8')
