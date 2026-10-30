"""Publish verified v16 evidence, with unmet network requirements explicitly reported."""
import argparse,json,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path

p=argparse.ArgumentParser()
for name in ['library','preferences','public','native','performance','search','runs']:p.add_argument('--'+name,required=True)
a=p.parse_args();root=Path(__file__).resolve().parent.parent
def find(base,name):return next(Path(base).rglob(name))
def read(base,name):return json.loads(find(base,name).read_text(encoding='utf-8'))
lib=read(a.library,'library-verification.json');pref=read(a.preferences,'preferences-verification.json');pages=read(a.preferences,'pagination-verification.json')
public=read(a.public,'public-acceptance.json');native=read(a.native,'native-public-acceptance.json');perf=read(a.performance,'performance-verification.json');search=read(a.search,'real-search-comparison.json')
assert len(lib)==16 and all(r['passed'] for r in lib)
assert len(pref)>=11 and all(r['passed'] for r in pref)
assert len(pages)>=10 and all(r['passed'] for r in pages)
assert len(public)==11 and len(native)==10 and len(search)==6
throughput=next(r for r in perf if r['check']=='unlimited-app-throughput')
assert throughput['passed'] and throughput['peak_payload_bytes_per_second']>=2*1024*1024
tests=list(Path(a.preferences).rglob('TEST-*.xml'))
assert tests and sum(int(ET.parse(f).getroot().get('tests','0')) for f in tests)>=3
assert all(int(ET.parse(f).getroot().get('failures','0'))==0 and int(ET.parse(f).getroot().get('errors','0'))==0 for f in tests)
runs=json.loads(a.runs);url='https://github.com/liao-zc/FreeMangnetPlayer';v='v0.1.16'
keep={'docs/report.md','docs/usage-zh.md'};images=root/'docs/screenshots/current';images.mkdir(parents=True,exist_ok=True)
for base,file,prefix in [(a.library,'library-verification.json','library-'),(a.preferences,'preferences-verification.json','preferences-'),(a.public,'public-acceptance.json','public-'),(a.performance,'performance-verification.json','performance-'),(a.search,'real-search-comparison.json','search-')]:
 directory=find(base,file).parent
 for source in directory.glob('*.png'):
  if 'failed' in source.name or 'failure' in source.name:continue
  if prefix=='library-' and not source.name.startswith('library-'):continue
  assert source.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
  target=images/(prefix+source.name);shutil.copyfile(source,target);keep.add(target.relative_to(root).as_posix())
e=root/'docs/evidence';e.mkdir(exist_ok=True)
for name,data in [('library',lib),('preferences',pref),('pages',pages),('public',public),('native',native),('performance',perf),('search',search),('runs',runs)]:
 path=e/('current-'+name+'.json');path.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8');keep.add(path.relative_to(root).as_posix())
