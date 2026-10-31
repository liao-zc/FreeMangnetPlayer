package com.omniplayer.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** HTTP Range based updater. Partial data is retained across pause, errors, and process restarts. */
class UpdateTransferService : Service() {
    companion object { const val ACTION_START = "com.omniplayer.UPDATE_START"; private const val CHANNEL = "app_updates" }
    private val running = AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(41, notification("正在准备更新"))
        if (running.compareAndSet(false, true)) Thread { transfer(startId) }.start()
        return START_NOT_STICKY
    }

    private fun transfer(startId: Int) {
        val prefs = SettingsStore.prefs(this)
        try {
            val url = prefs.getString("update-url", null) ?: error("更新地址缺失")
            val target = prefs.getString("update-apk-path", null)?.let(::File) ?: error("更新文件路径缺失")
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, target.name + ".part")
            var offset = part.length()
            val request = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000; readTimeout = 20_000; instanceFollowRedirects = true
                setRequestProperty("User-Agent", "OmniPlayer-Android")
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            connection = request
            request.connect()
            val code = request.responseCode
            if (offset > 0 && code != HttpURLConnection.HTTP_PARTIAL) {
                // Server does not support ranges: restart safely instead of corrupting the APK.
                part.delete(); offset = 0
            } else if (code !in 200..299) error("服务器返回状态 $code")
            val total = if (code == HttpURLConnection.HTTP_PARTIAL) {
                request.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: (offset + request.contentLengthLong)
            } else request.contentLengthLong
            prefs.edit().putString("update-state", "downloading").putLong("update-total", total).putLong("update-bytes", offset).apply()
            RandomAccessFile(part, "rw").use { file ->
                file.seek(offset)
                request.inputStream.buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val transferState = prefs.getString("update-state", "")
                        if (transferState == "paused") {
                            prefs.edit().putLong("update-bytes", file.filePointer).apply()
                            return
                        }
                        if (transferState == "queued") prefs.edit().putString("update-state", "downloading").apply()
                        val read = input.read(buffer)
                        if (read < 0) break
                        file.write(buffer, 0, read)
                        val bytes = file.filePointer
                        prefs.edit().putLong("update-bytes", bytes).apply()
                        getSystemService(NotificationManager::class.java).notify(41, notification("正在下载更新：${formatBytes(bytes)}${if (total > 0) " / ${formatBytes(total)}" else ""}"))
                    }
                }
            }
            if (total > 0 && part.length() != total) error("下载内容不完整，已保留部分文件，可继续更新")
            if (target.exists()) target.delete()
            check(part.renameTo(target)) { "无法保存更新安装包" }
            prefs.edit().putLong("update-bytes", target.length()).putLong("update-total", target.length())
                .putString("pending-update-apk", target.absolutePath).putString("update-state", "ready").apply()
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            val pending = launch?.let { PendingIntent.getActivity(this, 42, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
            getSystemService(NotificationManager::class.java).notify(42, NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("更新包已下载完成")
                .setContentText("点击打开系统安装确认").setContentIntent(pending).setAutoCancel(true).setOngoing(false).build())
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            if (prefs.getString("update-state", "") != "paused") prefs.edit().putString("update-state", "failed").apply()
            getSystemService(NotificationManager::class.java).notify(41, notification("更新下载中断：${e.localizedMessage ?: "网络错误"}；可继续更新"))
        } finally {
            connection?.disconnect(); connection = null; running.set(false); stopSelf(startId)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "软件更新", NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("OmniPlayer 更新").setContentText(text)
        .setOngoing(true).setOnlyAlertOnce(true).build()
    private fun formatBytes(value: Long): String = if (value >= 1024L * 1024) "%.1f MB".format(value / (1024.0 * 1024)) else "$value B"
}
