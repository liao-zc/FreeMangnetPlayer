from pathlib import Path
p=Path('scripts/build_peer_recovery_docs.py')
s=p.read_text(encoding='utf-8')
s=s.replace("'invalid-media-shows-error-without-process-restart']", "'invalid-media-shows-error-without-process-restart','realmedia-native-decoder-real-playback','realmedia-embedded-pause-seek-speed-fullscreen']")
s=s.replace('真实 H.264/AAC 视频。', '真实 H.264/AAC 视频及 RealVideo/RealAudio 视频。')
marker="text+='''## 3. 用户提供的同一磁力：旧版与新版观察"
assert marker in s
s=s.replace(marker, """text+='''## RealMedia 视频解码检查

更正后的磁力对应 `[www.kx508.com]复仇者联盟 DVD国语.rmvb`，大小 1,573,322,851 字节，分片大小 1 MiB。HTTP 取得种子后已验证 info 字典的 SHA-1 与用户 BTIH 一致，原种子没有 Tracker。[种子校验记录](evidence/peer-recovery-user-metadata.json)。

旧版没有将 RM/RMVB 列入视频文件，Media3 内核也不支持 RealMedia 容器。本版将它们识别为视频，并使用 LibVLC 软件解码；其他格式继续使用 Media3。LibVLC 通过仅监听本机的 HTTP Range 接口读取经过原有分片校验的数据，仍使用同一个下载任务。此依赖会增加安装包体积。

实际 RealVideo/RealAudio 视频验证：显示真实画面、播放时间推进、暂停/继续、向前跳转、1.25 倍速、全屏返回及进程未重启。此受控视频用于确认解码功能，用户电影是否实际起播另按下面的公网观测记录。

'''
text+=image('revision-09c-realmedia.png','实际 RealMedia 视频与内置播放器控制条')
text+=image('revision-09d-realmedia-fullscreen.png','RealMedia 实际全屏播放')
"""+marker)
s=s.replace('AndroidX Media3、jlibtorrent', 'AndroidX Media3、LibVLC、jlibtorrent')
p.write_text(s,encoding='utf-8')