keep.add('docs/evidence/current-cleanup.json')
passed=sum(r['passed'] for r in public[:10]);rate=throughput['peak_payload_bytes_per_second']/1e6
text=f'''# FreeMangnetPlayer {v}：15 项需求点检报告

日期：2026-10-30。测试环境：Android 15 / API 35 x86_64 模拟器；安装实际 APK，并通过 UI 操作、播放器状态与磁盘数据校验。截图均来自运行中的 App 或独立浏览器。ARM 手机、平板及电视尚需真机验证。

- [ARM64 安装包]({url}/releases/download/{v}/FreeMangnetPlayer-{v}-arm64-v8a.apk)
- [离线图文报告]({url}/releases/download/{v}/FreeMangnetPlayer-{v}-report.html)
- [源码包]({url}/releases/download/{v}/FreeMangnetPlayer-{v}-source.zip)

## 验收结论

本轮字体、历史记录、分页、播放器信息栏及本地收藏功能有实际测试证据。不限速受控做种测试有效载荷峰值 **{rate:.2f}MB/s**，App 限速值为 0；公网十条资源实际起播 **{passed}/10**，{'达到' if passed>=9 else '**未达到**'} 90% 要求。受控测试不能证明任何公网资源都能维持最低 2MB/s。

受控影片首次起播 {throughput['first_play_seconds']:.2f} 秒，当时已下载 {100*throughput['first_play_state']['downloaded']/throughput['first_play_state']['total']:.1f}%；没有按固定百分比限制起播，但仍须等到所请求的真实分片可读。

字体/历史与队列截图使用做种端限速的受控素材，以便观察尚未完成的缓存；这不是 App 限速。不限速吞吐由另一轮独立测量验证，所有 App 下载/上传限速值均为 0。

## 按用户编号逐项点检

| 编号 | 要求及修改 | 验证结果与边界 |
| --- | --- | --- |
| 1 | 设置页“显示与字体”；小、标准、大、超大及 75%–175% 滑块；黑体、宋体、楷体与自定义 TTF/OTF 导入 | 四档及滑块持久化、三种实际字体加载通过；宋体和楷体随包提供 OFL 字体。自定义字体导入接口已实现，系统文件选择器尚未逐设备点检 |
| 2 | 搜索行显示大小，解析详情显示真实文件字节数 | 提取磁力 xl、结果行及详情页大小；受控网页 39 条均有大小；解析页采用种子文件元数据。网站不提供大小且元数据未取得时显示“等待元数据大小”，**尚未满足所有资源始终有精确大小**，不会填造数值 |
| 3 | 独立历史模块：搜索 50、播放 100、下载 50、缓存 30；名称、时间、播放进度或任务进度 | Android 测试验证各类上限、时间倒序、进度更新和清空；实际 UI 验证播放/缓存/下载记录及清空；入口为“播放 → 历史记录”。清空历史保留缓存、下载文件和收藏 |
| 4 | 搜索名称基准 14sp、深灰 #333333、行高 16.8sp，每屏 20 条 | 39 条分成 2 屏全部可访问；字体设置和 Android 系统字号继续生效。Android 使用 sp；不是硬编码物理像素 |
| 5 | 提升吞吐；播放器内显示时长、分辨率、大小和实时缓存百分比/进度条 | 受控不限速测试峰值 {rate:.2f}MB/s；真实读取的媒体信息显示通过。媒体头未取得前不能知道时长和分辨率；已读取信息持久化后重开可立即显示。**不能保证公网最低 2MB/s** |
| 6 | 资源节点问题；十条指定磁力实际测试 | 元数据、Peer、有效视频字节、首帧与速度均独立记录；按哈希保存可重连节点，扩大读取预取窗口，优先读取头部和尾部。公网起播 {passed}/10，{'达到 90%' if passed>=9 else '**未通过 90% 验收**'}。DHT 路由节点不是做种节点，未将元数据成功算作播放成功 |
| 7 | 只显示“搜索中”进度；减少丢失结果，隐藏引擎来源 | UI 隐藏连接网站/来源信息；扫描结果、详情、data-magnet、输入框及 HTML 中的磁力；受控浏览器结果集合偏差 0。六个真实源另有观测；**真实源结果偏差尚未全部达到 ≤10% 要求**。访问被阻拦或没有可核验结果时不认定达标 |
| 8 | 播放器右上角白色空心爱心；点击红色实心，再点击取消 | 实际点击和收藏存储添加/删除通过。按用户本轮答复维持本地使用；自建账户同步留待未来接入 |
| 9 | 搜索只展示名称、大小、资源类型和热度；隐藏网站/爬取元数据 | 界面已收敛为上述字段及播放/下载/收藏操作。类型从来源文字提取；来源没有评分时显示“未提供评分”，没有生成虚假热度 |
| 10 | 搜索显示文件名，隐藏裸磁力 | 显示结果标题、详情关联标题或 dn 文件名，磁力仅用于操作。受控两条异名详情解析通过；原网页及磁力都不含名称时无法准确得知真实文件名 |
| 11 | 当前页、总页、上一页/下一页及指定页跳转 | 结果屏总页数精确计算；网站页码从原网页提取，实际跳转到第 3 页再回第 1 页及跳转结果屏通过。网站没有总页数或目标页地址时只能显示已发现页数，不编造总页数和地址 |
| 12 | 关闭“查看本网页”验证提示 | 搜索结果 UI 已移除该入口和验证按钮 |
| 13 | 搜索未完成时停止跳动，显示“搜索未完成” | 超时/访问错误停止追加；保留已有结果与返回、重试，真实源错误截图及空页返回校验 |
| 14 | 收藏全选后下载，任务在下载列表可查 | 实际全选收藏、点击下载、查看下载任务及记录通过；按文件 so 选择保留所选目标 |
| 15 | 选择低清不再弹出“没有低清轨道” | 菜单仅显示已发现且能解码的低清轨道；选取明确轨道，支持恢复原画；720p→360p 实际播放通过。单轨文件只有原画选项，未实现转码 |

## 十条公网资源实际结果

每条冷启动测试最多等待 120 秒，清除上次缓存后启动实际播放器，至少播放到 500ms 且收到有效数据才计为起播成功。首帧后继续观测约 10 秒；数据不足不记通过。以下类别采用用户提供的分类，文件名称以实际取得的元数据为准。

| 类别 | 磁力哈希 | 元数据 | 节点 | 有效字节 | 峰值 MB/s | 首帧秒 | 起播 |
| --- | --- | --- | --- | --- | --- | --- | --- |
'''
for r in public[:10]:
 s=r['final'];text+=f"| {r['category']} | `{r['hash']}` | {'有' if s.get('metadata') else '无'} | {s.get('peers',0)} | {s.get('payloadBytes',0)} | {r['peakPayloadBytesPerSecond']/1e6:.3f} | {round(r['firstFrameSeconds'],2) if r['firstFrameSeconds'] is not None else '未起播'} | {'通过' if r['passed'] else '未通过'} |\n"
