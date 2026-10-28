# FreeMangnetPlayer

Android 个人磁力搜索、解析、边下边播、下载缓存与收藏应用，名称为 **FreeMangnetPlayer**。

- [下载 v0.1.9 APK](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.9/FreeMangnetPlayer-v0.1.9.apk)
- [实际截图、操作说明与五项要求的测试报告](docs/usage-zh.md)
- [下载离线 HTML 报告（截图嵌入）](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.9/FreeMangnetPlayer-v0.1.9-report.html)
- [下载 FreeMangnetPlayer-v0.1.9-source.zip](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.9/FreeMangnetPlayer-v0.1.9-source.zip)

## 本次实测结论

修复无结果搜索的白屏与返回、解析按钮位置、元数据进度条和真实文件列表。在线测试种子通过 APP 搜索→解析→40% 时边下边播→下载文件 SHA-256 校验→倍速和前进/后退→收藏冷启动持久化验证，报告有真实动画画面。

**仍未全部通过：**用户哪吒磁力已经读出真实 MKV 文件列表，但测试网络中连接节点和下载量都是 0，未播放成功。六个引擎中的 dobt 实际代理页面无法访问，尚不能完成有效搜索。没有用其他视频冒充哪吒播放成功。

## 指定六个引擎

| 引擎 | 浏览器 / APP 搜索“哪吒”的唯一磁力数 | 本轮结果 |
| --- | --- | --- |
| clg.im | 12 / 12 | 完整哈希集合一致 |
| mg.dobt.cc | 未完成有效搜索 | 访问其实际代理页面失败 |
| www.bdflixs.com | 156 / 156 | 完整哈希集合一致，包含 Base32 |
| pomo.mom | 60 / 60 | 完整哈希集合一致 |
| www.mjf2020.com | 0 / 0 | 一致的空结果 |
| hdzu.org | 48 / 48 | 浏览器 HTTP 403 重试后成功，集合一致 |

另有一个自定义引擎配置。首页保留搜索框、引擎配置、磁力解析入口及播放 / 下载 / 收藏列表。结果在 APP 原生页面呈现并可返回；资源解析后可以收藏、下载、预览和播放。

## 使用限制与构建

Android API 24+；本轮运行测试为 API 35 手机模拟器，平板和电视还需实机验证。当前缓存最大文件，下载依赖资源页面运行，离开页面会暂停并保留缓存，尚无后台下载服务。

JDK 17、Android SDK 35、Gradle 8.9，执行 `gradle assembleDebug`。下载内核使用 jlibtorrent 1.2.19.0，播放器使用 AndroidX Media3；内置的 simple-torrent-android 包装器保留 MIT 许可并修复原生句柄和分片索引问题。[第三方许可](third-party/simple-torrent-android-LICENSE.txt)。

GitHub CI 校验 Git 对象无透明加密内容、源码 UTF-8、Markdown 图片路径及 GitHub 渲染。截图与原始测试 JSON 随报告上传，离线 HTML 可下载后直接阅读。