"""Publish only observed results, then remove obsolete tracked reports and patch scripts."""
import argparse,json,shutil,subprocess
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
parser=argparse.ArgumentParser();parser.add_argument('--shots',required=True);parser.add_argument('--run',required=True)
parser.add_argument('--regression',required=True)
a=parser.parse_args();root=Path(__file__).resolve().parent.parent;shots=Path(a.shots)
formats=json.loads((shots/'formats-verification.json').read_text(encoding='utf-8'))
pages=json.loads((shots/'pagination-verification.json').read_text(encoding='utf-8'))
comparison=json.loads((shots/'comparison-probe.json').read_text(encoding='utf-8'))
assert len(formats)==17 and all(x['passed'] for x in formats)
invalid=next(r for r in formats if r['fixture'].get('invalid'))
formats=[r for r in formats if not r['fixture'].get('invalid')]
assert len(pages)==5 and all(x['passed'] for x in pages)
target=root/'docs/screenshots/current';target.mkdir(parents=True,exist_ok=True)
for p in shots.glob('*.png'):
    assert p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n');shutil.copyfile(p,target/p.name)
left=Image.open(target/'compare-03-reference-resource.png').convert('RGB');right=Image.open(target/'compare-04-our-resource.png').convert('RGB')
width=540;height=max(int(left.height*width/left.width),int(right.height*width/right.width))
canvas=Image.new('RGB',(width*2,height+90),'white');draw=ImageDraw.Draw(canvas)
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',24)
draw.text((15,15),'Magnet_player：模拟器启动观测',font=font,fill='black');draw.text((width+15,15),'FreeMangnetPlayer：同一磁力',font=font,fill='black')
for x,im in [(0,left),(width,right)]:canvas.paste(im.resize((width,int(im.height*width/im.width))),(x,90))
canvas.save(target/'comparison.png')
e=root/'docs/evidence';e.mkdir(exist_ok=True)
# Publish relevant crash lines only, not unrestricted third-party application logs.
for key in ['reference_logcat','logcat']:
    comparison[key]='\n'.join(line for line in comparison.get(key,'').splitlines() if any(t in line for t in ['Fatal signal','FATAL EXCEPTION','UnsatisfiedLinkError','libndk_translation','libflutter','AndroidRuntime','SecurityException']) and not any(t in line.lower() for t in ['token','authorization','bearer']))
for name,value in [('formats',formats+[invalid]),('pagination',pages),('comparison',comparison)]:
    (e/('current-'+name+'.json')).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
