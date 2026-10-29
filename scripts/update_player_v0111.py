"""Add fullscreen/PiP presentation and native network diagnostics."""
from pathlib import Path

path = Path('app/src/main/java/com/freemangnetplayer/PlayerActivity.kt')
s = path.read_text(encoding='utf-8-sig')
s = s.replace('import android.Manifest', '''import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.Manifest''')
s = s.replace('    private var speed by', '''    private var fullscreen by mutableStateOf(false)
    private var floating by mutableStateOf(false)
    private var speed by''')
s = s.replace('        speed = savedInstanceState?.getFloat("speed") ?: 1f', '''        speed = savedInstanceState?.getFloat("speed") ?: 1f
        fullscreen = savedInstanceState?.getBoolean("fullscreen") ?: false
        updateSystemBars()''')
s = s.replace('                var deleteDialog by remember { mutableStateOf(false) }', '''                var deleteDialog by remember { mutableStateOf(false) }
                var networkDialog by remember { mutableStateOf(false) }
                BackHandler(enabled = fullscreen && !floating) { setFullscreen(false) }''')
# Keep engine diagnostics and playback retry alive in all presentation modes.
start = s.index('                        LaunchedEffect(current) {')
end = s.index('                        Card(Modifier.fillMaxWidth()) {', start)
effect = s[start:end]
s = s[:start] + s[end:]
insert = s.index('                Column(Modifier.fillMaxSize()')
s = s[:insert] + '                if (current != null) {\n' + effect + '                }\n' + s[insert:]
s = s.replace('                                        .put("queued", current.queued)', '''                                        .put("payloadRate", current.payloadRate).put("payloadBytes", current.payloadBytes)
                                        .put("rate", current.rate).put("dhtNodes", current.dhtNodes).put("seeds", current.seeds)
                                        .put("downloadLimit", current.downloadLimit).put("uploadLimit", current.uploadLimit)
                                        .put("fullscreen", fullscreen).put("floating", floating).put("networkMessage", current.networkMessage)
                                        .put("queued", current.queued)''')
start = s.index('                Column(Modifier.fillMaxSize()')
s = s[:start] + '''                if ((fullscreen || floating) && player != null) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        AndroidView(factory = { c -> PlayerView(c).apply {
                            useController = !floating
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            player = this@PlayerActivity.player
                        } }, update = { it.player = player; it.useController = !floating }, modifier = Modifier.fillMaxSize())
                        if (!floating) {
                            Row(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { setFullscreen(false) }) { Text("退出全屏") }
                                Button(onClick = { enterFloating() }) { Text("悬浮播放") }
                                Button(onClick = { fileDialog = true }) { Text("切换视频") }
                                Button(onClick = { cycleSpeed() }) { Text("${speed}×") }
                            }
                        }
                    }
                } else {
''' + s[start:]
# Player needs background import as the fullscreen view is black.
s = s.replace('import androidx.compose.foundation.layout.*', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.layout.*')
s = s.replace('''                                Text("${bytes(current.done)} / ${bytes(current.total)} · ${bytes(current.rate.toLong())}/秒 · ${current.peers} 节点", style = MaterialTheme.typography.bodySmall)''', '''                                Text("${bytes(current.done)} / ${bytes(current.total)} · ${bytes(current.payloadRate.toLong())}/秒 · ${current.peers} 节点", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { networkDialog = true }) { Text("网络诊断 · 不限速 · DHT ${current.dhtNodes}") }''')
s = s.replace('''                        if (player != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp))''', '''                        if (player != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { setFullscreen(true) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("全屏播放") }
                                OutlinedButton(onClick = { enterFloating() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("悬浮播放") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp))''')
s = s.replace('''{ val values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f); speed = values[(values.indexOf(speed) + 1) % values.size]; player?.setPlaybackSpeed(speed) }''', '{ cycleSpeed() }')
# Dialogs also work while in fullscreen.
start = s.index('                        if (fileDialog) AlertDialog(')
end = s.index('                        if (deleteDialog) AlertDialog(', start)
dialogs = s[start:end]
s = s[:start] + s[end:]
end = s.index('\n    override fun onSaveInstanceState')
tail = s[:end]
needle = '''                }
            }
        }
    }'''
tail = tail.rstrip() + chr(10)
assert tail.endswith(needle + '\n'), tail[-200:]
tail = tail[:-len(needle + '\n')] + '''                }
                }
                if (current != null) {
''' + dialogs + '''                    if (networkDialog) AlertDialog(onDismissRequest = { networkDialog = false }, title = { Text("真实网络诊断") }, text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("应用下载限速：${current.downloadLimit}（0 为不限速）\\n应用上传限速：${current.uploadLimit}（0 为不限速）")
                            Text("有效载荷速度：${bytes(current.payloadRate.toLong())}/秒\\n含协议总速度：${bytes(current.rate.toLong())}/秒")
                            Text("资源节点：${current.peers}，其中种子：${current.seeds}\\n连接数：${current.connectionCount}，DHT 节点：${current.dhtNodes}")
                            Text("有效接收：${bytes(current.payloadBytes)}\\nTCP / uTP、DHT、Tracker、局域网发现均已启用")
                            Text(if (current.peers == 0) "当前没有资源连接。DHT 为 0 时请对照切换 Wi-Fi / 移动网络，检查 VPN 和 UDP 可达性；DHT 正常仍无节点时可能是资源无人做种。"
                                else if (current.payloadRate < 1024) "已建立连接但没有足够有效数据；请用同一链接、同一网络与其他 BT 客户端对照。节点存在不代表节点愿意发送数据。"
                                else "正在接收有效数据。边下边播需要下载速度持续高于视频码率。")
                            if (current.networkMessage.isNotBlank()) Text("最近的 Tracker / 监听事件：${current.networkMessage}")
                            Text(playerMessage)
                        }
                    }, confirmButton = { TextButton(onClick = { networkDialog = false }) { Text("关闭") } })
                }
            }
        }
    }
'''
s = tail + s[end:]
s = s.replace('        outState.putFloat("speed", speed)', '        outState.putFloat("speed", speed)\n        outState.putBoolean("fullscreen", fullscreen)')
s = s.replace('    private fun releasePlayer()', '''    private fun cycleSpeed() {
        val values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        speed = values[(values.indexOf(speed) + 1) % values.size]
        player?.setPlaybackSpeed(speed)
    }
    private fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
        requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        updateSystemBars()
    }
    private fun updateSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else controller.show(WindowInsetsCompat.Type.systemBars())
    }
    private fun enterFloating() {
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            playerMessage = "此设备不支持系统悬浮播放（需要 Android 8.0+ 与画中画支持）"
            return
        }
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (error: Exception) { playerMessage = "无法进入悬浮播放，请检查系统画中画权限：${error.localizedMessage}" }
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        floating = isInPictureInPictureMode
        if (!floating) updateSystemBars()
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true && !isFinishing) enterFloating()
    }
    private fun releasePlayer()''')
s = s.replace('''    override fun onStop() {
        releasePlayer()''', '''    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode) { super.onStop(); return }
        releasePlayer()''')
path.write_text(s, encoding='utf-8')

path = Path('app/src/main/AndroidManifest.xml')
s = path.read_text(encoding='utf-8-sig').replace('<activity android:name=".PlayerActivity" android:exported="false" />',
    '<activity android:name=".PlayerActivity" android:exported="false" android:supportsPictureInPicture="true" android:resizeableActivity="true" android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout" />')
path.write_text(s, encoding='utf-8')
print('Applied fullscreen, PiP and diagnostics')
