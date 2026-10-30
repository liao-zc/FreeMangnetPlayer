package com.omniplayer

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.Priority
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Read only verified torrent pieces, including after seeking into an uncached region. */
internal class TorrentFileDataSource(private val handleProvider: () -> TorrentHandle?, private val flushGeneration: () -> Long, private val nativeLock: Any) : BaseDataSource(false) {
    private var file: RandomAccessFile? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var torrentOffset = 0L
    private var torrentEnd = 0L
    private var opened = false
    private var pieceLength = 0L
    private var lastPrefetched = -1
    private val flushed = mutableSetOf<Int>()
    private var pieceSnapshot: org.libtorrent4j.TorrentStatus? = null
    @Volatile private var debugFileOffset = -1L
    @Volatile private var debugPiece = -1
    @Volatile private var debugPieceAvailable = false
    @Volatile private var debugDeadlinePiece = -1
    @Volatile private var debugFlushGeneration = -1L

    fun debugInfo() = JSONObject().put("fileOffset", debugFileOffset).put("pieceLength", pieceLength)
        .put("readPiece", debugPiece).put("readPieceAvailable", debugPieceAvailable)
        .put("deadlinePiece", debugDeadlinePiece).put("flushGeneration", debugFlushGeneration)

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val local = File(dataSpec.uri.path ?: throw IOException("文件地址无效"))
        synchronized(nativeLock) {
        val handle = handleProvider() ?: throw IOException("磁力任务已关闭")
        val info = handle.torrentFile() ?: throw IOException("尚未取得文件元数据")
        val files = info.files()
        val index = (0 until files.numFiles()).firstOrNull {
            File(handle.savePath(), files.filePath(it)).canonicalPath == local.canonicalPath
        } ?: throw IOException("文件不在当前种子列表中")
        val size = files.fileSize(index)
        if (dataSpec.position > size) throw IOException("播放位置超出文件范围")
        pieceLength = info.pieceLength().toLong()
        torrentOffset = files.fileOffset(index)
        debugFileOffset = torrentOffset
        torrentEnd = torrentOffset + size
        position = dataSpec.position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) size - position else minOf(dataSpec.length, size - position)
        uri = dataSpec.uri
        }
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (!local.exists()) {
            if (handleProvider() == null || Thread.currentThread().isInterrupted) throw IOException("读取已取消")
            if (SystemClock.elapsedRealtime() >= deadline) throw IOException("尚无可播放分片，资源节点未提供数据")
            try { Thread.sleep(50) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("读取已取消", e) }
        }
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
        debugPiece = piece
        val count = minOf(length.toLong(), remaining, pieceLength - pieceOffset).toInt()
        val deadline = SystemClock.elapsedRealtime() + 60_000
        var pendingFlush = -1L
        var flushPieces: List<Int> = emptyList()
        while (piece !in flushed) {
            synchronized(nativeLock) {
            val handle = handleProvider() ?: throw IOException("磁力任务已关闭")
            if (!handle.isValid || Thread.currentThread().isInterrupted) throw IOException("读取已取消")
            val info = handle.torrentFile() ?: throw IOException("元数据不可用")
            if (lastPrefetched != piece) {
                if (lastPrefetched >= 0 && !handle.havePiece(lastPrefetched)) {
                    handle.piecePriority(lastPrefetched, Priority.DEFAULT)
                }
                handle.piecePriority(piece, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(piece, 0)
                debugDeadlinePiece = piece
                // Request only the current piece urgently. Large simultaneous
                // windows can leave every piece partial, so the player keeps
                // waiting for a verified header despite plenty of total bytes.
                val end = minOf(piece + 2, ((torrentEnd - 1) / pieceLength).toInt())
                for (next in piece + 1..end) if (!handle.havePiece(next)) {
                    handle.setPieceDeadline(next, 5_000 * (next - piece))
                }
                lastPrefetched = piece
            }
            debugPieceAvailable = handle.havePiece(piece)
            debugFlushGeneration = flushGeneration()
            if (pendingFlush >= 0 && flushGeneration() > pendingFlush) {
                flushed.addAll(flushPieces)
                pieceSnapshot = null
            }
            if (piece !in flushed && pendingFlush < 0 && handle.havePiece(piece)) {
                // Snapshot verified pieces before flushing. CACHE_FLUSHED confirms those
                // bytes are on disk, including files sharing a piece with skipped files.
                // Do not read skipped neighbors: libtorrent may store them in a partfile.
                pieceSnapshot = handle.status(TorrentHandle.QUERY_PIECES)
                val bits = pieceSnapshot!!.pieces()
                val first = (torrentOffset / pieceLength).toInt()
                val last = ((torrentEnd - 1) / pieceLength).toInt()
                flushPieces = (first..last).filter { it < bits.size() && bits.getBit(it) }
                if (piece !in flushPieces) flushPieces = flushPieces + piece
                pendingFlush = flushGeneration()
                handle.flushCache()
            }
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                val reason = if (!debugPieceAvailable) "piece 尚未校验" else "piece 已校验但缓存未确认落盘"
                throw IOException("$reason；piece=$piece，文件偏移=$absolute，flushPending=${pendingFlush >= 0}")
            }
            try { Thread.sleep(50) } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("读取已取消", interrupted)
            }
        }
        val disk = file ?: throw IOException("视频文件已关闭")
        disk.seek(position)
        val read = disk.read(buffer, offset, count)
        if (read < 0) throw IOException("已校验分片尚未写入文件")
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
            flushed.clear(); pieceSnapshot = null; lastPrefetched = -1
            if (opened) { opened = false; transferEnded() }
        }
    }
}
