from pathlib import Path
p = Path('app/src/main/java/com/masterwok/simpletorrentandroid/TorrentSession.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('                    AlertType.TRACKER_ERROR,', '                    AlertType.CACHE_FLUSHED -> torrentSession.get()?.let { it.cacheFlushGeneration++ }\n                    AlertType.TRACKER_ERROR,')
s = s.replace('    @Volatile var networkMessage: String = ""', '    @Volatile var networkMessage: String = ""\n    @Volatile var cacheFlushGeneration: Long = 0')
p.write_text(s, encoding='utf-8')
p = Path('app/src/main/java/com/freemangnetplayer/TorrentFileDataSource.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('import java.security.MessageDigest\n', '')
s = s.replace('private val handleProvider: () -> TorrentHandle?', 'private val handleProvider: () -> TorrentHandle?, private val flushGeneration: () -> Long')
s = s.replace('    private val verified = LinkedHashMap<Int, ByteArray>()', '    private val flushed = mutableSetOf<Int>()')
start = s.index('        var bytes = verified[piece]')
end = s.index('        position += read', start)
s = s[:start] + '''        var pendingFlush = -1L
        var flushPieces: List<Int> = emptyList()
        while (piece !in flushed) {
            val handle = handleProvider() ?: throw IOException("磁力任务已关闭")
            if (!handle.isValid || Thread.currentThread().isInterrupted) throw IOException("读取已取消")
            val info = handle.torrentFile() ?: throw IOException("元数据不可用")
            if (lastPrefetched != piece) {
                val window = (8 * 1024 * 1024 / pieceLength).toInt().coerceIn(4, 128)
                val end = minOf(piece + window, ((torrentEnd - 1) / pieceLength).toInt())
                for (next in piece..end) if (!handle.havePiece(next)) handle.setPieceDeadline(next, (next - piece) * 150)
                lastPrefetched = piece
            }
            if (pendingFlush >= 0 && flushGeneration() > pendingFlush) {
                flushed.addAll(flushPieces)
                continue
            }
            if (pendingFlush < 0 && handle.havePiece(piece)) {
                // Snapshot verified pieces before flushing. CACHE_FLUSHED confirms those
                // bytes are on disk, including files sharing a piece with skipped files.
                // Do not read skipped neighbors: libtorrent may store them in a partfile.
                val bits = handle.status(TorrentHandle.QUERY_PIECES).pieces()
                val first = (torrentOffset / pieceLength).toInt()
                val last = ((torrentEnd - 1) / pieceLength).toInt()
                flushPieces = (first..last).filter { bits.getBit(it) }
                pendingFlush = flushGeneration()
                handle.flushCache()
            }
            if (SystemClock.elapsedRealtime() >= deadline) throw IOException("视频分片供给不足，请查看资源节点和网络诊断")
            try { Thread.sleep(50) } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("读取已取消", interrupted)
            }
        }
        val disk = file ?: throw IOException("视频文件已关闭")
        disk.seek(position)
        val read = disk.read(buffer, offset, count)
        if (read < 0) throw IOException("已校验分片尚未写入文件")
''' + s[end:]
s = s.replace('verified.clear(); lastPrefetched = -1', 'flushed.clear(); lastPrefetched = -1')
p.write_text(s, encoding='utf-8')
p = Path('app/src/main/java/com/freemangnetplayer/PlayerActivity.kt')
s = p.read_text(encoding='utf-8').replace('TorrentFileDataSource { task.handle }', 'TorrentFileDataSource({ task.handle }, { task.engine?.cacheFlushGeneration ?: -1L })')
p.write_text(s, encoding='utf-8')
p = Path('scripts/verify_performance.py')
s = p.read_text(encoding='utf-8').replace("    require_visible('退出全屏', '悬浮播放', '切换视频')", "    from capture_usage_screenshots import visible_text\n    if 'Got it' in visible_text():\n        find_and_tap('Got it')\n    require_visible('退出全屏', '悬浮播放', '切换视频')")
p.write_text(s, encoding='utf-8')
p = Path('scripts/build_performance_docs.py')
s = p.read_text(encoding='utf-8').replace('播放器读取前再校验整块磁盘数据的 SHA-1，避免原生写缓存尚未落盘时误读稀疏空洞。', '播放器先检查原生分片校验，再等待 CACHE_FLUSHED 确认已写入磁盘，避免误读尚未落盘的空洞；兼容与未选文件共享分片的 partfile。')
p.write_text(s, encoding='utf-8')
print('Fixed verified cached reads at selected/skipped file boundaries and system fullscreen tutorial handling')
