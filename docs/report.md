# FreeMangnetPlayer v0.1.16：15 项需求点检报告

日期：2026-10-30。测试环境：Android 15 / API 35 x86_64 模拟器；安装实际 APK，并通过 UI 操作、播放器状态与磁盘数据校验。截图均来自运行中的 App 或独立浏览器。ARM 手机、平板及电视尚需真机验证。

- [ARM64 安装包](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.16/FreeMangnetPlayer-v0.1.16-arm64-v8a.apk)
- [离线图文报告](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.16/FreeMangnetPlayer-v0.1.16-report.html)
- [源码包](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.16/FreeMangnetPlayer-v0.1.16-source.zip)

## 验收结论

本轮字体、历史记录、分页、播放器信息栏及本地收藏功能有实际测试证据。不限速受控做种测试有效载荷峰值 **8.89MB/s**，App 限速值为 0；公网十条资源实际起播 **0/10**，**未达到** 90% 要求。受控测试不能证明任何公网资源都能维持最低 2MB/s。

受控影片首次起播 5.12 秒，当时已下载 6.0%；没有按固定百分比限制起播，但仍须等到所请求的真实分片可读。

字体/历史与队列截图使用做种端限速的受控素材，以便观察尚未完成的缓存；这不是 App 限速。不限速吞吐由另一轮独立测量验证，所有 App 下载/上传限速值均为 0。

## 按用户编号逐项点检

| 编号 | 要求及修改 | 验证结果与边界 |
| --- | --- | --- |
| 1 | 设置页“显示与字体”；小、标准、大、超大及 75%–175% 滑块；黑体、宋体、楷体与自定义 TTF/OTF 导入 | 四档及滑块持久化、三种实际字体加载通过；宋体和楷体随包提供 OFL 字体。自定义字体导入接口已实现，系统文件选择器尚未逐设备点检 |
| 2 | 搜索行显示大小，解析详情显示真实文件字节数 | 提取磁力 xl、结果行及详情页大小；受控网页 39 条均有大小；解析页采用种子文件元数据。网站不提供大小且元数据未取得时显示“等待元数据大小”，**尚未满足所有资源始终有精确大小**，不会填造数值 |
| 3 | 独立历史模块：搜索 50、播放 100、下载 50、缓存 30；名称、时间、播放进度或任务进度 | Android 测试验证各类上限、时间倒序、进度更新和清空；实际 UI 验证播放/缓存/下载记录及清空；入口为“播放 → 历史记录”。清空历史保留缓存、下载文件和收藏 |
| 4 | 搜索名称基准 14sp、深灰 #333333、行高 16.8sp，每屏 20 条 | 39 条分成 2 屏全部可访问；字体设置和 Android 系统字号继续生效。Android 使用 sp；不是硬编码物理像素 |
| 5 | 提升吞吐；播放器内显示时长、分辨率、大小和实时缓存百分比/进度条 | 受控不限速测试峰值 8.89MB/s；真实读取的媒体信息显示通过。媒体头未取得前不能知道时长和分辨率；已读取信息持久化后重开可立即显示。**不能保证公网最低 2MB/s** |
| 6 | 资源节点问题；十条指定磁力实际测试 | 元数据、Peer、有效视频字节、首帧与速度均独立记录；按哈希保存可重连节点，扩大读取预取窗口，优先读取头部和尾部。公网起播 0/10，**未通过 90% 验收**。DHT 路由节点不是做种节点，未将元数据成功算作播放成功 |
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
| 电影 | `B3D7179216306E63081AC0DDA84A714B183263F4` | 有 | 0 | 0 | 0.000 | 未起播 | 未通过 |
| 电影 | `D455858E4FCA306FCF315B1229CAF4AC4E065DBA` | 无 | 1 | 0 | 0.000 | 未起播 | 未通过 |
| 电影 | `6E00F1E081B67749B48141D38950BF6343BC5F10` | 有 | 1 | 0 | 0.000 | 未起播 | 未通过 |
| 动漫 | `665ED0FDA70B0AAFBE2ABE656EE0EA9DAE975389` | 有 | 1 | 0 | 0.000 | 未起播 | 未通过 |
| 动漫 | `840B33399D8C3AC57CF1CD9346ABE31236DDB4C1` | 无 | 0 | 0 | 0.000 | 未起播 | 未通过 |
| 动漫 | `571BB0140930A9B46DBA127D2F23B3EAF6B53530` | 有 | 0 | 0 | 0.000 | 未起播 | 未通过 |
| 剧集 | `FCF228066E11199A85F97AFC023A39BAA482C5B6` | 有 | 0 | 0 | 0.000 | 未起播 | 未通过 |
| 剧集 | `DA0AF5B9CE6BA74445F32576E7C90DD004A89A7F` | 有 | 0 | 0 | 0.000 | 未起播 | 未通过 |
| 剧集 | `C79263E0F308865FBF3A9613F1B2A8DEF1EBAAAA` | 无 | 0 | 0 | 0.000 | 未起播 | 未通过 |
| 纪录片 | `A56FB49D397F177938F8BC5BFBAA7D032DCD074A` | 有 | 0 | 0 | 0.000 | 未起播 | 未通过 |

