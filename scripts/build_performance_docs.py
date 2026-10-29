"""Build the v0.1.11 report solely from actual APK test artifacts."""
import argparse
import json
import shutil
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--shots', required=True)
parser.add_argument('--run', required=True)
parser.add_argument('--commit', required=True)
parser.add_argument('--regression-shots')
parser.add_argument('--regression-run')
args = parser.parse_args()
shots = Path(args.shots)
checks = json.loads((shots / 'performance-verification.json').read_text(encoding='utf-8'))
required = ['bottom-settings-entry', 'pasted-tracker-free-magnet-metadata', 'unlimited-app-throughput',
    'real-native-limits-and-network-diagnostics', 'immersive-fullscreen-real-playback',
    'system-pip-over-home-continues-playing', 'return-from-pip', 'unlimited-download-complete-file-sha256',
    'html-encoded-paste-cold-parse', 'plain-hash-input-and-responsive-discovery', 'return-during-plain-magnet-discovery', 'existing-hash-merges-new-network-parameters']
index = {row['check']: row for row in checks}
assert all(index.get(name, {}).get('passed') is True for name in required), 'Incomplete functional checks'
fixture = json.loads((shots / 'performance-fixture.json').read_text(encoding='utf-8'))
speed = index['unlimited-app-throughput']
metadata = index['pasted-tracker-free-magnet-metadata']
pip = index['system-pip-over-home-continues-playing']
full = index['immersive-fullscreen-real-playback']
public = index.get('public-pure-hash-comparison')
target = Path('docs/screenshots/performance')
target.mkdir(parents=True, exist_ok=True)
for source in shots.glob('perf-*.png'):
    assert source.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
    shutil.copyfile(source, target / source.name)
evidence = Path('docs/evidence')
shutil.copyfile(shots / 'performance-verification.json', evidence / 'performance-verification.json')
shutil.copyfile(shots / 'performance-fixture.json', evidence / 'performance-fixture.json')

def image(name, caption):
    assert (target / name).exists(), name
    return f'\n![{caption}](screenshots/performance/{name})\n'

