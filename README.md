# OmniPlayer

OmniPlayer 是面向 Android 手机、平板和电视的本地媒体播放器，包含 IPTV 直播与 BitTorrent 磁力播放功能。应用保留本地数据；是否能获取公网 BT 资源取决于种子、Peer、Tracker、网络和设备解码能力。

## 功能模块

- **电视直播**：添加多个 M3U/M3U8 地址或导入本地播放列表，按分组查看频道，收藏、观看历史、全屏和画中画。
- **磁力播放**：在应用内搜索或粘贴 magnet，查看文件列表，播放、缓存、下载并管理收藏/历史。
- **通用设置**：字体、背景、存储访问与软件更新入口。

## 架构和验收文档

- [软件架构图与包职责](docs/architecture.md)
- [完整历史需求清单和逐项点检报告](docs/requirements-checklist.md)
- [本轮重构报告](docs/report.md)
- [当前主界面截图](docs/screenshots/current-ui/01-home.png)
- [界面使用说明](docs/usage-zh.md)

## 构建信息

- Android application ID：`com.freemangnetplayer`（为覆盖旧版安装并保留数据而保留）
- 显示名称：OmniPlayer
- 当前发布版本：`v0.3.0`（versionCode `28`）
- 模块：`:app`、`:ui-inspector`
- 核心技术：Kotlin、Jetpack Compose、Media3、LibVLC、libtorrent4j

## 重要说明

磁力文件列表出现并不代表已连接 Peer 或已经取得可播放视频分片。不同种子的首帧等待和速度可能差异很大；本项目不保证公网种子均可播放、不保证固定下载速度，也不声称支持所有编码组合。完整实测证据与未通过项请看全量点检报告。

## 开源组件

第三方组件与许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。