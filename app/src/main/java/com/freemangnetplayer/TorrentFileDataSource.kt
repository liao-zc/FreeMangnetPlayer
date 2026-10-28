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

/** Read only verified torrent pieces, including after seeking into an uncached region. */
internal class TorrentFileDataSource(private val handleProvider: () -> TorrentHandle?) : BaseDataSource(false) {
    private var file: RandomAccessFile? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var torrentOffset = 0L
    private var opened = false

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
        torrentOffset = files.fileOffset(index)
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
        val count = minOf(length.toLong(), remaining).toInt()
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (true) {
            val handle = handleProvider() ?: throw IOException("磁力任务已关闭")
            if (!handle.isValid || Thread.currentThread().isInterrupted) throw IOException("读取已取消")
            val info = handle.torrentFile() ?: throw IOException("元数据不可用")
            val pieceLength = info.pieceLength().toLong()
            val first = ((torrentOffset + position) / pieceLength).toInt()
            val last = ((torrentOffset + position + count - 1) / pieceLength).toInt()
            val missing = (first..last).filter { !handle.havePiece(it) }
            if (missing.isEmpty()) break
            for (piece in missing) handle.setPieceDeadline(piece, 0)
            for (piece in (last + 1)..minOf(last + 12, info.numPieces() - 1)) {
                if (!handle.havePiece(piece)) handle.setPieceDeadline(piece, 1000)
            }
            if (SystemClock.elapsedRealtime() >= deadline) throw IOException("所需视频分片尚未缓存，等待连接超时")
            try { Thread.sleep(100) } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("读取已取消", interrupted)
            }
        }
        val read = file?.read(buffer, offset, count) ?: throw IOException("视频文件已关闭")
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
            if (opened) { opened = false; transferEnded() }
        }
    }
}
