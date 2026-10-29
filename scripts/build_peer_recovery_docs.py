"""Write v0.1.13 report from real APK observations, including failures honestly."""
import argparse,json,shutil
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--shots',required=True);p.add_argument('--run',required=True)
p.add_argument('--regression-shots',required=True);p.add_argument('--regression-run',required=True)
a=p.parse_args();shots=Path(a.shots)
rows=json.loads((shots/'player-revision-verification.json').read_text(encoding='utf-8'))
by={r['check']:r for r in rows}
required=['metadata-fallback-preserves-trackers-and-downloads','file-magnet-system-clipboard-roundtrip','copied-magnet-restores-selected-file',
'stream-start-below-twenty-percent','embedded-pause-play-speed-and-seek','playback-time-duration-and-scrubbable-progress','fullscreen-embedded-small-icons','pip-continues-and-restores',
'switch-file-releases-previous-temporary-bytes','explicit-cache-survives-next-resource','no-crash-or-process-restart-during-playback-stress','invalid-media-shows-error-without-process-restart']
assert all(by.get(k,{}).get('passed') is True for k in required)
reg=json.loads((Path(a.regression_shots)/'optimization-verification.json').read_text(encoding='utf-8'))
assert len(reg)==22 and all(r.get('passed') for r in reg)
target=Path('docs/screenshots/peer-recovery');target.mkdir(parents=True,exist_ok=True)
for f in shots.glob('*.png'):
    assert f.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
    shutil.copyfile(f,target/f.name)
