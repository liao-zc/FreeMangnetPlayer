package com.omniplayer.core
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.os.Environment
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

/** GitHub Releases based updater. Android still requires the user to confirm APK installation. */
object AppUpdater {
    data class Release(val version: String, val apkUrl: String, val pageUrl: String)
    private const val API = "https://api.github.com/repos/liao-zc/OmniPlayer/releases/latest"

    suspend fun latest(context: Context): Release? = withContext(Dispatchers.IO) {
        val connection = URL(API).openConnection().apply {
            connectTimeout = 12_000; readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OmniPlayer-Android")
        }
        val body = connection.getInputStream().bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null
        val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        val remote = json.optString("tag_name").removePrefix("v")
        val asset = json.optJSONArray("assets") ?: return@withContext null
        val apks = (0 until asset.length()).map { asset.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", true) }
        val deviceAbis = if (Build.VERSION.SDK_INT >= 21) Build.SUPPORTED_ABIS.toList() else listOf(Build.CPU_ABI)
        val chosen = deviceAbis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.optString("name").contains(abi, true) } }
            ?: apks.firstOrNull { it.optString("name").contains("universal", true) }
            ?: apks.firstOrNull { it.optString("name").matches(Regex("(?i).*OmniPlayer.*\\.apk")) }
            ?: apks.firstOrNull()
        if (chosen == null || compareVersions(remote, current) <= 0) null
        else Release(remote, chosen.getString("browser_download_url"), json.optString("html_url"))
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = Regex("\\d+").findAll(a).map { it.value.toInt() }.toList()
        val right = Regex("\\d+").findAll(b).map { it.value.toInt() }.toList()
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrNull(i) ?: 0).compareTo(right.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    fun enqueue(context: Context, release: Release): Long {
        val name = "OmniPlayer-${release.version}.apk"
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("OmniPlayer ${release.version}")
             .setDescription("Downloading update package")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request).also { id ->
            SettingsStore.prefs(context).edit().putLong("update-download-id", id).putString("update-apk-name", name).apply()
        }
    }

    fun installerIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

object UpdatePromptGate { @Volatile var promptedThisProcess = false }

object PendingUpdateInstaller {
    @Volatile private var launching = false
    fun resume(activity: Activity) {
        if (launching || android.os.Build.VERSION.SDK_INT < 26 || !activity.packageManager.canRequestPackageInstalls()) return
        val prefs = SettingsStore.prefs(activity)
        val apk = prefs.getString("pending-update-apk", null)?.let(::File)?.takeIf(File::isFile) ?: return
        launching = true
        runCatching { activity.startActivity(AppUpdater.installerIntent(activity, apk)) }
            .onSuccess { prefs.edit().remove("pending-update-apk").apply() }
            .onFailure { launching = false }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ launching = false }, 3_000)
    }
}
