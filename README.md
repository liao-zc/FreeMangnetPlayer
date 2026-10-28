# FreeMangnetPlayer

FreeMangnetPlayer 是供个人使用的 Android 磁力链接搜索、下载与播放应用。搜索使用一个关键词输入框；搜索引擎在“设置”中选择，网址不显示在首页。

**下载：**[GitHub Releases](https://github.com/liao-zc/FreeMangnetPlayer/releases)，新版 APK 名称为 `FreeMangnetPlayer-版本号.apk`。

文档直达：[详细使用说明](https://github.com/liao-zc/FreeMangnetPlayer/blob/main/docs/usage-zh.md) · [第三方依赖说明](https://github.com/liao-zc/FreeMangnetPlayer/blob/main/THIRD_PARTY_NOTICES.md)。

## 操作说明（应用实际截图）

### 1. 输入关键词并搜索

首页只显示关键词输入框和搜索按钮。搜索来源可在右上角“设置”中选择，设置中展示引擎名称，不需要输入网址。

![关键词搜索首页](docs/screenshots/01-home-keyword.png)

![输入关键词](docs/screenshots/02-keyword-entered.png)

### 2. 在应用内查看搜索结果

搜索网站在应用内页面打开，网页跳转不会交给外部浏览器。页面里的磁力链接会被识别并加入页面上方的资源列表；也可以点“粘贴磁力”手动添加链接。此次模拟器未能加载出第三方站点结果，资源列表不伪造结果。

![搜索引擎设置](docs/screenshots/03-engine-settings.png)

![应用内搜索结果](docs/screenshots/04-in-app-results.png)

### 3. 直接播放或下载

每条识别到或手动添加的磁力资源提供“播放”和“下载 / 缓存”操作。播放页面显示连接、缓存进度、速度和做种者数量；下载完成后可从该页面播放已缓存的视频。

![手动添加磁力链接](docs/screenshots/05-add-magnet-dialog.png)

![下载与缓存进度](docs/screenshots/06-torrent-progress.png)

演示截图可能使用虚构磁力链接，仅表示界面状态，不代表该资源可用。资源播放和下载需要种子有可连接的做种者，且视频编码受设备支持。

## 当前版本范围

- 支持 Android 7.0（API 24）及以上，面向手机、平板和 Android TV；电视遥控器可操作焦点，文字搜索需设备输入法。
- 内置搜索来源可在设置中选择。搜索页面由第三方网站提供，站点改版、验证码或网络限制都可能影响结果；磁力结果识别取决于网站把链接暴露在页面中。
- 当前每个种子自动选择最大文件；暂不提供种子内部文件选择、后台下载服务、退出应用后的续传或下载任务库。
- 下载保存在应用专属 Movies 目录。卸载应用或清除应用数据会删除这些文件。
- 只下载和播放你有权使用的内容，并遵守搜索网站条款及适用法律。

## 构建

使用 Android Studio（JDK 17、Android SDK 35）打开项目，或运行 `gradlew.bat assembleDebug`。本地调试包位于 `app/build/outputs/apk/debug/app-debug.apk`；版本标签会触发 GitHub Actions 云端构建并发布命名 APK。

## 可复用开源项目

| 方向 | 仓库 | 说明 |
| --- | --- | --- |
| 当前 MVP 磁力引擎 | [simple-torrent-android](https://github.com/masterwok/simple-torrent-android) | MIT；此项目当前使用，仓库已标记弃用，且只取最大文件。 |
| BitTorrent 引擎 | [frostwire-jlibtorrent](https://github.com/frostwire/frostwire-jlibtorrent) | MIT Java/JNI 封装，可作为后续替换方案，需处理 Android ABI。 |
| 完整客户端参考 | [LibreTorrent](https://github.com/proninyaroslav/libretorrent) | 成熟 Android 客户端，GPL-3.0；复用代码需遵守 GPL。 |
| Android TV 参考 | [FongMi/TV](https://github.com/FongMi/TV) | TV 导航和播放参考，不含 BitTorrent 引擎，GPL-3.0。 |
| 网盘资源管理参考 | [CloudSaver](https://github.com/jiangrui1994/CloudSaver) | 相邻领域项目，不是可直接嵌入的磁力组件。 |
| 影视资源聚合参考 | [Selene](https://github.com/MoonTechLab/Selene) | 相邻领域项目；复用前需核实仓库当前许可证。 |

原始应用代码采用 MIT。第三方组件遵循各自许可证，详情见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。