package com.freemangnetplayer

import android.content.Context
import android.os.Environment
import java.io.File

object SettingsStore {
    fun prefs(c: Context) = c.getSharedPreferences("player-settings", Context.MODE_PRIVATE)
    fun root(c: Context, download: Boolean): File {
        val key = if (download) "download-path" else "cache-path"
        val fallback = File(c.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: c.filesDir,
            if (download) "Downloads" else "Cache")
        return File(prefs(c).getString(key, fallback.absolutePath) ?: fallback.absolutePath)
    }
    fun maxDownloads(c: Context) = prefs(c).getInt("max-downloads", 2).coerceIn(1, 8)
    fun maxCaches(c: Context) = prefs(c).getInt("max-caches", 10).coerceIn(1, 100)
    fun validateRoot(path: String): File {
        require(path.startsWith("/")) { "请输入绝对路径" }
        val root = File(path).canonicalFile
        require(root.isDirectory || root.mkdirs()) { "无法创建目录：$root，请选择应用可写目录" }
        val probe = File.createTempFile("fmp-write-", ".tmp", root)
        try { probe.writeText("write-check") } finally { probe.delete() }
        return root
    }
    fun taskFolder(c: Context, download: Boolean, id: String): File =
        File(root(c, download), "FreeMangnetPlayerTasks/$id").apply {
            require(isDirectory || mkdirs()) { "无法创建任务目录" }
        }
}