同默认 Tracker 的独立客户端：1/10 取得元数据，0/10 收到有效视频数据。App 在本轮环境中也未收到目标资源数据；暂未定位为单一网络或程序原因，90% 起播要求仍未解决。

节点数量与速度为本轮环境观测，不等于资源在所有网络中不可用。独立 native 客户端配置同一默认 Tracker 清单另做 180 秒观测；运行时、连接方式和观测时间与 Android 不同，不能把它当成同设备的另一款播放器测试。完整数据见证据。

### 原指定磁力

`magnet:?xt=urn:btih:2362347540B27D8A4142AF2BBE0A071F26A3B7A1`：元数据 有，有效字节 0，起播 未通过。继续保留为固定验收资源。

## 与 Magnet_player 的对照范围

对标仓库：https://github.com/zaidielykelee/Magnet_player 。此前其 APK 在 ARM 转译模拟器中无法稳定运行，尚未完成两款 App 的同设备起播和速度对照；本报告没有将独立 native 客户端冒充对标 App，也没有虚构对方播放画面。此项仍需可运行对标 App 的设备才能验收。

## 六个真实搜索源

| 引擎 | 浏览器磁力数 | App 磁力数 | 可比性与偏差 |
| --- | --- | --- | --- |
| clg.im | 11 | 11 | 无法核验（访问失败或无有效基线） |
| BT搜索（dobt） | 0 | 0 | 无法核验（访问失败或无有效基线） |
| BD电影首发站 | 156 | 0 | 100.0% |
| Pomo | 60 | 198 | 230.0% |
| 美剧粉 | 0 | 0 | 无法核验（访问失败或无有效基线） |
| 高清族 | 48 | 0 | 无法核验（访问失败或无有效基线） |

### 搜索源 1 对照截图



### 搜索源 2 对照截图



### 搜索源 3 对照截图



### 搜索源 4 对照截图



### 搜索源 5 对照截图



### 搜索源 6 对照截图



统计使用唯一磁力哈希集合，不能证明所有网页资源行都对应磁力。浏览器基线取标题含关键词的详情，App 还遍历部分同源其他详情，因此 Pomo 的结果范围偏大；其他源的零结果仍需继续排查访问和详情适配。真实六源一致性尚未通过，不能把额外无关结果视为更好。未把两个访问失败的零结果当成“结果一致”。

## 操作说明和 App 实际截图

### 字体设置

从底部设置进入“显示与字体”，选择四档字号或拖动滑块。

![字体设置](screenshots/current/preferences-preferences-01-font-settings.png)

### 宋体与大字号

选择宋体和大字号；黑体、楷体及自定义导入位于同一模块。

![宋体与大字号](screenshots/current/preferences-preferences-02-song-large.png)

### 楷体

选择楷体查看实际字体。

![楷体](screenshots/current/preferences-preferences-03-kai-font.png)

### 播放器收藏

点播放器右上角爱心添加收藏；再点取消。

![播放器收藏](screenshots/current/preferences-preferences-04-filled-favorite.png)

### 可选画质

点内置画质按钮，仅列出已识别的可选轨道。

![可选画质](screenshots/current/preferences-preferences-05-supported-quality-menu.png)

### 媒体信息及缓存进度

点播放画面唤出控制栏，查看真实时长、分辨率、大小和缓存百分比。控制栏会自动隐藏。

![媒体信息及缓存进度](screenshots/current/preferences-preferences-04-filled-favorite.png)

### 手动保留缓存

点击缓存后按钮变为已缓存；退出资源后从缓存视频入口重新打开。