own=next(x for x in comparison['observations'] if x['app']=='FreeMangnetPlayer');st=own['samples'][-1]['state']
ref_running=bool(comparison.get('reference_process'))
text=f'''# FreeMangnetPlayer v0.1.14 优化与实际验证报告

日期：2026-10-29。测试设备：Android 11 / API 30 Google APIs Pixel 2 x86_64 模拟器。对标安装包包含 ARM 库，本 APP 包含四种 ABI；模拟器结果不能代替普通手机、电视和平板的真机验收。

- [下载 APK](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-arm64-v8a.apk)
- [离线图文报告](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-report.html)
- [实际安装与测试记录](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/{a.run})

## 1. 固定磁力与尚未完成的验收

主验收磁力固定为 `magnet:?xt=urn:btih:2362347540B27D8A4142AF2BBE0A071F26A3B7A1`。以后不以其他电影替代这条资源的播放结果。格式测试使用公开授权的 Big Buck Bunny 片段，通过受控 `.torrent` 地址与做种端下载；它们只用于格式和数据读取验证。

本轮这条磁力的最终观测：元数据 **{st.get('metadata',False)}**；资源节点 **{st.get('peers',0)}**；有效下载 **{st.get('payloadBytes',0):,} 字节**；播放位置 **{st.get('position',0)} ms**；解码内核 **{st.get('decoder','未启动')}**。解析文件列表与实际播放分开验收：若无分片或画面，本报告不将其标为播放成功。

**尚未达到对标播放器的目标电影播放效果。**用户提供的手机截图确实展示了该片画面及三个“视频源节点”，说明不能把此前本 APP 无节点当成资源失效。参考 APK 在本模拟器中{'有进程，但实际播放需核对下方截图与记录' if ref_running else '未能保持运行'}，无法取得有效的同设备播放速度、首帧和连续卡顿对照。用户已选择提供播放地址或 `.torrent`，本报告生成时尚未收到实际地址或文件；需要取得后继续定位其播放源与 BT 发现入口的差异。没有编造三个可用播放源。

## 2. 对标观测与效果图

实际安装包的应用名与包名也在原始 badging 记录中，不能仅凭下载文件名认定与用户手机安装版本完全相同。

对标 APK：[官方仓库](https://github.com/zaidielykelee/Magnet_player)，`st_v1.5.0_9_20260510_211803.apk`。仓库只发布 README 和安装包，没有内核源码；没有复制闭源实现，也没有假定视频源一定是 BT Peer。

| 项目 | FreeMangnetPlayer | Magnet_player | 本轮结论 |
| --- | --- | --- | --- |
| 用户指定电影 | 见真实节点与分片观测 | 用户手机截图能播放 | 本 APP 该项仍待继续优化 |
| 同模拟器实测 | 已安装并测试 | 启动与界面见记录 | 无有效播放对照，不做速度排名 |
| 搜索分页 | 39 条首屏、17 条次页、空末页及返回全部核对 | 没有有效同设备分页数据 | 已修复本 APP 缺陷，不宣称胜过对方 |
| 视频格式 | 16 组容器/编码真实播放与 SHA-256 核对 | 未取得完整格式验收数据 | 保留双内核、选片、缓存等现有功能 |

下面两张图来自实际模拟器，同一磁力。若参考侧显示桌面，那是启动失败后的实际状态，不是其视频播放界面；此图不能作为本 APP 播放更好的证据。

![实际同环境观测对照](screenshots/current/comparison.png)

[原始对标与主资源观测](evidence/current-comparison.json)

## 3. 搜索分页与结果完整性

旧版没有提取分页导航，并仅抓取标题包含完整关键词的详情，确实会漏结果。新版：

1. 上一页、下一页固定在结果页顶部；从网站的 `rel=next`、下一页链接、分页区提取真实页面地址，没有靠猜页码拼 URL。
2. 每页读取全部直接磁力及正文详情链接，以四个并发详情请求控制负载，**没有结果条数上限**，不再用完整关键词过滤详情标题。
3. 相同的完整磁力字符串只显示一次；过滤的是导航、登录等页面链接，不是已发现的磁力。不同磁力参数不合并成一条。
4. 显示当前页、详情读取中数量和失败数量；返回上一页保留该页结果。无结果、加载或网站错误时仍保留返回键。
5. 网站未提供可识别下一页地址时明确提示，并可查看本页原网页；依赖 JavaScript 按钮而无导航链接的特殊网站不能假称已完成通用分页。跨站详情可能受浏览器 CORS 限制，失败会显示。

| 实际操作 | 核对结果 | 截图 |
| --- | --- | --- |
| 第一页含 37 条直接磁力及 2 条不同译名详情 | 39/39，通过，无完整关键词过滤 | [首屏](screenshots/current/pagination-1.png) |
| 下一页 | 17/17，通过 | [第二页](screenshots/current/pagination-2.png) |
| 空末页 | 0/0，通过，返回控件仍在 | [空末页](screenshots/current/pagination-3.png) |
| 返回上一页 | 17 条全部恢复，通过 | [返回](screenshots/current/pagination-back.png) |
| 返回主页 | 通过 | 见逐项记录 |

受控页面用于逐条比对预期集合，不代表六个公网网站在任意网络均可用。保留六个指定引擎和自定义设置，不新增引擎。

[逐条分页证据](evidence/current-pagination.json)

## 4. 主流格式与实际解码

识别的视频扩展名：MP4、M4V、MKV、WebM、AVI、MOV/QT、TS、MTS/M2TS/M2T、MPEG/MPG/MPE、VOB、FLV/F4V、WMV/ASF、RM/RMVB、OGV/OGG、3GP/3G2、DivX、MXF、MPV、H.264、H.265/HEVC 裸流。

Media3 优先处理常用容器；AVI、WMV/ASF、RM/RMVB、MPEG/VOB、FLV、OGV 等使用 LibVLC。Media3 的容器/解码错误会尝试一次 LibVLC，失败后显示错误并停止自动重试，避免两个内核来回切换。两者共用 Media3 Player 接口和内置控制条、倍速、跳转、全屏、画中画操作，切换时释放旧播放器。

**扩展名识别不代表所有编码组合都能播放。**以下列出本轮真实测试；音视频编码、清晰度及硬件能力仍有区别，未测试的组合不写成通过。加密 DRM、光盘镜像也不能按普通视频文件承诺播放。

| 实际文件容器/编码组合 | 内核 | 视频播放 | 完整下载 SHA-256 | 实际画面 |
| --- | --- | --- | --- | --- |
'''
for r in formats:
    f=r['fixture'];text+=f"| {f['extension']} · {f['video_codec']} / {f['audio_codec']} | {r['state'].get('decoder','')} | 通过 | 一致 | [截图](screenshots/current/format-{f['extension']}.png) |\n"
