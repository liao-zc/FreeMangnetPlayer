package com.omniplayer.core
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = SettingsStore.prefs(context)
        prefs.getString("update-apk-path", null)?.let { runCatching { java.io.File(it).delete() } }
        prefs.edit().remove("pending-update-apk").remove("update-apk-path").remove("update-url")
            .remove("update-notes").putString("update-state", "installed").apply()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(launch) }
    }
}