labels = {
    'bottom-settings-entry': '底部设置入口及设置页',
    'pasted-tracker-free-magnet-metadata': '输入框粘贴无 Tracker 磁力，真实获取元数据',
    'unlimited-app-throughput': '不限速做种端与 APK 有效吞吐量测量',
    'real-native-limits-and-network-diagnostics': '原生限速值、载荷、DHT 和节点诊断',
    'immersive-fullscreen-real-playback': '全屏真实影片播放及位置持续前进',
    'system-pip-over-home-continues-playing': '系统画中画悬浮且真实播放继续',
    'return-from-pip': '退出悬浮并恢复播放页',
    'unlimited-download-complete-file-sha256': '下载完整视频并核对 SHA-256',
    'html-encoded-paste-cold-parse': 'HTML 实体编码磁力冷启动粘贴解析',
    'plain-hash-input-and-responsive-discovery': '纯哈希输入识别、节点发现及可操作界面',
    'return-during-plain-magnet-discovery': '节点发现过程中返回首页',
    'existing-hash-merges-new-network-parameters': '同一哈希重新粘贴时合并新增节点/Tracker 并解析',
}
text = f'''# FreeMangnetPlayer v0.1.11 功能优化与速度诊断报告

日期：2026-10-29。实际 APK 在 Android API 35 手机模拟器中运行；[本轮完整测试](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/{args.run})，代码提交 `{args.commit}`。下列图片全部为运行中的实际截图。

- [下载安装包](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.11/FreeMangnetPlayer-v0.1.11.apk)
- [下载离线截图报告](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.11/FreeMangnetPlayer-v0.1.11-report.html)
- [完整源码](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.11/FreeMangnetPlayer-v0.1.11-source.zip)
- [本轮原始点检数据](evidence/performance-verification.json)
- [不限速做种端和独立客户端对照](evidence/performance-fixture.json)
- [上一版完整操作说明与 22 项验证](optimization-v0.1.10.md)

## 1. 四项要求的结果

| 要求 | 优化结果 | 本轮验证范围 |
| --- | --- | --- |
| 设置放到底部 | 底部导航新增设置入口，移除顶部设置按钮 | 检查按钮实际坐标、点击进入设置页并返回 |
| 解决解析、下载和首播慢 | 立即启动 P2P；TCP/uTP 自动选择，DHT、Tracker、局域网发现开启；会话和资源不限速；独立端口；缓存与预取优化；有效载荷诊断 | 不限速做种端和实际 APK 吞吐测量、真实播放、完整下载 SHA-256；公网对照另列 |
| 单独粘贴磁力解析 | 正常输入框支持原始磁力、HTML 实体和 URL 编码；32 位 base32 BTIH 统一为 40 位十六进制；避免整条 URL 重复解码 | 从输入框实际输入链接，冷启动解析，不向 APP 注入 .torrent 或文件列表 |
| 全屏和悬浮播放 | 沉浸式横屏全屏；系统画中画悬浮；全屏切换视频、倍速；悬浮保持播放器与任务 | 全屏和悬浮影片截图，系统 pinned 窗口检查，播放位置持续前进，返回播放页 |

## 2. 速度问题：已定位的配置与没有证实的原因

**旧 APK 没有设置 10 B/s 或 16 KB/s 的应用限速。旧测试脚本的做种端限制为 16 KB/s，该限制只在测试服务器上，未打包进 APK。旧测试证明功能能运行，但无法证明高速下载能力；本轮高速验证已换成所有传输限速为 0 的做种端。**

检查发现并修复了以下问题：

1. 无 Tracker 的磁力先同步等待外部元数据网站，阻塞 P2P 启动最多约 12 秒。现在先启动 P2P，8 秒后仍没有元数据才并行尝试 HTTP 备用，不等待网站成功才连接节点。
2. 出站 uTP 原先永久关闭。本轮实测发现模拟器限速做种端的 uTP 路径反复读超时，因此现在优先 TCP、保留入站 uTP；12 秒后仍无有效连接时自动启用出站 uTP。该策略选择传输路径，不限制字节速度。
3. 多个任务的原生会话使用默认监听配置；现在各会话使用独立自动端口，并保留 IPv4/IPv6。
4. 原生磁盘缓存原先关闭；现在每活动会话使用 16 MiB，缓存过期 1 秒。播放器先检查原生分片校验，再等待 CACHE_FLUSHED 确认已写入磁盘，避免误读尚未落盘的空洞；兼容与未选文件共享分片的 partfile。
5. 等待期间原先每 100 毫秒重复设置多个分片 deadline；现在每前进一个分片设置一次有界前向预取窗口，切换视频清理旧 deadlines。
6. 同一哈希旧任务原先会丢弃新粘贴链接中的 Tracker 和节点地址；现在合并这些参数并应用到活动会话，避免再次粘贴仍沿用缺失节点的旧链接。
7. 原会话桥对整条磁力 URL 解码，可能破坏参数内的转义。现在交给原生磁力解析器解码每个参数，保留 Tracker 与直接节点地址。

**以上是代码中可复现的配置问题，不能仅凭代码检查断言其中某项就是用户手机上 10 B/s 的唯一原因。实际低速还需看有效载荷、资源节点、DHT、网络与同链接客户端对照。**

## 3. 不限速实测数值

真实资源为 Blender《Big Buck Bunny》CC BY 3.0 影片转码片段，循环编码为 180 秒、约 8 Mbps 的视频。不是进度模拟、随机字节或伪造画面。做种端所有上传/下载限制均为 0，应用会话及任务的上传/下载限制也为 0。

| 指标 | 实测 |
| --- | --- |
| 视频总大小 | {fixture['size']:,} 字节 |
| 输入框粘贴后获取元数据 | {metadata['seconds']:.2f} 秒（包含自动化点击开销） |
| 首次真实播放 | {speed['first_play_seconds']:.2f} 秒（从点击解析开始，包含截图和 UI 操作开销） |
| 首播时已下载 | {speed['first_play_state']['downloaded']:,} / {speed['first_play_state']['total']:,} 字节 |
| 首播是否早于完整下载 | {'是' if speed['played_before_complete'] else '否；本轮高速文件已先下载完成，边下边播以回归测试为准'} |
| APK 采样峰值有效载荷速度 | {speed['peak_payload_bytes_per_second']:,} B/s，约 {speed['peak_payload_bytes_per_second']/1024/1024:.2f} MiB/s |
| 独立原生客户端下载平均速度 | {fixture['baseline']['average_bytes_per_second']/1024/1024:.2f} MiB/s |
| 独立客户端下载耗时 | {fixture['baseline']['seconds']:.2f} 秒 |
| 完整文件 SHA-256 | `{fixture['sha256']}` |

独立客户端位于 runner 主机，APP 位于 Android 模拟器，两者使用同一做种端，但模拟器网络转发和存储路径不同；不能将两个速度的差值直接视为 APP 损耗，也不能将本地速度视为所有公网资源速度。APK 峰值按原生 `downloadPayloadRate` 采样；该值是有效资源数据，不包含单纯的握手或 Tracker 字节。
'''
text += image('perf-04-unlimited-playing.png', '不限速真实影片播放及有效下载速度')
text += image('perf-05-native-diagnostics.png', '真实原生限速、节点、载荷和网络诊断')
text += '\n## 4. 公网纯磁力与网络环境对照\n\n'
plain = index['plain-hash-input-and-responsive-discovery']
text += f"本地测试资源去掉 Tracker 和直接节点后，仅输入纯哈希：APP 识别正确、节点发现时可返回；20 秒时元数据{'已取得' if plain['metadata_received'] else '未取得'}。隔离做种端不一定能在公网 DHT 被发现，**不能把输入识别通过当成任意纯磁力必定解析成功**。\n\n"
text += image('perf-10-plain-hash-discovery.png', '纯哈希输入后的实际节点发现状态，返回可用')
if public:
    b = public['native_baseline']
    bs = b.get('final_state', {})
    ps = public['state']
    text += f'''随后使用官方 Ubuntu LTS 安装镜像作公网对照，磁力哈希来自[官方种子]({b['torrent_url']})；两边均从纯磁力开始，未注入种子元数据。独立 Python 原生客户端和 APP 分别最多观察 75 秒。

| 环境 | 元数据 | DHT 节点 | 资源节点 | 有效接收 | 峰值有效速度 |
| --- | --- | --- | --- | --- | --- |
| 独立原生客户端，runner 网络 | {bs.get('metadata', False)} | {bs.get('dht_nodes', 0)} | {bs.get('peers', 0)} | {bs.get('payload_bytes', 0):,} B | {b.get('peak_payload_bytes_per_second', 0):,} B/s |
| APK，模拟器网络 | {ps.get('metadata', False)} | {ps.get('dhtNodes', 0)} | {ps.get('peers', 0)} | {ps.get('payloadBytes', 0):,} B | {public.get('peak_payload_bytes_per_second', 0):,} B/s |

该表是实际测量，收到 0 字节时不会标成公网下载成功。DHT 有节点不等于该资源已有可连接做种者；DHT 为 0 与没有资源节点也不是同一种状态。测试环境和用户手机不同，用户目前遇到的资源/手机网络尚未完成同链接复测。
'''
    text += image('perf-11-public-dht-resource.png', '官方公网纯磁力在 APP 中的实际结果')