text+='''
![实际 MP4 播放](screenshots/current/format-mp4.png)

![实际 RM 播放](screenshots/current/format-rm.png)

[格式逐项数据](evidence/current-formats.json)

损坏 MP4 专项：显示播放错误并停止自动重试，原进程保持不变；不会把没有轨道的 VLC Playing 事件计为播放成功。

![损坏文件实际错误界面](screenshots/current/format-broken.png)

## 5. 修改建议的采纳与边界

| 建议 | 本轮处理 |
| --- | --- |
| 元数据、Peer、有效分片、起播分开统计 | 增加连接阶段、元数据/Peer/首个已校验分片/起播耗时及再次缓冲次数；未观察到记 -1 |
| 文件存在不能当作分片可读 | 保留原有按偏移校验分片、等待写盘、取消、跳转优先级和读取超时；格式下载另做完整 SHA-256 核对 |
| Android 15 长后台服务限制 | 已有 onTimeout 保存任务与停止引擎；新增时限停止原因记录并撤销前台通知。没有改服务类型绕过限制，本轮未完成六小时真机实测 |
| 两个内核统一接口与生命周期 | 共用 Player 接口；增加错误回退，保留旧实例身份判断和释放逻辑；本机 Range 地址现在使用正确容器扩展名 |
| HTTP 安全与元数据 | 优先 HTTPS，并保留 BTIH 校验；用户自定义 HTTP、旧 Tracker 与本机流仍需要 HTTP，因此没有以固定域名白名单破坏这些功能 |
| BT 引擎适配层 | 保留已有 TorrentTasks/TorrentSession 所有权与 nativeLock，页面不直接新建 JNI Session；未盲目升级 Java/JNI 依赖 |
| 全 ABI 实际验收 | 仍提供四 ABI；模拟器测试不能声称 ARM 真机、电视和平板全部运行验证完成 |

本轮没有用“下载完成”替代“播放成功”，没有将目标电影的 0 Peer 归咎于资源失效，也没有承诺任意磁力都能立即播放。完整文件哈希成功的受控素材与主电影的公网状态分别报告。

## 安装包体积优化

v0.1.13 通用包包含四种 CPU 架构，约 250 MB，比参考 ARM 安装包大。本版另提供单 ABI 安装包，ARM64 手机、电视和平板优先下载 ARM64 版；旧 ARMv7 设备使用 ARMv7 版。保留通用包供其他架构使用，签名、应用名称和功能保持一致。

- [ARM64 版](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-arm64-v8a.apk)
- [ARMv7 版](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-armeabi-v7a.apk)
- [四 ABI 通用包](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14.apk)

## 6. 仓库清理与文档可读性

删除旧版本说明、旧截图和旧证据，删除一次性源码替换脚本；保留现行代码、原始许可证、当前报告证据和可重复测试。旧版本仍可从 Git 历史及 Releases 查看。当前报告与操作说明为 UTF-8 明文；验证 Git 对象、图片签名、相对链接与 GitHub Markdown 渲染，并提供截图内嵌离线 HTML。

GitHub 通知由个人账号设置控制。本项目没有新增发送邮件的工作流；平台 Actions 邮件可在 https://github.com/settings/notifications 调整。
'''
regdir=Path(a.regression)
reg=[r for r in json.loads((regdir/'player-revision-verification.json').read_text(encoding='utf-8')) if 'passed' in r]
assert len(reg)==21 and all(r['passed'] for r in reg)
(e/'current-regression.json').write_text(json.dumps(reg,ensure_ascii=False,indent=2),encoding='utf-8')
for p in regdir.glob('revision-*.png'):
    if p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):shutil.copyfile(p,target/('reg-'+p.name))