![手动保留缓存](screenshots/current/preferences-preferences-06-media-cache-information.png)

### 收藏批量下载

收藏页全选或多选后下载，进入下载列表查看。

![收藏批量下载](screenshots/current/preferences-preferences-07-selected-favorite-download.png)

### 播放历史

播放页进入历史记录，切换搜索、播放、下载、缓存类别；支持删除记录和清空。

![播放历史](screenshots/current/preferences-preferences-08-playback-history.png)

### 缓存重启入口

缓存后从“播放 → 缓存视频”重新打开指定文件。

![缓存重启入口](screenshots/current/library-library-05-cache-after-restart.png)

### 20 条分页第 1 屏

显示文件名称、大小、类型、热度；分页栏提供上一屏、下一屏和跳页。

![20 条分页第 1 屏](screenshots/current/preferences-local-results-1.png)

### 20 条分页第 2 屏

同一个网站页 39 条分为两屏，没有截断。

![20 条分页第 2 屏](screenshots/current/preferences-local-results-2.png)

### 结果页跳转

点“跳页”，输入目标页码并确认。

![结果页跳转](screenshots/current/preferences-local-jump-page-2.png)

### 不限速实际播放

受控不限速做种，实际播放和有效载荷速度。

![不限速实际播放](screenshots/current/performance-perf-04-unlimited-playing.png)

### 十条资源逐条截图

![公网资源 1](screenshots/current/public-public-01.png)

![公网资源 2](screenshots/current/public-public-02.png)

![公网资源 3](screenshots/current/public-public-03.png)

![公网资源 4](screenshots/current/public-public-04.png)

![公网资源 5](screenshots/current/public-public-05.png)

![公网资源 6](screenshots/current/public-public-06.png)

![公网资源 7](screenshots/current/public-public-07.png)

![公网资源 8](screenshots/current/public-public-08.png)

![公网资源 9](screenshots/current/public-public-09.png)

![公网资源 10](screenshots/current/public-public-10.png)

## 详细检查和测试入口

| 检查项 | 结果 |
| --- | --- |
| `default-original-quality` | 通过 |
| `quality-control-inside-player-with-no-transcoding-claim` | 通过 |
| `explicit-cache-records-files-before-completion` | 通过 |
| `partial-downloaded-video-auto-enters-queue` | 通过 |
| `next-control-switches-to-partial-video` | 通过 |
| `download-library-indexes-individual-files` | 通过 |
| `favorite-file-record` | 通过 |
| `cache-entry-discoverable-after-process-restart` | 通过 |
| `cached-file-reopens-and-plays` | 通过 |
| `existing-lower-resolution-track-selects-real-360p` | 通过 |
| `original-quality-restores-highest-track` | 通过 |
| `favorite-delete-keeps-cache-files` | 通过 |
| `batch-history-delete-keeps-cache` | 通过 |
| `single-cached-file-delete-preserves-other-files` | 通过 |
| `batch-cache-file-delete` | 通过 |
| `batch-download-file-deletion` | 通过 |
| `display-font-module-and-four-presets` | 通过 |
| `song-font-and-large-preset-persist` | 通过 |
| `kai-font-applied` | 通过 |
| `favorite-heart-adds-current-video` | 通过 |
| `favorite-heart-second-tap-removes` | 通过 |
| `actual-duration-resolution-size-and-cache-progress` | 通过 |
| `play-and-cache-history-created` | 通过 |
| `favorite-select-all-download-visible-in-library` | 通过 |
| `independent-history-module-and-timed-progress` | 通过 |
| `clear-single-history-keeps-other-records` | 通过 |
| `clear-all-history-keeps-favorites` | 通过 |
| `source-page-1-complete` | 通过 |
| `local-twenty-result-screen-1` | 通过 |
| `local-twenty-result-screen-2` | 通过 |
| `local-page-back-restores-first-screen` | 通过 |
| `source-page-2-complete` | 通过 |
| `source-page-3-complete` | 通过 |
| `previous-page-restores-all-results` | 通过 |
| `website-page-jump-three-and-one` | 通过 |
| `local-result-page-jump` | 通过 |
| `empty-page-and-back-responsive` | 通过 |

回归流程按已完成子项记录；旧 library/吞吐流程曾在后续 UI 脚本失败，不能把整个旧流程标为通过。吞吐重试曾因多行输入框未完全清空而贴入残留字符，已改为全选后替换。最终专项流程状态以对应 Actions 链接为准。

