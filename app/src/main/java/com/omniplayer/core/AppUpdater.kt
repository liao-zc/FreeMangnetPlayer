package com.omniplayer.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.URL

/** Release lookup and durable state for resumable app updates. */
object AppUpdater {
    data class Release(val version: String, val apkUrl: String, val pageUrl: String, val notes: String = "")
    const val ACTION_INSTALL_READY = "com.omniplayer.UPDATE_INSTALL_READY"
    private const val API = "https://api.github.com/repos/liao-zc/OmniPlayer/releases/latest"

    suspend fun latest(context: Context): Release? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val connection = URL(API).openConnection().apply {
            connectTimeout = 12_000; readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OmniPlayer-Android")
        }
        val json = JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null
        val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        val remote = json.optString("tag_name").removePrefix("v")
        val assets = json.optJSONArray("assets") ?: return@withContext null
        val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name").endsWith(".apk", true) }
        val abis = if (Build.VERSION.SDK_INT >= 21) Build.SUPPORTED_ABIS.toList() else listOf(Build.CPU_ABI)
        val chosen = abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.optString("name").contains(abi, true) } }
            ?: apks.firstOrNull { it.optString("name").contains("universal", true) }
            ?: apks.firstOrNull()
        if (chosen == null || compareVersions(remote, current) <= 0) null
        else Release(remote, chosen.getString("browser_download_url"), json.optString("html_url"), json.optString("body").trim())
    }

    private fun compareVersions(a: String, b: String): Int {
        val x = Regex("\\d+").findAll(a).map { it.value.toInt() }.toList()
        val y = Regex("\\d+").findAll(b).map { it.value.toInt() }.toList()
        for (i in 0 until maxOf(x.size, y.size)) (x.getOrNull(i) ?: 0).compareTo(y.getOrNull(i) ?: 0).let { if (it != 0) return it }
        return 0
    }

    fun enqueue(context: Context, release: Release) {
        val prefs = SettingsStore.prefs(context)
        val same = prefs.getString("update-url", null) == release.apkUrl
        val apkName = "OmniPlayer-${release.version}.apk"
        val apk = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, apkName)
        if (apk.isFile && apk.length() > 0L && prefs.getString("pending-update-apk", null) == apk.absolutePath) {
            install(context, apk)
            return
        }
        prefs.edit().putString("update-url", release.apkUrl).putString("update-version", release.version)
            .putString("update-notes", release.notes).putString("update-apk-path", apk.absolutePath)
            .putString("update-state", if (same && apk.exists()) "paused" else "queued")
            .putLong("update-bytes", if (same) apk.length() else 0L)
            .putLong("update-total", 0L).apply()
        if (!same && apk.exists()) apk.delete()
        start(context)
    }

    fun start(context: Context) {
        val intent = Intent(context, UpdateTransferService::class.java).setAction(UpdateTransferService.ACTION_START)
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun pause(context: Context) {
        SettingsStore.prefs(context).edit().putString("update-state", "paused").apply()
    }

    fun resume(context: Context) {
        SettingsStore.prefs(context).edit().putString("update-state", "queued").apply()
        start(context)
    }

    fun installerIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun install(context: Context, apk: File) {
        SettingsStore.prefs(context).edit().putString("pending-update-apk", apk.absolutePath).putString("update-state", "ready").apply()
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val permission = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(permission) }
        } else runCatching { context.startActivity(installerIntent(context, apk)) }
    }
}

object UpdatePromptGate { @Volatile var promptedThisProcess = false }

object PendingUpdateInstaller {
    @Volatile private var attemptedThisProcess = false
    fun resetForAppLaunch() { attemptedThisProcess = false }
    fun resume(activity: Activity) {
        if (attemptedThisProcess) return
        val prefs = SettingsStore.prefs(activity)
        val apk = prefs.getString("pending-update-apk", null)?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
            ?: prefs.getString("update-apk-path", null)?.let(::File)?.takeIf { it.isFile && it.length() > 0L && prefs.getString("update-state", "") == "ready" }
            ?: return
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) return
        attemptedThisProcess = true
        runCatching { activity.startActivity(AppUpdater.installerIntent(activity, apk)) }
    }
}
