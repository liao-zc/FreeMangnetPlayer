package com.freemangnetplayer

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.frostwire.jlibtorrent.TorrentHandle
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Read only verified torrent pieces, including after seeking into an uncached region. */
internal class TorrentFileDataSource(private val handleProvider: () -> TorrentHandle?) : BaseDataSource(false) {
    private var file: RandomAccessFile? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var torrentOffset = 0L
    private var torrentEnd = 0L
    private var opened = false
    private var pieceLength = 0L
    private var lastPrefetched = -1
    private val verified = LinkedHashMap<Int, ByteArray>()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val handle = handleProvider() ?: throw IOException("磁力任务已关闭")
        val info = handle.torrentFile() ?: throw IOException("尚未取得文件元数据")
        val files = info.files()
        val local = File(dataSpec.uri.path ?: throw IOException("文件地址无效"))
        val index = (0 until files.numFiles()).firstOrNull {
            File(handle.savePath(), files.filePath(it)).canonicalPath == local.canonicalPath
        } ?: throw IOException("文件不在当前种子列表中")
        val size = files.fileSize(index)
        if (dataSpec.position > size) throw IOException("播放位置超出文件范围")
        pieceLength = info.pieceLength().toLong()
        torrentOffset = files.fileOffset(index)
        torrentEnd = torrentOffset + size
        position = dataSpec.position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) size - position else minOf(dataSpec.length, size - position)
        uri = dataSpec.uri
        file = RandomAccessFile(local, "r").apply { seek(position) }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val absolute = torrentOffset + position
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
        position += read
        remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { file?.close() } finally {
            file = null
            uri = null
            verified.clear(); lastPrefetched = -1
            if (opened) { opened = false; transferEnded() }
        }
    }
}