Android 设备测试另校验字体真实加载、字号持久化、各类历史数量上限、时间倒序、播放进度及收藏存储切换。

- [library 测试过程](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40986851277)

- [preferences 测试过程](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40990866522)

- [pages 测试过程](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40994181448)

- [public 测试过程](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40986864228)

- [native 测试过程](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40988375209)

- [performance 测试过程](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40988171757)

- [search 测试过程](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40994186837)

[字体/历史证据](evidence/current-preferences.json) · [分页](evidence/current-pages.json) · [十条公网资源](evidence/current-public.json) · [独立客户端](evidence/current-native.json) · [吞吐](evidence/current-performance.json) · [真实搜索](evidence/current-search.json)

当前报告及截图替换旧版本资料；旧 Git 历史和 Release 保留。Markdown 使用 UTF-8 明文，发布后核对 GitHub 字节和渲染，另提供内嵌图片 HTML。

## 本轮修复与真实验收进展（2026-10-30）

### 文件名、大小和分页：通过受控网页回归

[完整运行记录](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41002883131) 的 10 项检查全部通过。页面链接文字统一为“查看详情”，页脚为“页面总计 34.03GB”；App 仍显示各资源的真实名称，37 个直接链接各为 2.3GB，2 个详情资源各为 1.5MB。39 条全部取得，无遗漏。网站翻页、每屏 20 条、页码跳转及空页返回均通过。

原因修复：页面加载过程中的首次扫描可能尚无详情链接；扫描期间不能提前宣布搜索完成。现已等待页面加载完成和所有详情请求结束，再确认搜索完成。

以下是运行中的实际 App 截图，素材为受控搜索网页，用于验证名称和大小提取，不是公网影片播放截图：

![最新搜索结果第 1 屏](screenshots/current/preferences-local-results-1.png)
![最新搜索结果第 2 屏](screenshots/current/preferences-local-results-2.png)
![最新结果页跳转](screenshots/current/preferences-local-jump-page-2.png)

### 十条公网磁力：尚未通过

删除 DHT 无限等待后的[真实 APK 测试](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41000002632) 为 0/10 起播，10 条有效视频数据均为 0；其中 7 条取得元数据。文件列表不能代表收到视频。

本机 libtorrent 2.1.1 绑定 WLAN 的约 597 秒测试同样未取得十条资源的元数据和有效数据。三个 DHT 引导入口与两个 UDP Tracker 的直接探测均超时；公开 Ubuntu 对照种子通过 HTTPS Tracker 发现节点并收到 1,572,864 字节。这些结果说明存在可用 BT 路径，同时 UDP 发现入口仍需排查，不能把全部失败直接归因于文件或 APP 限速。

### 新版引擎：仍在修复回归，尚未发布