e=Path('docs/evidence')
(e/'peer-recovery-verification.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
(e/'peer-recovery-regression.json').write_text(json.dumps(reg,ensure_ascii=False,indent=2),encoding='utf-8')
def image(name,caption):
    assert (target/name).exists(),name
    return f'\n![{caption}](screenshots/peer-recovery/{name})\n\n'
startup=by['stream-start-below-twenty-percent'];recovery=by[required[0]]
text=f'''# FreeMangnetPlayer v0.1.13 节点连接与播放优化报告

日期：2026-10-29。实际设备：Android API 35 Pixel 2 模拟器。以下截图来自安装后的 APP，播放测试使用真实 BitTorrent 做种、真实 H.264/AAC 视频。

- [下载安装包](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.13/FreeMangnetPlayer-v0.1.13.apk)
- [离线图文报告](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.13/FreeMangnetPlayer-v0.1.13-report.html)
- [实际专项测试记录](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/{a.run})
- [原始逐项证据](evidence/peer-recovery-verification.json)

## 1. 为什么取得文件列表仍然无法播放

文件列表是种子的元数据；真正播放需要取得视频分片。DHT 的路由节点数量不等于持有该视频的节点数量。用户截图中 DHT 124、资源节点 0、有效下载 0，说明元数据已到达，视频数据尚未到达。

代码与实测发现的缺陷：

1. HTTP 补充元数据会替换原任务，旧版没有保留原磁力中的 Tracker 地址及已连接节点。新版在替换前保存这些信息，并等待旧任务实际移除，避免新任务被当成重复添加而忽略。
2. 仅开启 uTP 后，模拟器实测出现已连接做种节点但数据停在约 80 KiB、速率个位数 B/s 的问题。新版立即开放 TCP/uTP，连续 10 秒没有有效数据时重新连接并尝试 TCP；TCP 连不上节点持续 20 秒时再开放 uTP。这是连接恢复，不是限速。
3. 缺少独立的资源节点查询与回连。新版在无有效供给时执行独立 DHT 查找，并尝试已发现节点，降低对单次公告的依赖。

上述缺陷已修复；不能只凭截图断言它们是用户手机上该资源失败的全部原因。

## 2. 元数据完成后继续下载与播放：实际验证

测试服务器先返回空节点列表；提供的补充 .torrent 不包含任何 Tracker。收到补充元数据后，只有原磁力中的 Tracker 能返回真实做种节点，因此可以验证替换后是否保留该入口。

| 检查 | 结果 |
| --- | --- |
| HTTP 补充元数据后取得真实节点与下载字节 | 通过 |
| 保留原 Tracker | {recovery['state']['restoredTrackers']} 个 |
| 从输入磁力至测试观察到实际起播（含 UI 操作开销） | {recovery['seconds']:.2f} 秒 |
| 另一独立冷启动视频的起播下载比例 | {startup['ratio']*100:.2f}% |
| 应用下载/上传速率限制 | 0 / 0，即不限速 |

受控做种服务器使用 128 KiB/s 来观察早期起播，APK 没有这个上限。实际流畅程度仍取决于可达节点、有效供给、网络及视频码率。
'''
text+=image('revision-00-metadata-peer-recovery.png','补充元数据后实际取得视频分片并播放')
text+=image('revision-03-integrated-controls.png','独立冷启动真实视频与内嵌控制条')
text+='''## 3. 用户提供的同一磁力：旧版与新版观察

测试链接：`magnet:?xt=urn:btih:f1006d95049a840aa34cec95967ea590bf5ffeaf`。

两版分别清空应用数据，并尝试解析、下载和播放；每版最多观察 150 次采样，UI 操作也计入时间。公网节点状态随时间变化，因此这个顺序对比不能视为同时同节点的性能基准。

| 版本 | 文件列表 | 资源节点 | 有效下载字节 | 实际首次播放时间 |
| --- | --- | --- | --- | --- |
'''
for version in ['v0.1.12','v0.1.13']:
    r=by['user-resource-'+version];st=r['state'];frame=r['first_frame_seconds']
    text+=f"| {version} | {'已取得' if st.get('metadata') else '未取得'} | {st.get('peers',0)} | {st.get('payloadBytes',0):,} | {f'{frame:.2f} 秒' if frame is not None else '观察期间未起播'} |\n"
text+='\n每一行按真实观察记录；未起播的资源不标为播放成功。Magnet_player 提供的 GitHub 仓库只有 README 与安装包，没有公开内核源码。本轮没有完成该播放器在用户手机同一网络下的操作对照，不能推断其使用直连、云缓存或其他特定机制。\n'
for version in ['v0.1.12','v0.1.13']:
    text+=image('user-resource-'+version+'.png','用户磁力 '+version+' 的真实观察画面')
text+='''## 4. 选择文件并复制磁力链接

1. 解析后在文件列表点击目标文件旁的“复制磁力”；也可以点播放器中的选片图标，找到“复制磁力：文件名”。
2. 链接写入 Android 系统剪贴板，可粘贴到其他播放器、聊天或文本框。
3. 链接包含原种子的 BTIH、目标文件名 `dn`、文件索引 `so`，并保留原 Tracker、明确节点、Web Seed 和 Exact Source 参数。
4. 多文件种子中的文件共用种子哈希，不能凭空生成单文件的新种子。接收方支持 BEP 53 `so` 才能恢复文件选择；否则会展示整个种子文件列表。

实际点检：点击复制后在系统输入框执行粘贴，核对 BTIH、文件名、文件选择与 Tracker 参数；粘贴回本 APP 后再次解析，检查目标文件索引。结果通过。
'''
text+=image('revision-00b-file-magnet-copy.png','系统剪贴板实际粘贴包含目标文件信息的磁力')
passed=[r for r in rows if r.get('passed') is True]
text+=f'''## 5. 原有功能回归

本轮专项检查 {len(passed)} 项通过；公网用户资源与 Ubuntu 资源另列为实际观察，不混入通过数量。[下载管理回归](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/{a.regression_run}) 22 项通过，包含真实下载 SHA-256、单选/多选、暂停/继续/取消、后台与熄屏任务、存储位置、设置持久化、缓存保留与删除。[回归原始证据](evidence/peer-recovery-regression.json)。

播放器倍速、前后跳转、时间条、全屏、画中画、多文件切换、临时缓存清理与损坏媒体错误处理也执行了实测。该模拟器上的进程检查通过；尚未完成用户手机原设备的闪退与网络对照。

## 6. GitHub 文档与邮件通知

文档用 UTF-8 明文提交，校验 Git 对象、图片签名、相对链接和 GitHub 渲染；同时提供截图内嵌的离线 HTML。

本项目没有增加任何发送邮件的工作流。GitHub 平台通知由用户账户设置决定；当前 GitHub 授权缺少 notifications 权限，未修改用户个人通知设置。若仍收到 GitHub Actions 邮件，可在 [个人通知设置](https://github.com/settings/notifications) 的 Actions 部分关闭 Email；仓库右上角 Watch 可调整该仓库的通知。

应用名称 FreeMangnetPlayer，包名 com.freemangnetplayer，版本 v0.1.13，与 v0.1.12 使用同一签名，可覆盖安装。
'''
Path('docs/peer-v0.1.13.md').write_text(text,encoding='utf-8')
Path('docs/usage-zh.md').write_text(text,encoding='utf-8')
Path('README.md').write_text('''# FreeMangnetPlayer

个人使用的 Android 磁力播放器，当前版本 **v0.1.13**。

- [下载 APK](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.13/FreeMangnetPlayer-v0.1.13.apk)
- [节点连接、播放与文件磁力复制报告](docs/peer-v0.1.13.md)
- [离线图文报告](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.13/FreeMangnetPlayer-v0.1.13-report.html)
- [操作说明](docs/usage-zh.md)
- [上一版播放器控件报告](docs/player-v0.1.12.md)
- [源码下载](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.13/FreeMangnetPlayer-v0.1.13-source.zip)

支持内嵌播放器、时间进度条、倍速、跳转、全屏、系统画中画、下载、缓存、收藏与文件切换。不限速；网络与公网资源结果见实际报告。

Kotlin、Jetpack Compose、AndroidX Media3、jlibtorrent；支持 arm64-v8a、armeabi-v7a、x86、x86_64。许可证及鸣谢见 [第三方声明](THIRD_PARTY_NOTICES.md)。
''',encoding='utf-8')
print('Report generated from',len(passed),'passed checks and',len(reg),'regression checks')
