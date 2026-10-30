package com.omniplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.net.Uri
import android.os.Build
import java.io.File

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val prefs = SettingsStore.prefs(context)
        val expected = prefs.getLong("update-download-id", -1L)
        if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2L) != expected) return
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(expected)) ?: return
        cursor.use {
            if (!it.moveToFirst() || it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) return
            val uri = dm.getUriForDownloadedFile(expected) ?: return
            val apk = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?.let { d -> File(d, prefs.getString("update-apk-name", "").orEmpty()) }
            if (apk == null || !apk.exists()) return
            prefs.edit().putString("pending-update-apk", apk.absolutePath).apply()
            val install = if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            } else AppUpdater.installerIntent(context, apk)
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(install) }
        }
    }
}

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(launch) }
    }
}
