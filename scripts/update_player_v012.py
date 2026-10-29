from pathlib import Path

root = Path('app/src/main')
layout = root / 'res/layout'
layout.mkdir(exist_ok=True)
icons = {
    'fullscreen': 'M3,9V3H9V5H5V9ZM15,3H21V9H19V5H15ZM19,15H21V21H15V19H19ZM9,19V21H3V15H5V19Z',
    'pip': 'M3,3H21V21H3ZM5,5V19H19V5ZM11,11H17V17H11Z',
    'playlist': 'M3,5H16V7H3ZM3,11H16V13H3ZM3,17H11V19H3ZM17,14L23,18L17,22Z',
}
for name, path in icons.items():
    (root / f'res/drawable/ic_player_{name}.xml').write_text(f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFFFF" android:pathData="{path}"/></vector>''', encoding='utf-8')

def image(id, description, drawable):
    return f'''<ImageButton android:id="@+id/{id}" android:layout_width="44dp" android:layout_height="44dp" android:background="?android:attr/selectableItemBackgroundBorderless" android:padding="10dp" android:contentDescription="{description}" android:src="{drawable}"/>'''

(layout / 'player_controls.xml').write_text('''<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="match_parent">
<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_gravity="bottom" android:orientation="vertical" android:background="#B8000000" android:paddingHorizontal="4dp" android:paddingBottom="4dp">
<LinearLayout android:layout_width="match_parent" android:layout_height="32dp" android:gravity="center_vertical">
<TextView android:id="@+id/exo_position" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textColor="#FFFFFF" android:textSize="12sp" android:minWidth="40dp" android:gravity="center" android:text="00:00"/>
<androidx.media3.ui.DefaultTimeBar android:id="@+id/exo_progress" android:layout_width="0dp" android:layout_weight="1" android:layout_height="32dp" app:played_color="#29CEC0" app:scrubber_color="#FFFFFF" app:buffered_color="#88FFFFFF"/>
<TextView android:id="@+id/exo_duration" android:layout_width="wrap_content" android:layout_height="wrap_content" android:textColor="#FFFFFF" android:textSize="12sp" android:minWidth="40dp" android:gravity="center" android:text="00:00"/>
</LinearLayout>
<LinearLayout android:layout_width="match_parent" android:layout_height="44dp" android:gravity="center_vertical">
''' + image('exo_rew', '后退 15 秒', '@drawable/exo_styled_controls_rewind')
    + image('exo_play_pause', '播放/暂停', '@drawable/exo_styled_controls_play')
    + image('exo_ffwd', '前进 15 秒', '@drawable/exo_styled_controls_fastforward')
    + '''<Space android:layout_width="0dp" android:layout_height="1dp" android:layout_weight="1"/>
<TextView android:id="@+id/player_speed" android:layout_width="44dp" android:layout_height="44dp" android:gravity="center" android:text="1.0×" android:textColor="#FFFFFF" android:textSize="13sp" android:background="?android:attr/selectableItemBackgroundBorderless" android:contentDescription="播放倍速" android:clickable="true" android:focusable="true"/>
'''+ image('player_files', '切换视频', '@drawable/ic_player_playlist')
    + image('player_pip', '悬浮播放', '@drawable/ic_player_pip')
    + image('player_fullscreen', '全屏播放', '@drawable/ic_player_fullscreen')
    + '</LinearLayout></LinearLayout></FrameLayout>', encoding='utf-8')

p = root / 'java/com/freemangnetplayer/PlayerActivity.kt'
s = p.read_text(encoding='utf-8')
s = s.replace('import android.net.Uri', 'import android.net.Uri\nimport android.widget.TextView\nimport android.view.View\nimport android.view.WindowManager')
s = s.replace('        super.onCreate(savedInstanceState)', '        super.onCreate(savedInstanceState)\n        window.setBackgroundDrawableResource(android.R.color.black)\n        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)', 1)
s = s.replace('if (File(current.folder, file.path).exists() && file.done > 0) startPlayback(current, file)', 'if (File(current.folder, file.path).exists()) runCatching { startPlayback(current, file) }.onFailure { playerMessage = "播放器准备失败：${it.localizedMessage}"; retryAfter = System.currentTimeMillis() + 3000 }')
s = s.replace('.put("source", source)', '.put("source", current.source)')
s = s.replace('.put("speed", speed)', '.put("duration", player?.duration ?: 0).put("buffered", player?.bufferedPosition ?: 0).put("playerError", playerMessage).put("retainedCache", current.retainedCache).put("speed", speed)')
s = s.replace('        task = result.getOrNull()', '        task = result.getOrNull()\n        task?.let { TorrentTasks.releaseOtherTemporaryTasks(it) }')
a = s.index('                        AndroidView(factory = { c -> PlayerView(c).apply {')
b = s.index('\n                    }\n                } else {', a)
s = s[:a] + '                        VideoSurface(Modifier.fillMaxSize(), { fileDialog = true })' + s[b:]
a = s.index('                            AndroidView(factory = { c -> PlayerView(c).apply')
b = s.index('\n                        } else {', a)
s = s[:a] + '                            VideoSurface(Modifier.fillMaxWidth().then(if (compact) Modifier.height(240.dp) else Modifier.weight(1f)), { fileDialog = true })' + s[b:]
a = s.index('                        if (player != null) {\n                            Row(')
b = s.index('                        if (current.files.isNotEmpty()) Row', a)
s = s[:a] + s[b:]
s = s.replace('OutlinedButton(onClick = { requestPlayback(false) }', 'OutlinedButton(onClick = { TorrentTasks.retainCache(current); playerMessage = "已保存为缓存，切换资源后继续保留" }')
s = s.replace('{ Text("预览") }', '{ Text(if (current.retainedCache) "已缓存" else "缓存") }')
s = s.replace('        TorrentTasks.playFile(task, index); requested = true; autoPlay = true', '        TorrentTasks.switchStreamFile(task, index); requested = true; autoPlay = true')
s = s.replace('        val media = ExoPlayer.Builder(this)', '        val media = ExoPlayer.Builder(this)\n            .setSeekBackIncrementMs(15000).setSeekForwardIncrementMs(15000)')
s = s.replace('            override fun onPlayerError(error: PlaybackException) {\n                savedPosition = media.currentPosition\n                playerMessage = "等待已校验缓存：${error.errorCodeName}"\n                retryAfter = System.currentTimeMillis() + 3000\n                player = null; media.release()\n            }', '''            override fun onPlayerError(error: PlaybackException) {
                // Release outside ExoPlayer's callback; stale callbacks must not clear a new player.
                window.decorView.post {
                    if (player === media && !isDestroyed) {
                        savedPosition = media.currentPosition
                        playerMessage = "播放失败：${error.errorCodeName}；可重试或查看网络诊断"
                        retryAfter = System.currentTimeMillis() + 3000
                        releasePlayer()
                    }
                }
            }''')
s = s.replace('        media.setMediaItem(', '        player = media\n        media.setMediaItem(')
s = s.replace('        player = media\n    }', '    }')
s = s.replace('    private fun releasePlayer() { player?.let { savedPosition = it.currentPosition; it.release() }; player = null }', '''    private fun releasePlayer() {
        val old = player ?: return
        player = null
        savedPosition = old.currentPosition
        old.stop()
        old.release()
    }
    @Composable
    private fun VideoSurface(modifier: Modifier, onFiles: () -> Unit) {
        AndroidView(factory = { context ->
            PlayerView(context).apply {
                setControllerLayoutId(R.layout.player_controls)
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                controllerShowTimeoutMs = 3500
                setShowRewindButton(true); setShowFastForwardButton(true)
                findViewById<View>(R.id.player_fullscreen).setOnClickListener { changeFullscreen(!fullscreen) }
                findViewById<View>(R.id.player_pip).setOnClickListener { enterFloating() }
                findViewById<View>(R.id.player_files).setOnClickListener { onFiles() }
                findViewById<View>(R.id.player_speed).setOnClickListener { cycleSpeed() }
            }
        }, update = { view ->
            view.player = player
            view.useController = !floating
            view.findViewById<TextView>(R.id.player_speed).text = "${speed}×"
            view.findViewById<View>(R.id.player_fullscreen).contentDescription = if (fullscreen) "退出全屏" else "全屏播放"
        }, onRelease = { view -> view.player = null }, modifier = modifier.background(Color.Black))
    }''')
p.write_text(s, encoding='utf-8')

p = root / 'java/com/freemangnetplayer/SettingsStore.kt'
s = p.read_text(encoding='utf-8').replace('getInt("max-downloads", 2)', 'getInt("max-downloads", 5)')
p.write_text(s, encoding='utf-8')
p = Path('app/build.gradle.kts')
s = p.read_text(encoding='utf-8').replace('versionCode = 12; versionName = "0.1.11"', 'versionCode = 13; versionName = "0.1.12"')
p.write_text(s, encoding='utf-8')