else:
    text += '本轮公网基线未完成可比较记录，不能宣称公网纯磁力解析与下载问题已在用户网络解决。\n'
text += '''
## 5. 操作步骤和截图

### 5.1 底部设置

首页最下方点“设置”，进入存储地址、下载/缓存数量和默认搜索引擎配置；保存后返回首页。顶部保留搜索栏和引擎菜单。
'''
text += image('perf-01-bottom-settings.png', '设置入口位于首页底部导航')
text += image('perf-01b-settings.png', '实际打开的个性化设置页')
text += '''
### 5.2 粘贴并解析磁力

在首页“磁力链接输入框”粘贴完整链接，点击“解析资源”。解析完成后显示真实文件列表；选中文件可下载、播放或收藏。解析依赖提供元数据的节点，失败时可查看网络诊断或返回。
'''
text += image('perf-02-pasted-magnet.png', '从首页输入框实际输入测试磁力')
text += image('perf-03-real-metadata.png', '通过 P2P 得到真实视频文件信息')
text += image('perf-09-html-pasted-parsed.png', 'HTML 实体编码链接的冷启动解析结果')
text += '''
### 5.3 全屏播放

视频起播后点“全屏播放”，进入横屏沉浸播放。播放器提供暂停、拖动进度、全屏上方的切换视频与倍速按钮；点“退出全屏”或系统返回键恢复资源页。
'''
text += image('perf-06-fullscreen-film.png', '全屏播放器中实际影片画面')
text += f"\n全屏播放位置实际从 {full['position_before']} ms 前进到 {full['position_after']} ms，验证影片继续播放。\n"
text += '''
### 5.4 悬浮播放

播放中点“悬浮播放”，视频进入 Android 系统画中画窗口，可在其他界面上继续播放；正在播放时按 HOME 也会尝试进入画中画。点悬浮窗并使用系统展开控制返回播放器；拖动窗口可调整位置，系统关闭控制可关闭窗口。

需要 Android 8.0+、设备支持画中画，并允许该应用的系统画中画权限。此功能使用系统 PiP，不申请“显示在其他应用上层”的任意悬浮窗权限；厂商不支持 PiP 的电视可能无法使用。
'''
text += image('perf-07-floating-over-home.png', '系统 pinned 画中画窗口中的真实影片')
text += f"\n悬浮期间播放位置从 {pip['position_before']} ms 前进到 {pip['position_after']} ms，系统窗口状态为 pinned，播放器未因页面离开而释放。\n"
text += image('perf-07b-system-menu.png', '实际系统悬浮窗菜单中的展开与关闭控制')
text += image('perf-08-return-from-floating.png', '返回播放器后的真实影片')
text += '\n## 6. 逐项点检\n\n| 点检项 | 结果 |\n| --- | --- |\n'
for name in required:
    text += f'| {labels[name]} | 通过 |\n'
