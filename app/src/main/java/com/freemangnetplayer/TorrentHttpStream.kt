package com.freemangnetplayer

import android.net.Uri
import androidx.media3.datasource.DataSpec
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Loopback byte ranges read only through the existing verified-piece data source. */
internal class TorrentHttpStream(private val task: TorrentTask, private val target: TaskFile) : AutoCloseable {
    private val token = UUID.randomUUID().toString()
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newFixedThreadPool(4)
    private val clients = ConcurrentHashMap<Socket, TorrentFileDataSource>()
    @Volatile private var closed = false
    val uri: Uri = Uri.parse("http://127.0.0.1:${server.localPort}/$token/video.rmvb")
    init {
        Thread({
            while (!closed) runCatching {
                val socket = server.accept()
                workers.execute { serve(socket) }
            }
        }, "torrent-loopback").apply { isDaemon = true; start() }
    }
    private fun serve(socket: Socket) {
        var source: TorrentFileDataSource? = null
        try {
            socket.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val request = reader.readLine()?.split(' ') ?: return
            if (request.size < 2 || request[1].substringBefore('?') != "/$token/video.rmvb") return
            var range: String? = null
            var headerCount = 0
            while (headerCount++ < 64) {
                val line = reader.readLine() ?: return
                if (line.startsWith("Range:", true)) range = line.substringAfter(':').trim()
                if (line.isEmpty()) break
            }
            val size = target.size
            val values = range?.removePrefix("bytes=")?.substringBefore(',')?.split('-', limit = 2)
            val start = if (values?.getOrNull(0)?.isEmpty() == true) (size - (values.getOrNull(1)?.toLongOrNull() ?: size)).coerceAtLeast(0)
                else values?.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = if (values?.getOrNull(0)?.isEmpty() == true) size - 1
                else (values?.getOrNull(1)?.toLongOrNull() ?: size - 1).coerceAtMost(size - 1)
            val output = socket.getOutputStream()
            if (start !in 0 until size || end < start) {
                output.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nConnection: close\r\n\r\n".toByteArray()); return
            }
            val length = end - start + 1
            val status = if (range == null) "200 OK" else "206 Partial Content"
            val headers = "HTTP/1.1 $status\r\nContent-Type: application/octet-stream\r\nAccept-Ranges: bytes\r\nContent-Length: $length\r\n" +
                (if (range != null) "Content-Range: bytes $start-$end/$size\r\n" else "") + "Connection: close\r\n\r\n"
            output.write(headers.toByteArray()); output.flush()
            if (request[0] == "HEAD") return
            source = TorrentFileDataSource({ task.handle }, { task.engine?.cacheFlushGeneration ?: -1L }, task.nativeLock)
            clients[socket] = source
            source.open(DataSpec.Builder().setUri(Uri.fromFile(java.io.File(task.folder, target.path))).setPosition(start).setLength(length).build())
            val buffer = ByteArray(32 * 1024)
            while (!closed) {
                val count = source.read(buffer, 0, buffer.size)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        } catch (_: Exception) {
            // Decoder close/seek and task cancellation close the range connection.
        } finally {
            clients.remove(socket); runCatching { source?.close() }; runCatching { socket.close() }
        }
    }
    override fun close() {
        closed = true; runCatching { server.close() }
        clients.forEach { (socket, source) -> runCatching { socket.close() }; runCatching { source.close() } }
        workers.shutdownNow()
    }
}
