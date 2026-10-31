package com.omniplayer.live

import java.net.HttpURLConnection
import java.net.URL

internal object IptvPlaylistRepository {
    fun fetch(url: String): String {
        if (url.startsWith("file:", true)) {
            return java.io.File(java.net.URI(url)).readText(Charsets.UTF_8)
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        return try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(16 * 1024)
                var total = 0
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 80 * 1024 * 1024) { "播放列表超过 80 MB" }
                    out.append(buffer, 0, count)
                }
                out.toString()
            }
        } finally {
            connection.disconnect()
        }
    }
}