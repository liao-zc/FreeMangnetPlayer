package com.omniplayer.magnet
import com.omniplayer.core.*
import android.content.Context
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Refresh public discovery endpoints without delaying any magnet start. */
object TrackerDirectory {
    private const val ENDPOINT = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"
    private const val LIMIT = 48
    private fun parse(text: String) = text.lineSequence().map { it.trim() }.filter {
        runCatching { val u = URI(it); u.scheme in setOf("udp", "https", "http") && !u.host.isNullOrBlank() && it.length <= 1024 }.getOrDefault(false)
    }.distinct().take(LIMIT).toList()
    fun cached(context: Context): List<String> =
        parse(SettingsStore.prefs(context).getString("public-trackers", "").orEmpty())
    fun refresh(context: Context): List<String> {
        val preferences = SettingsStore.prefs(context)
        val cached = cached(context)
        if (cached.isNotEmpty() && System.currentTimeMillis() - preferences.getLong("public-trackers-at", 0) < 86_400_000) return cached
        return runCatching {
            val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 6000; connection.readTimeout = 6000
                check(connection.responseCode == 200)
                val text = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(output.size() + count <= 65536)
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
                val values = parse(text)
                check(values.isNotEmpty())
                preferences.edit().putString("public-trackers", values.joinToString("\n"))
                    .putLong("public-trackers-at", System.currentTimeMillis()).apply()
                values
            } finally { connection.disconnect() }
        }.getOrDefault(cached)
    }
}
