# FreeMangnetPlayer v0.1.14 优化与实际验证报告

日期：2026-10-29。测试设备：Android 11 / API 30 Google APIs Pixel 2 x86_64 模拟器。对标安装包包含 ARM 库，本 APP 包含四种 ABI；模拟器结果不能代替普通手机、电视和平板的真机验收。

- [下载 APK](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-arm64-v8a.apk)
- [离线图文报告](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.14/FreeMangnetPlayer-v0.1.14-report.html)
- [实际安装与测试记录](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40852177396)

## 1. 固定磁力与尚未完成的验收

主验收磁力固定为 `magnet:?xt=urn:btih:2362347540B27D8A4142AF2BBE0A071F26A3B7A1`。以后不以其他电影替代这条资源的播放结果。格式测试使用公开授权的 Big Buck Bunny 片段，通过受控 `.torrent` 地址与做种端下载；它们只用于格式和数据读取验证。

本轮这条磁力的最终观测：元数据 **True**；资源节点 **0**；有效下载 **0 字节**；播放位置 **0 ms**；解码内核 **LibVLC**。解析文件列表与实际播放分开验收：若无分片或画面，本报告不将其标为播放成功。

**尚未达到对标播放器的目标电影播放效果。**用户提供的手机截图确实展示了该片画面及三个“视频源节点”，说明不能把此前本 APP 无节点当成资源失效。参考 APK 在本模拟器中未能保持运行，无法取得有效的同设备播放速度、首帧和连续卡顿对照。用户已选择提供播放地址或 `.torrent`，本报告生成时尚未收到实际地址或文件；需要取得后继续定位其播放源与 BT 发现入口的差异。没有编造三个可用播放源。

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
| mp4 · libx264 / aac | Media3 | 通过 | 一致 | [截图](screenshots/current/format-mp4.png) |
| mkv · libx265 / aac | Media3 | 通过 | 一致 | [截图](screenshots/current/format-mkv.png) |
| webm · libvpx-vp9 / libopus | Media3 | 通过 | 一致 | [截图](screenshots/current/format-webm.png) |
| avi · mpeg4 / libmp3lame | LibVLC | 通过 | 一致 | [截图](screenshots/current/format-avi.png) |
| flv · flv / libmp3lame | LibVLC | 通过 | 一致 | [截图](screenshots/current/format-flv.png) |
| wmv · wmv2 / wmav2 | LibVLC | 通过 | 一致 | [截图](screenshots/current/format-wmv.png) |
| mpg · mpeg2video / mp2 | LibVLC | 通过 | 一致 | [截图](screenshots/current/format-mpg.png) |
| ts · libx264 / aac | Media3 | 通过 | 一致 | [截图](screenshots/current/format-ts.png) |
| m2ts · mpeg2video / ac3 | LibVLC | 通过 | 一致 | [截图](screenshots/current/format-m2ts.png) |
| mov · libx264 / aac | Media3 | 通过 | 一致 | [截图](screenshots/current/format-mov.png) |
| 3gp · mpeg4 / aac | Media3 | 通过 | 一致 | [截图](screenshots/current/format-3gp.png) |
| rm · rv20 / real_144 | LibVLC | 通过 | 一致 | [截图](screenshots/current/format-rm.png) |
| ogv · libtheora / libvorbis | LibVLC | 通过 | 一致 | [截图](screenshots/current/format-ogv.png) |
| av1.mp4 · libaom-av1 / aac | Media3 | 通过 | 一致 | [截图](screenshots/current/format-av1.mp4.png) |
| vp8.webm · libvpx / libvorbis | Media3 | 通过 | 一致 | [截图](screenshots/current/format-vp8.webm.png) |
| h263.3gp · h263 / aac | Media3 | 通过 | 一致 | [截图](screenshots/current/format-h263.3gp.png) |

![实际 MP4 播放](screenshots/current/format-mp4.png)

![实际 RM 播放](screenshots/current/format-rm.png)

[格式逐项数据](evidence/current-formats.json)

