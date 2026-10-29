from pathlib import Path

p = Path('app/src/main/java/com/freemangnetplayer/TorrentFileDataSource.kt')
s = p.read_text(encoding='utf-8-sig')
s = s.replace('import java.io.RandomAccessFile', 'import java.io.RandomAccessFile\nimport java.security.MessageDigest')
s = s.replace('    private var opened = false', '''    private var opened = false
    private var pieceLength = 0L
    private var lastPrefetched = -1
    private val verified = LinkedHashMap<Int, ByteArray>()''')
s = s.replace('        torrentOffset = files.fileOffset(index)', '        pieceLength = info.pieceLength().toLong()\n        torrentOffset = files.fileOffset(index)')
start = s.index('        val count = minOf(length.toLong(), remaining).toInt()')
end = s.index('        position += read', start)
s = s[:start] + '''        val absolute = torrentOffset + position
        val piece = (absolute / pieceLength).toInt()
        val pieceOffset = (absolute % pieceLength).toInt()
        val count = minOf(length.toLong(), remaining, pieceLength - pieceOffset).toInt()
        val deadline = SystemClock.elapsedRealtime() + 60_000
        var bytes = verified[piece]
        var requestedFlush = false
        while (bytes == null) {
            val handle = handleProvider() ?: throw IOException("磁力任务已关闭")
            if (!handle.isValid || Thread.currentThread().isInterrupted) throw IOException("读取已取消")
            val info = handle.torrentFile() ?: throw IOException("元数据不可用")
            if (lastPrefetched != piece) {
                // A bounded 8 MiB forward window, requested once per piece, not every 100 ms.
                val window = (8 * 1024 * 1024 / pieceLength).toInt().coerceIn(4, 128)
                val end = minOf(piece + window, ((torrentEnd - 1) / pieceLength).toInt())
                for (next in piece..end) if (!handle.havePiece(next)) {
                    handle.setPieceDeadline(next, (next - piece) * 150)
                }
                lastPrefetched = piece
            }
            if (handle.havePiece(piece)) {
                // havePiece can include verified data still in libtorrent's write cache.
                // Verify disk bytes as well, so a sparse zero-filled range is never played.
                val files = info.files()
                val begin = piece * pieceLength
                val finish = minOf(begin + pieceLength, info.totalSize())
                val candidate = ByteArray((finish - begin).toInt())
                var readable = true
                for (index in 0 until files.numFiles()) {
                    val fileStart = files.fileOffset(index)
                    val first = maxOf(begin, fileStart)
                    val last = minOf(finish, fileStart + files.fileSize(index))
                    if (first >= last) continue
                    try {
                        RandomAccessFile(File(handle.savePath(), files.filePath(index)), "r").use { disk ->
                            disk.seek(first - fileStart)
                            disk.readFully(candidate, (first - begin).toInt(), (last - first).toInt())
                        }
                    } catch (error: IOException) { readable = false; break }
                }
                val digest = if (readable) MessageDigest.getInstance("SHA-1").digest(candidate)
                    .joinToString("") { "%02x".format(it) } else ""
                if (readable && digest == info.hashForPiece(piece).toString()) {
                    bytes = candidate
                    verified[piece] = candidate
                    while (verified.size > 3) verified.remove(verified.keys.first())
                    break
                }
                if (!requestedFlush) { handle.flushCache(); requestedFlush = true }
            }
            if (SystemClock.elapsedRealtime() >= deadline) throw IOException("视频分片供给不足，请查看资源节点和网络诊断")
            try { Thread.sleep(100) } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("读取已取消", interrupted)
            }
        }
        val read = minOf(count, bytes!!.size - pieceOffset)
        System.arraycopy(bytes, pieceOffset, buffer, offset, read)
''' + s[end:]
s = s.replace('            uri = null', '            uri = null\n            verified.clear(); lastPrefetched = -1')
p.write_text(s, encoding='utf-8')
p = Path('app/src/main/java/com/freemangnetplayer/TorrentTasks.kt')
s = p.read_text(encoding='utf-8').replace('options.settingsPack.cacheSize(1024)', 'options.settingsPack.cacheSize(1024)\n                    options.settingsPack.setInteger(settings_pack.int_types.cache_expiry.swigValue(), 1)')
p.write_text(s, encoding='utf-8')
print('Applied bounded prefetch and verified disk-cache reads')
