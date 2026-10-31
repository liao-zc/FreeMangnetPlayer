# OmniPlayer 重构与全功能点检报告

更新日期：2026-10-31

本轮已把磁力播放、IPTV 直播、通用能力和全局设置按职责拆分，并按历史需求建立了逐项功能清单。完整架构见 [架构说明](architecture.md)，逐项结果与证据边界见 [全量功能点检清单](requirements-checklist.md)。

## 本轮重构

- `core`：共用偏好、字体/背景、存储路径、更新、媒体格式和大小显示。
- `magnet`：搜索模型/UI/链接处理/网页桥接、BT 任务与 Peer、播放器、下载缓存和内容管理。
- `live`：播放列表读取、M3U 解析/频道数据、直播列表、源设置、播放器与直播历史收藏；三个 Activity 分文件维护。
- `settings`：全局设置页面。
- `MainActivity` 保留应用入口、导航和搜索 WebView 会话生命周期；持久化键、包名和外部 magnet 入口保持兼容。

## 本轮验证结果

| 检查 | 结果 |
|---|---|
| Manifest 与分域组件路径检查 | 通过，10 个 Android 组件路径由 CI 静态脚本核对 |
| Android APK 编译 | 通过，GitHub Actions `41162201200` |
| Android instrumentation 编译 | 通过，同一 Actions run |
| API 35 Android 模拟器 instrumentation | 通过，同一 Actions run：`connectedDebugAndroidTest` 成功 |
| GitHub Markdown UTF-8/渲染检查 | 通过，Actions `41162201204`；README 和使用说明成功渲染 |
| 工作区与远端内容核对 | 通过，54 个本轮变更路径逐文件 blob SHA 比对，0 个不一致；远端分支 `b90d974f` 后追加解码回退修复提交 `c26378e7` |
| `git diff --check` | 通过；保留既有 CRLF/LF 换行提示 |
| 本机 Gradle | 未启动：该 Windows 环境没有 JDK；使用远端 Java 17 + Android SDK 完成构建与测试 |

## 仍未通过的产品验收项

指定公网磁力的边下边播仍有明显首帧延迟。历史 Actions `41158983146` 测得一个指定资源约 0.1% 下载量时出首帧，但等待约 107 秒，有效速度约 175–736 KB/s。因此这不满足“快速起播”，也不能证明用户提供的十条资源达到 90% 成功率或最低 2 MB/s。可用 Peer/Tracker、做种状态、路由器和网络质量会改变结果，客户端无法凭算法为无种/无可用节点资源制造数据。

全功能表将每项标成通过、部分或未通过，并注明本轮实测、历史证据或尚缺的设备/公网条件。重构后的编译与 instrumentation 已通过，但这不代表所有历史 UI、所有直播源、真实公网种子和多机型体验均在本轮逐项实测通过。
## 2026-10-31 当前分支复核补记

| 检查 | 结果 |
|---|---|
| 最新架构拆分代码构建与 API 35 instrumentation | 通过：Actions [41162813195](https://github.com/liao-zc/OmniPlayer/actions/runs/41162813195) |
| GitHub Markdown 渲染 | 通过：Actions [41165205364](https://github.com/liao-zc/OmniPlayer/actions/runs/41165205364) |
| 当前分支全量媒体库/设置/搜索分页回归 | 四轮未通过：`41164610686`、`41165205509`、`41165851825`、`41166377619`。视频持续播放但控制层仍隐藏；诊断显示 `useController=true`、画质子控件存在、控制根节点 `GONE`。现已在点击处理和播放器首次绑定时显式设为 `VISIBLE`，第五轮 Actions `41166934395` 待验证。 |
| 当前分支截图采集 | 通过：Actions [41164612904](https://github.com/liao-zc/OmniPlayer/actions/runs/41164612904)，14 张手机模拟器截图已更新到 `docs/screenshots/current-ui/`。该截图任务与最新播放器控制条顺序调整只差一行，不影响截图中的设置和直播页面。 |
| 最新播放器控制条回归 | 进行中：Actions [41166934395](https://github.com/liao-zc/OmniPlayer/actions/runs/41166934395)，验证强制显示 Media3 控制容器修复。此前四轮未通过控制层检查，后续偏好/分页检查未执行。 |
| 公网磁力快速起播 | 未通过：历史受控/公网证据仍显示启动慢，无法证明十条用户磁力达到 90% 成功或 2 MB/s。 |

`41163294809` 已通过编译和 instrumentation 单元/组件测试，但后续综合脚本在播放器画质控件检查处停止，因此后续偏好与搜索分页阶段未执行。修复后的完整结果应以新的 CI run 为准。本地项目文件夹仍名为 `FreeMagnetPlayer`，安装包 ID `com.freemangnetplayer` 为兼容旧安装而保留；界面和仓库名称为 OmniPlayer。