if args.regression_shots:
    regression = json.loads((Path(args.regression_shots) / 'optimization-verification.json').read_text(encoding='utf-8'))
    assert len(regression) == 22 and all(row.get('passed') is True for row in regression)
    shutil.copyfile(Path(args.regression_shots) / 'optimization-verification.json', evidence / 'performance-regression.json')
    text += f"\n[同一代码的 22 项回归测试](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/{args.regression_run})全部通过，包含未完成下载即播放、暂停/继续/取消、熄屏、单选/多选、视频切换、倍速/跳转、目录复用、排队与清空。该回归故意采用低于视频码率的限速做种端，用来保持未下载完的状态；它不参与本轮高速测量。[回归原始结果](evidence/performance-regression.json)。\n"
text += '''
## 7. 安装与验证边界

- v0.1.10 与本版沿用同一固定签名，可覆盖安装；更早的 v0.1.9 首次迁移仍需要先记录收藏/磁力，再卸载旧包。
- 速度没有承诺最低值或不限网络全程流畅；无有效节点或供给低于视频码率时，必须等待缓存。
- 本轮手机模拟器实测全屏和系统 PiP，未用手机截图冒充电视或平板实机。
- Android 15 的后台 dataSync 每日六小时限制、厂商省电和存储权限仍适用。没有更改系统限制来虚报后台能力。
- Git 提交原始对象检查 UTF-8、透明加密头、图片格式；GitHub CI 检查 Markdown 图片与渲染。离线 HTML 内嵌截图，可下载后直接用浏览器打开。
'''
Path('docs/performance-v0.1.11.md').write_text(text, encoding='utf-8')
Path('docs/usage-zh.md').write_text(text, encoding='utf-8')
readme = f'''# FreeMangnetPlayer

自用 Android 磁力播放器，应用名 **FreeMangnetPlayer**，包名 `com.freemangnetplayer`。

## v0.1.11 下载和实际报告

- [下载安装包](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.11/FreeMangnetPlayer-v0.1.11.apk)
- [操作步骤、真实截图和速度优化报告](docs/performance-v0.1.11.md)
- [离线 HTML 报告，下载后用浏览器打开](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.11/FreeMangnetPlayer-v0.1.11-report.html)
- [完整源码 ZIP](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.11/FreeMangnetPlayer-v0.1.11-source.zip)
- [原始点检记录](docs/evidence/performance-verification.json)

设置入口位于底部。支持关键词搜索、磁力解析、下载/缓存、收藏、多视频切换、倍速/跳转、全屏和系统 PiP 悬浮播放。APP 下载/上传不限速；本轮不限速做种实测峰值 **{speed['peak_payload_bytes_per_second']/1024/1024:.2f} MiB/s**，该值来自本地模拟器测试，不代表任意公网资源速度。公网纯磁力对照的实际结果和限制见报告。

v0.1.10 可以直接覆盖安装；v0.1.9 的首次固定签名迁移需卸载旧版，先记录收藏和磁力。Android 7.0+；PiP 需要 Android 8.0+ 和设备支持。电视/平板尚未实机验证。

## 编译

JDK 17、Android SDK 35、Gradle 8.9，执行 `gradle assembleDebug`。内核 jlibtorrent 1.2.19.0，播放器 AndroidX Media3 1.5.1。签名由 GitHub Actions secrets 提供，私钥不在仓库。[第三方许可](third-party/simple-torrent-android-LICENSE.txt)。

## 历史报告

- [v0.1.10 完整说明和 22 项实测](docs/optimization-v0.1.10.md)
- [v0.1.9 历史报告](docs/archive-v0.1.9.md)
'''
Path('README.md').write_text(readme, encoding='utf-8')
print('Built actual performance report with', len(required), 'functional checks')
