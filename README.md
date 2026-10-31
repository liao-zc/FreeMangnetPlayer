# OmniPlayer

OmniPlayer 是面向 Android 手机、平板和电视的本地媒体播放器，包含 IPTV 直播与 BitTorrent 磁力播放功能。应用保留本地数据；是否能获取公网 BT 资源取决于种子、Peer、Tracker、网络和设备解码能力。

## 功能模块

- **电视直播**：添加多个 M3U/M3U8 地址或导入本地播放列表，按分组查看频道，收藏、观看历史、全屏和画中画。
- **磁力播放**：在应用内搜索或粘贴 magnet，查看文件列表，播放、缓存、下载并管理收藏/历史。
- **搜索和任务管理**：搜索结果按源站翻页并去重显示磁力链接；下载与缓存集中在任务页，搜索/播放/任务历史分开管理。
- **通用设置**：字体、背景、存储访问与软件更新入口。

## 架构和验收文档

- [软件架构图与包职责](docs/architecture.md)
- [完整历史需求清单和逐项点检报告](docs/requirements-checklist.md)
- [本轮重构报告](docs/report.md)
- [当前主界面截图](docs/screenshots/current-ui/01-home.png)
- [本轮 CI 实际验证截图](docs/screenshots/current-ci/library-stored-queue.png)
- [界面使用说明](docs/usage-zh.md)

## 构建信息

- Android application ID：`com.freemangnetplayer`（为覆盖旧版安装并保留数据而保留）
- 显示名称：OmniPlayer
- 当前版本：`v0.3.11`（更新流程优化版本；全量 CI 与发布待验证）。
- v0.3.10 已按 ABI 发布 arm64-v8a、armeabi-v7a、x86、x86_64 四种 APK；旧通用包包含四种 ABI 的 libtorrent 和 LibVLC 原生库，因此体积较大，ABI 拆包减少了单包内的无关原生库。
- [v0.3.10 Release 与 APK 下载](https://github.com/liao-zc/OmniPlayer/releases/tag/v0.3.10)，SHA-256 清单和大小见 Release 附件。CI 通用 APK 下载到 [`dist/OmniPlayer-debug.apk`](dist/OmniPlayer-debug.apk)。
- 模块：`:app`、`:ui-inspector`
- 核心技术：Kotlin、Jetpack Compose、Media3、LibVLC、libtorrent4j

## 重要说明

磁力文件列表出现并不代表已连接 Peer 或已经取得可播放视频分片。不同种子的首帧等待和速度可能差异很大；本项目不保证公网种子均可播放、不保证固定下载速度，也不声称支持所有编码组合。完整实测证据与未通过项请看全量点检报告。

## 开源组件

第三方组件与许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
## 一键回归与 APK 构建

在已安装 GitHub CLI、登录有仓库写入权限并能访问 GitHub 的 PowerShell 中运行：

```powershell
.\scripts\run_full_ci.ps1
```

脚本会无限退避重试推送当前分支，启动 `library.yml` 全量 Android/媒体库/偏好/搜索分页回归并等待完成；通用 CI APK 提取至 `dist/OmniPlayer-debug.apk`，逐项 CI 报告保存至 `artifacts/ci/<run-id>/`。发布时可加 `-PublishRelease`：CI 全部通过后会自动创建版本 Release 并上传四种 ABI APK、体积和 SHA-256 清单。失败测试的截图和状态文件保留在 Actions artifact 中。

### 软件更新

更新下载支持 HTTP Range 断点续传，并可在设置中暂停或继续。下载完成后，应用会保留安装包并提供系统安装确认入口；若暂不安装，可重新打开应用或在软件说明页选择“立即安装”。更新说明随版本发布并在更新确认页显示。Android 系统仍要求用户确认安装。