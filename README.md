# FreeMangnetPlayer

自用 Android 磁力播放器，应用名 **FreeMangnetPlayer**，包名 `com.freemangnetplayer`。

## v0.1.11 下载和实际报告

- [下载安装包](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.11/FreeMangnetPlayer-v0.1.11.apk)
- [操作步骤、真实截图和速度优化报告](docs/performance-v0.1.11.md)
- [离线 HTML 报告，下载后用浏览器打开](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.11/FreeMangnetPlayer-v0.1.11-report.html)
- [完整源码 ZIP](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.11/FreeMangnetPlayer-v0.1.11-source.zip)
- [原始点检记录](docs/evidence/performance-verification.json)

设置入口位于底部。支持关键词搜索、磁力解析、下载/缓存、收藏、多视频切换、倍速/跳转、全屏和系统 PiP 悬浮播放。APP 下载/上传不限速；本轮不限速做种实测峰值 **6.45 MiB/s**，该值来自本地模拟器测试，不代表任意公网资源速度。公网纯磁力对照的实际结果和限制见报告。

v0.1.10 可以直接覆盖安装；v0.1.9 的首次固定签名迁移需卸载旧版，先记录收藏和磁力。Android 7.0+；PiP 需要 Android 8.0+ 和设备支持。电视/平板尚未实机验证。

## 编译

JDK 17、Android SDK 35、Gradle 8.9，执行 `gradle assembleDebug`。内核 jlibtorrent 1.2.19.0，播放器 AndroidX Media3 1.5.1。签名由 GitHub Actions secrets 提供，私钥不在仓库。[第三方许可](third-party/simple-torrent-android-LICENSE.txt)。

## 历史报告

- [v0.1.10 完整说明和 22 项实测](docs/optimization-v0.1.10.md)
- [v0.1.9 历史报告](docs/archive-v0.1.9.md)
