package com.freemangnetplayer

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onCreate() {
        super.onCreate()
        TorrentTasks.initialize(this)
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("downloads", "下载与缓存任务", NotificationManager.IMPORTANCE_LOW))
        foreground()
        scope.launch { while (isActive) { delay(3000); foreground() } }
    }
    private fun foreground() {
        val tasks = TorrentTasks.tasks.values
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val rows = org.json.JSONArray()
            tasks.forEach { rows.put(org.json.JSONObject().put("id", it.id).put("done", it.done).put("total", it.total)
                .put("paused", it.paused).put("queued", it.queued).put("cancelled", it.cancelled).put("folder", it.folder.absolutePath)) }
            java.io.File(filesDir, "tasks-check.json").writeText(rows.toString())
        }
        val active = tasks.count { !it.paused && !it.cancelled && !it.complete }
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("open-tab", "下载"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(value: String) = PendingIntent.getService(this, value.hashCode(), Intent(this, DownloadService::class.java).setAction(value), PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "downloads") else Notification.Builder(this)
        val notification = builder.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("FreeMangnetPlayer")
            .setContentText("$active 个下载 / 缓存任务；点击管理")
            .setContentIntent(pending).setOnlyAlertOnce(true).setOngoing(active > 0)
            .addAction(Notification.Action.Builder(null, "暂停全部", action("pause")).build())
            .addAction(Notification.Action.Builder(null, "继续全部", action("resume")).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(101, notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "pause" -> TorrentTasks.tasks.values.toList().filter { !it.cancelled }.forEach { TorrentTasks.pause(it) }
            "resume" -> TorrentTasks.tasks.values.toList().filter { !it.cancelled }.forEach { TorrentTasks.resume(it) }
        }
        TorrentTasks.schedule()
        return START_STICKY
    }
    // Android 15 limits background dataSync foreground services to six hours per day.
    override fun onTimeout(startId: Int, fgsType: Int) {
        TorrentTasks.stopAll()
        stopSelf()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); TorrentTasks.stopAll(); super.onDestroy() }
}