AV1 的自动测试确认播放位置推进、完整文件哈希一致，但截取时视频区域为黑色，尚未取得可见首帧证据；此项不能作为画面正确的完整验收。其他格式的画面见各自截图。


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

## 7. 播放与缓存回归点检

Android 15 / API 35 模拟器实际执行 [回归工作流](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40848325867)，21 项通过。受控做种素材用于验证生命周期与下载行为，不代替指定电影。

| 检查项 | 结果 |
| --- | --- |
| `metadata-fallback-preserves-trackers-and-downloads` | 通过 |
| `file-magnet-system-clipboard-roundtrip` | 通过 |
| `copied-magnet-restores-selected-file` | 通过 |
| `default-maximum-downloads-five` | 通过 |
| `pasted-magnet-real-metadata` | 通过 |
| `stream-start-below-twenty-percent` | 通过 |
| `all-player-controls-inside-video` | 通过 |
| `embedded-pause-play-speed-and-seek` | 通过 |
| `playback-time-duration-and-scrubbable-progress` | 通过 |
| `fullscreen-embedded-small-icons` | 通过 |
| `pip-continues-and-restores` | 通过 |
| `switch-file-releases-previous-temporary-bytes` | 通过 |
| `explicit-cache-survives-next-resource` | 通过 |
| `cache-limit-preserves-explicit-cache` | 通过 |
| `new-resource-clears-previous-unretained-cache` | 通过 |
| `temporary-cleanup-and-explicit-retention-after-restart` | 通过 |
| `no-crash-or-process-restart-during-playback-stress` | 通过 |
| `realmedia-native-decoder-real-playback` | 通过 |
| `realmedia-embedded-pause-seek-speed-fullscreen` | 通过 |
| `invalid-media-shows-error-without-process-restart` | 通过 |
| `magnet-retry-and-back-remain-responsive` | 通过 |

实际操作截图：

### 文件列表与选片

![文件列表与选片](screenshots/current/reg-revision-02-real-file-list.png)

### 复制目标文件磁力

![复制目标文件磁力](screenshots/current/reg-revision-00b-file-magnet-copy.png)

### 内置播放、暂停、倍速、跳转与进度条

![内置播放、暂停、倍速、跳转与进度条](screenshots/current/reg-revision-03-integrated-controls.png)

### 全屏

![全屏](screenshots/current/reg-revision-04-fullscreen-icons.png)

### 悬浮画中画

![悬浮画中画](screenshots/current/reg-revision-05-real-pip.png)

### 切换第二个文件

![切换第二个文件](screenshots/current/reg-revision-06-second-video.png)

### 保留显式缓存

![保留显式缓存](screenshots/current/reg-revision-07-explicit-cache.png)

### 缓存数量限制

![缓存数量限制](screenshots/current/reg-revision-07b-cache-limit.png)

### 新资源清理临时缓存

![新资源清理临时缓存](screenshots/current/reg-revision-08-new-resource.png)

### RealMedia 播放

![RealMedia 播放](screenshots/current/reg-revision-09c-realmedia.png)

### 损坏文件错误反馈

![损坏文件错误反馈](screenshots/current/reg-revision-09b-media-error.png)

### 重试与返回

![重试与返回](screenshots/current/reg-revision-11-retry-responsive.png)


[逐项状态与测量数据](evidence/current-regression.json)

时间指标采用约 500 ms 状态轮询及开始播放事件；不是精确视频首帧计时。短暂 Peer 连接可能未采到，出现已下载数据但 Peer 耗时为 -1 不代表没有连接。

## 8. 同关键词搜索与立即起播核查（2026-10-29）

用户只有固定磁力，没有 `.torrent`。本轮采用同关键词 `Avengers`（复仇者联盟）搜索对照，不再以提供种子作为前提。[实际工作流](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40856458567)。

### 起播条件核查

