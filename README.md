# FreeMangnetPlayer

个人使用的 Android 磁力播放器，适配手机、平板和电视。当前版本 **v0.1.12**。

- [下载 APK](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.12/FreeMangnetPlayer-v0.1.12.apk)
- [播放器、解析与缓存优化报告](docs/player-v0.1.12.md)
- [带实际截图的操作说明](docs/usage-zh.md)
- [离线 HTML 图文报告](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.12/FreeMangnetPlayer-v0.1.12-report.html)
- [源码下载](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.12/FreeMangnetPlayer-v0.1.12-source.zip)

播放器内嵌图标、时间/缓冲进度条、倍速、跳转、切片、全屏和系统画中画；默认并发任务数 5。播放临时缓存会在切换后释放，主动缓存/下载会保留。上传/下载不限速；实际较慢做种测试在 **3.70%** 下载量时开始播放。节点发现和流畅程度受真实资源与网络影响，完整实测范围见报告。

## 开源与构建

Kotlin、Jetpack Compose、AndroidX Media3、jlibtorrent；APK 包含 arm64-v8a、armeabi-v7a、x86、x86_64。通过 GitHub Actions 构建和真实 Android 模拟器验证。第三方许可见 [第三方声明](THIRD_PARTY_NOTICES.md)。