[独立分支](https://github.com/liao-zc/FreeMangnetPlayer/tree/improve/modern-bt-engine) 使用 libtorrent4j 2.1.0-39，Java 与四种架构 JNI 同版本，已成功编译和打包。新增不阻塞磁力启动的公共 Tracker 刷新与本地缓存；用累计接收字节判断进展，避免慢速数据被判为停滞。

受控做种测试发现元数据补充并替换句柄后连接未恢复、有效字节为 0，该回归尚未通过。已改为替换句柄后先经 TCP 重连，无连接时再恢复 uTP，正在[重新测试](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41003279966)。该分支尚未并入 main，新版本 APK 尚未发布。

验收已升级：十条必须全部在文件未下载完时渲染视频，并持续推进播放至少 20 秒。之前“90% 通过”或仅有播放状态的判据不再作为本任务的完成条件。

## 本次补充：发布时间、独立任务名额和输出脚本

### 搜索结果的资源时间

资源名称下显示来源提供的发布时间。读取资源行的 time 标签、明确的发布时间字段或详情页发布时间；保留“昨天”等来源原文，不把搜索时间或整页时间当作每条资源的发布时间。来源没有发布时间时显示“来源未提供”，不编造日期。

[发布时间回归](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41004109559) 的 10 项检查全部通过：受控页面 39 个资源，37 条日期为 2026-10-29，2 条详情页日期为 2025-03-01；名称、大小、20 条分页、页码跳转及空页返回同步通过。上面的三张搜索截图已替换为这次实际 App 截图。受控网站验证提取逻辑，不代表六个公网来源都提供日期。

### 最大下载数量不包含缓存

下载任务与缓存/播放任务分开计算、排队和恢复。默认同时下载上限为 5；缓存采用设置中的独立缓存任务上限。暂停、完成、取消和排队任务不计入活动名额。设置标签明确标注“不含缓存”和“单独计算”。

[Android 测试](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41004735689) 已通过，覆盖 5 个活动下载与 10 个活动缓存分别计数、排队缓存不挤占下载、暂停/完成/取消不占名额。第一次测试夹具未将 queued 设为 false，误把全部夹具当作排队任务；已修正夹具后重新通过，未放宽断言。

### PowerShell 格式化错误

用户提供的错误说明格式字符串引用了参数范围以外的索引。引用 {0} 至 {7} 需要八个格式参数；缺少完整原命令，不能进一步确定是参数遗漏还是表达式解析导致。这个输出错误本身不能证明 BT 连接或播放算法有问题。

新增 scripts/summarize_public_results.ps1：显式遍历 JSON 数组，以对象输出文件名、元数据、节点、有效下载字节和验收结果，避免位置格式索引。Windows PowerShell 已实际读取 public-acceptance.json 并输出 11 条记录，没有 FormatError。记录仍如实显示未通过，不会把输出修复算作播放修复。

### BT 仍在验证

现代引擎元数据替换后丢失 Tracker 的原因已定位：TorrentInfo 不保留顶层 Tracker，重新添加任务时必须显式恢复 URL 和 tier。实验分支现已恢复到新句柄，并以新句柄实际 Tracker 集合验证恢复数量。[最新受控回归](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41005592483) 正在执行，尚不能声明解决或发布验收版 APK。十条磁力仍要求全部实际渲染首帧、在文件未下载完成时起播并持续播放。

### 最新运行结果：受控起播通过，缓存后续检查失败

[现代引擎回归 41005802769](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41005802769) 已完成。Android 五项存储/字体/任务名额测试通过；受控 Episode-01.mp4 实际起播：总大小 12,590,316 字节，采样时已下载 1,354,314 字节，首帧时间戳非零，视频高度 180，播放进度 913ms，总时长 180 秒，有效传输字节 1,166,686。此采样证明受控文件未下载完成时能起播，不证明连续 20 秒无卡顿或十条公网磁力通过。metadataReplacements 为 0，不能用此样本证明元数据替换路径已恢复。

后续“全选后缓存记录至少两条”检查失败，整套回归仍未通过；已增加选择状态断言以及缓存失败的任务状态、记录和 UI 快照，继续定位。

[受控起播状态](evidence/modern-controlled-playback.json)

![现代引擎受控视频实际画面与画质菜单](screenshots/current/modern-controlled-playing.png)

画面为受控测试视频，不是用户十条电影的验收截图。

[最新十条磁力验收](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41005807792) 仍在执行 Android 逐条检查；独立 native 探测已经结束，十条均无元数据且有效视频字节为 0。native 工作流步骤成功仅表示探测脚本执行完成，不表示资源播放通过。

[缓存定位回归](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41006619134) 已启动。

### 持续排查：多文件选择与 Peer 数据供给

[选择状态回归 41006619134](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41006619134) 明确在“全选后两个 Episode 均被选中”处失败。代码检查发现 playFile 在准备/重试时覆盖多选，实验分支已保留显式选择；但[修复后的 41006843768](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41006843768) 仍在相同断言失败，当前修复不足以解决界面操作问题，不能标记缓存全选通过。

新增两个直接 Android 回归检查：重复播放准备保持多选，以及无选择时初始化当前文件；同时捕获弹窗点击全选前后的实际截图、超时 UI 和任务状态，继续定位界面点击与页面重建。

- [直接选择回归及完整功能检查](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41007551092)
- [弹窗截图与超时快照检查](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41007647713)

磁力诊断增加最多 16 个 Peer 的地址、客户端、拥有资源比例、下载字节、速度和连接标志，以及最多 20 条断连/错误事件，仅写入调试状态。这些字段帮助区分发现地址、完成握手和真正供给视频数据，不把短暂连接数当作播放成功。

[含 Peer 诊断的十条验收](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41007252723) 正在执行。前一轮 41005807792 也仍在运行，保留其独立结果，未因观察等待而重启或宣布失败。

再次检查对标仓库 zaidielykelee/Magnet_player，当前树仍只有 README.md，无法进行 BT 源码级比较；未将其他客户端探测冒充对标 App 的同设备效果。

### Tracker 恢复后的十条真实验收：仍为 0/10

[完整运行 41005807792](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41005807792) 已结束。十条分别冷启动检查，所有资源有效视频字节为 0、首帧时间戳为 0；7 条取得元数据，3 条未取得。两条最终显示种子数 1，但仍没有收到数据。Tracker 集合恢复数量不是起播证据。

| 资源哈希 | 元数据 | 最终 Peer / 种子 | 视频字节 | 结果 |
| --- | --- | --- | --- | --- |
| `b3d7179216306e63081ac0dda84a714b183263f4` | 有 | 0 / 0 | 0 | 未通过 |
| `d455858e4fca306fcf315b1229caf4ac4e065dba` | 无 | 1 / 0 | 0 | 未通过 |
| `6e00f1e081b67749b48141d38950bf6343bc5f10` | 有 | 1 / 0 | 0 | 未通过 |
| `665ed0fda70b0aafbe2abe656ee0ea9dae975389` | 有 | 0 / 0 | 0 | 未通过 |
| `840b33399d8c3ac57cf1cd9346abe31236ddb4c1` | 无 | 0 / 0 | 0 | 未通过 |
| `571bb0140930a9b46dba127d2f23b3eaf6b53530` | 有 | 1 / 1 | 0 | 未通过 |
| `fcf228066e11199a85f97afc023a39baa482c5b6` | 有 | 0 / 0 | 0 | 未通过 |
| `da0af5b9ce6ba74445f32576e7c90dd004a89a7f` | 有 | 0 / 0 | 0 | 未通过 |
| `c79263e0f308865fbf3a9613f1b2a8def1ebaaaa` | 无 | 0 / 0 | 0 | 未通过 |
| `a56fb49d397f177938f8bc5bfbaa7d032dcd074a` | 有 | 1 / 1 | 0 | 未通过 |

[逐条状态与采样记录](evidence/modern-public-acceptance.json)保留最终文件列表和每次采样的播放、节点、字节指标；完整未裁剪文件可从上述运行的 public-playback-acceptance 附件下载，避免在仓库重复保存每次采样的全部文件列表。固定第十一条 236234… 同样未起播。本轮结果仍不能证明资源在其他网络没有做种，也不能证明仅调整本机调度就能解决；带 Peer 数据和断连原因的新测试继续执行。

文件选择的截图已明确：全选时弹窗的文件勾选有效，关闭后选择恢复为单个当前视频。直接 Android 检查七项全部通过，包括重复 playFile 不覆盖多选，故仍需查找其他覆盖路径。新增有限长度的选择变化调用记录和 Activity 实例标识，用实际调用位置定位，而不是继续猜测。

### Peer 诊断和选择状态重建的实际发现

[Peer 诊断运行 41007252723](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41007252723) 已结束，十条仍无有效视频数据、无首帧。多条已握手连接自报 dht-spy/1.0，资源拥有比例 0、下载字节 0；另有 Transmission、qBittorrent、BitTorrent 连接以及大量 TCP/uTP 超时或对端重置。不能将这些节点与真正供给视频分片的节点混为一谈，也不能据本次测试推断所有网络都无做种。

新引擎返回 Peer 地址格式为 tcp://IP:port。元数据替换恢复逻辑以前仅按 IP:port 切割，未移除协议前缀，导致构造重连地址失败。实验分支已按 URI 解析 IPv4、IPv6 及协议前缀，保留地址和端口，拒绝非法端口和地址；新增三个 Android 回归测试。

[Peer 原始字段及断连记录摘要](evidence/modern-peer-diagnostics.json)保留每个地址观察到的客户端和数据比例，以及不同的错误事件。完整逐秒文件从对应 Actions 附件下载。

[选择调用追踪 41008640791](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41008640791) 在失败时只记录到 MainActivity.onCreate → TorrentTasks.initialize 从已保存状态载入 [4]；全选时的修改记录已消失，表明过程中发生任务/进程重建，尚不能仅凭此确定退出原因。新回归保存 logcat 和 Android process exit-info，进一步定位重建原因。

- [进程退出原因回归](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41009537644)
- [Peer 地址修复与功能回归](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41009900336)
- [修复后的十条磁力验收](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/41009904571)

以上新回归仍在运行，尚未认定修复通过或发布新版 APK。