text+=f"\n同默认 Tracker 的独立客户端：{sum(r['final'].get('metadata',False) for r in native)}/10 取得元数据，{sum(r['final'].get('payloadBytes',0)>0 for r in native)}/10 收到有效视频数据。App 在本轮环境中也未收到目标资源数据；暂未定位为单一网络或程序原因，90% 起播要求仍未解决。\n"
text+='\n节点数量与速度为本轮环境观测，不等于资源在所有网络中不可用。独立 native 客户端配置同一默认 Tracker 清单另做 180 秒观测；运行时、连接方式和观测时间与 Android 不同，不能把它当成同设备的另一款播放器测试。完整数据见证据。\n'
text+='\n### 原指定磁力\n\n'
control=public[10];s=control['final'];text+=f"`{control['magnet']}`：元数据 {'有' if s.get('metadata') else '无'}，有效字节 {s.get('payloadBytes',0)}，起播 {'通过' if control['passed'] else '未通过'}。继续保留为固定验收资源。\n"
text+='\n## 与 Magnet_player 的对照范围\n\n对标仓库：https://github.com/zaidielykelee/Magnet_player 。此前其 APK 在 ARM 转译模拟器中无法稳定运行，尚未完成两款 App 的同设备起播和速度对照；本报告没有将独立 native 客户端冒充对标 App，也没有虚构对方播放画面。此项仍需可运行对标 App 的设备才能验收。\n\n## 六个真实搜索源\n\n| 引擎 | 浏览器磁力数 | App 磁力数 | 可比性与偏差 |\n| --- | --- | --- | --- |\n'
for r in search:
 state=f"{r['differenceRatio']*100:.1f}%" if r['comparable'] else '无法核验（访问失败或无有效基线）'
 text+=f"| {r['engine']} | {r['browserHashCount']} | {r['appHashCount']} | {state} |\n"
for i in range(1,7):text+=f'\n### 搜索源 {i} 对照截图\n\n![独立浏览器 {i}](screenshots/current/search-browser-{i}.png)\n\n![App 搜索 {i}](screenshots/current/search-real-search-app-{i}.png)\n'
text+='\n统计使用唯一磁力哈希集合，不能证明所有网页资源行都对应磁力。未把两个访问失败的零结果当成“结果一致”。\n'
text+='\n## 操作说明和 App 实际截图\n'
shots=[('字体设置','preferences-preferences-01-font-settings.png','从底部设置进入“显示与字体”，选择四档字号或拖动滑块。'),('宋体与大字号','preferences-preferences-02-song-large.png','选择宋体和大字号；黑体、楷体及自定义导入位于同一模块。'),('楷体','preferences-preferences-03-kai-font.png','选择楷体查看实际字体。'),('播放器收藏','preferences-preferences-04-filled-favorite.png','点播放器右上角爱心添加收藏；再点取消。'),('可选画质','preferences-preferences-05-supported-quality-menu.png','点内置画质按钮，仅列出已识别的可选轨道。'),('媒体信息及缓存进度','preferences-preferences-04-filled-favorite.png','点播放画面唤出控制栏，查看真实时长、分辨率、大小和缓存百分比。控制栏会自动隐藏。'),('手动保留缓存','preferences-preferences-06-media-cache-information.png','点击缓存后按钮变为已缓存；退出资源后从缓存视频入口重新打开。'),('收藏批量下载','preferences-preferences-07-selected-favorite-download.png','收藏页全选或多选后下载，进入下载列表查看。'),('播放历史','preferences-preferences-08-playback-history.png','播放页进入历史记录，切换搜索、播放、下载、缓存类别；支持删除记录和清空。'),('缓存重启入口','library-library-05-cache-after-restart.png','缓存后从“播放 → 缓存视频”重新打开指定文件。'),('20 条分页第 1 屏','preferences-local-results-1.png','显示文件名称、大小、类型、热度；分页栏提供上一屏、下一屏和跳页。'),('20 条分页第 2 屏','preferences-local-results-2.png','同一个网站页 39 条分为两屏，没有截断。'),('结果页跳转','preferences-local-jump-page-2.png','点“跳页”，输入目标页码并确认。'),('不限速实际播放','performance-perf-04-unlimited-playing.png','受控不限速做种，实际播放和有效载荷速度。')]
for title,name,guide in shots:
 assert (images/name).exists(),name
 text+=f'\n### {title}\n\n{guide}\n\n![{title}](screenshots/current/{name})\n'