- `PlayerActivity` 在取得视频文件列表后创建播放器，不检查下载百分比。
- Media3 起播缓冲设置为 300 ms；LibVLC 网络及文件缓冲设置为 300 ms。这些值是播放器时间缓冲设置，不是固定文件缓存比例，也不是端到端起播时间保证。
- `TorrentFileDataSource` 按读取偏移等待已校验并写盘的分片，跳转时调整分片期限；视频头部和尾部索引优先请求，不等完整下载。
- 既有受控回归在约 **4.57%** 下载量时起播，但从任务开始到播放事件约 **20.7 秒**，不能标为“立马播”。指定电影上次仍为 **0 视频字节**，同样不能标为通过。

### 真实搜索与参考 APP 启动结果

两个版本使用相同关键词。只有能取得相同 BTIH 的实际搜索结果并播放，才能比较同资源起播；不同版本或不同节点的数据不可直接作速度排名。渲染尝试为默认、关闭 Impeller、软件渲染，均使用原 APK 的启动参数，没有修改参考安装包。

### Android API 30

| 参考 APP 渲染尝试 | 保持进程 |
| --- | --- |
| default | 未保持运行 |
| opengl | 未保持运行 |
| software | 未保持运行 |

参考搜索未完成：`Reference process exits before usable UI on this emulator.`。此环境不能验证双方实际播放差异。

| 本 APP 实际请求引擎 | 实际报告引擎 | 磁力结果数量 |
| --- | --- | --- |
| clg.im | clg.im | 12 |
| BT搜索（dobt） | BT搜索（dobt） | 0 |
| BD电影首发站 | BD电影首发站 | 674 |
| Pomo | Pomo | 450 |
| 美剧粉 | 美剧粉 | 0 |
| 高清族 | 高清族 | 223 |

[实际状态、UI 与诊断证据](evidence/current-same-search-api30.json)

![API 30 search-ours-0 实际截图](screenshots/current/same-search-api30-search-ours-0.png)

![API 30 search-ours-1 实际截图](screenshots/current/same-search-api30-search-ours-1.png)

![API 30 search-ours-2 实际截图](screenshots/current/same-search-api30-search-ours-2.png)

![API 30 search-ours-3 实际截图](screenshots/current/same-search-api30-search-ours-3.png)

![API 30 search-ours-4 实际截图](screenshots/current/same-search-api30-search-ours-4.png)

![API 30 search-ours-5 实际截图](screenshots/current/same-search-api30-search-ours-5.png)

![API 30 search-reference-default 实际截图](screenshots/current/same-search-api30-search-reference-default.png)

![API 30 search-reference-opengl 实际截图](screenshots/current/same-search-api30-search-reference-opengl.png)

![API 30 search-reference-software 实际截图](screenshots/current/same-search-api30-search-reference-software.png)


**立即起播及同资源播放对照尚未通过，不能把本轮搜索执行完成写成播放优化完成。**如参考 APP 仍无法运行，此测试环境缺少可用对标客户端，需要在可运行参考 APP 的安卓设备继续验证。

### 结果解释与测试限制

上述数量仅为提取到的磁力条数，不能解读为全部符合 `Avengers` 的不同影片，更不能解读为可播放数。BD 首发站在采样时仍有 3 个详情页面读取中；dobt 为超时，美剧粉为无结果。没有两款 APP 的同一 BTIH 播放数据，因此尚不能作播放速度排名。

首次脚本未正确持久化引擎选择，得到重复默认引擎结果；复测已通过真实设置文件修正，各请求引擎与实际引擎一致。Android 15 初次环境另外出现 Launcher ANR，遮挡界面，其搜索观测已排除，不计通过。

![Android 15 测试环境实际 ANR](screenshots/current/reference-api35-launcher-anr.png)

[Android 15 参考启动诊断与无效数据排除记录](evidence/current-reference-api35.json)

本轮没有修改播放器代码或发布新 APK，仅增加可重复对照脚本和真实报告。**指定电影立即起播尚未实现**；现有环境无法运行参考 APP，需要能运行它的安卓设备才能继续取得同资源播放差异。并未要求用户必须提供 `.torrent`。