text+='\n## 7. 播放与缓存回归点检\n\nAndroid 15 / API 35 模拟器实际执行 [回归工作流](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40848325867)，21 项通过。受控做种素材用于验证生命周期与下载行为，不代替指定电影。\n\n| 检查项 | 结果 |\n| --- | --- |\n'
for r in reg:text+=f"| `{r['check']}` | 通过 |\n"
text+='\n实际操作截图：\n\n'
for label,name in [('文件列表与选片','02-real-file-list'),('复制目标文件磁力','00b-file-magnet-copy'),('内置播放、暂停、倍速、跳转与进度条','03-integrated-controls'),('全屏','04-fullscreen-icons'),('悬浮画中画','05-real-pip'),('切换第二个文件','06-second-video'),('保留显式缓存','07-explicit-cache'),('缓存数量限制','07b-cache-limit'),('新资源清理临时缓存','08-new-resource'),('RealMedia 播放','09c-realmedia'),('损坏文件错误反馈','09b-media-error'),('重试与返回','11-retry-responsive')]:
    text+=f'### {label}\n\n![{label}](screenshots/current/reg-revision-{name}.png)\n\n'
text+='\n[逐项状态与测量数据](evidence/current-regression.json)\n\n时间指标采用约 500 ms 状态轮询及开始播放事件；不是精确视频首帧计时。短暂 Peer 连接可能未采到，出现已下载数据但 Peer 耗时为 -1 不代表没有连接。\n'
(root/'docs/report.md').write_text(text,encoding='utf-8');(root/'docs/usage-zh.md').write_text(text,encoding='utf-8')
(root/'README.md').write_text('''# FreeMangnetPlayer

个人使用的安卓磁力播放器，当前版本 **v0.1.14**。

- [下载 APK](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-arm64-v8a.apk)
- [实际验证与对标报告](docs/report.md)
- [离线图文报告](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-report.html)
- [操作说明](docs/usage-zh.md)
- [源码包](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-source.zip)
- [许可证与第三方声明](THIRD_PARTY_NOTICES.md)

包含六个指定搜索引擎、自定义设置、搜索分页、磁力解析、播放/下载/收藏、后台任务和双播放器解码。

**指定电影的资源发现与实际起播仍待解决，同设备对标需要用户提供播放地址或 .torrent。**不能用格式测试成功替代该电影成功，详见报告。
''',encoding='utf-8')
keep_scripts={'build_current_report.py','build_offline_report.py','browser_baseline.py','capture_usage_screenshots.py','check_docs.py','check_repository.py','compare_reference_player.py','git_plaintext_filter.py','magnet_hash.py','optimization_fixture.py','performance_fixture.py','player_revision_fixture.py','player_test_controls.py','public_network_probe.py','publish_optimization_report.py','test_resource.py','torrent_fixture.py','user_network_probe.py','verify_optimization.py','verify_performance.py','verify_player_revision.py','verify_real_app.py','verify_search_pages.py','verify_video_formats.py','verify_published_docs.py','video_format_fixture.py','search_page_fixture.py','inspect_user_torrent.py','probe_user_metadata.py'}
tracked=subprocess.check_output(['git','ls-files'],cwd=root,text=True).splitlines();removed=[]
for relative in tracked:
    old_doc=relative.startswith('docs/') and relative not in ['docs/report.md','docs/usage-zh.md'] and not relative.startswith(('docs/screenshots/current/','docs/evidence/current-'))
    old_script=relative.startswith('scripts/') and Path(relative).name not in keep_scripts
    if old_doc or old_script:
        path=(root/relative).resolve();assert path.is_relative_to(root) and path.is_file(),path;path.unlink();removed.append(relative)
(e/'current-cleanup.json').write_text(json.dumps(removed,ensure_ascii=False,indent=2),encoding='utf-8')
print('Current reports generated; obsolete tracked files removed:',len(removed))