text+='\n### 十条资源逐条截图\n'
for i in range(1,11):text+=f'\n![公网资源 {i}](screenshots/current/public-public-{i:02d}.png)\n'
text+='\n## 详细检查和测试入口\n\n| 检查项 | 结果 |\n| --- | --- |\n'
for r in lib+pref+pages:text+=f"| `{r['check']}` | 通过 |\n"
text+='\n回归流程按已完成子项记录；旧 library/吞吐流程曾在后续 UI 脚本失败，不能把整个旧流程标为通过。吞吐重试曾因多行输入框未完全清空而贴入残留字符，已改为全选后替换。最终专项流程状态以对应 Actions 链接为准。\n'
text+='\nAndroid 设备测试另校验字体真实加载、字号持久化、各类历史数量上限、时间倒序、播放进度及收藏存储切换。\n'
for key,run in runs.items():text+=f'\n- [{key} 测试过程]({url}/actions/runs/{run})\n'
text+='\n[字体/历史证据](evidence/current-preferences.json) · [分页](evidence/current-pages.json) · [十条公网资源](evidence/current-public.json) · [独立客户端](evidence/current-native.json) · [吞吐](evidence/current-performance.json) · [真实搜索](evidence/current-search.json)\n\n当前报告及截图替换旧版本资料；旧 Git 历史和 Release 保留。Markdown 使用 UTF-8 明文，发布后核对 GitHub 字节和渲染，另提供内嵌图片 HTML。\n'
for name in ['report.md','usage-zh.md']:(root/'docs'/name).write_text(text,encoding='utf-8')
(root/'README.md').write_text(f'''# FreeMangnetPlayer

安卓磁力播放器，当前版本 **{v}**。本地使用模式。

- [ARM64 下载]({url}/releases/download/{v}/FreeMangnetPlayer-{v}-arm64-v8a.apk)
- [十五项点检与实际截图](docs/report.md)
- [操作说明](docs/usage-zh.md)
- [离线图文报告]({url}/releases/download/{v}/FreeMangnetPlayer-{v}-report.html)
- [源码包]({url}/releases/download/{v}/FreeMangnetPlayer-{v}-source.zip)
- [第三方许可证](THIRD_PARTY_NOTICES.md)

新增显示与字体、四类历史、20 条分页及跳页、媒体缓存信息、爱心收藏与收藏多选下载。受控不限速测试峰值 {rate:.2f}MB/s；公网十条起播 {passed}/10，{'达到' if passed>=9 else '未达到'} 90% 要求。公网速度、缺失资源元数据及网站总页数不作虚假保证，见报告。
''',encoding='utf-8')
removed=[]
for name in subprocess.check_output(['git','ls-files'],text=True).splitlines():
 if name.startswith('docs/') and name not in keep:
  f=(root/name).resolve();assert f.is_relative_to(root/'docs')
  if f.is_file():f.unlink();removed.append(name)
(e/'current-cleanup.json').write_text(json.dumps(removed,ensure_ascii=False,indent=2),encoding='utf-8')
print('Verified current report generated; obsolete tracked documents/assets removed:',len(removed))
