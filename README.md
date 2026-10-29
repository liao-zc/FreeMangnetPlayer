# FreeMangnetPlayer

Android 个人磁力搜索、解析、边下边播、下载缓存与收藏应用，名称为 **FreeMangnetPlayer**。

- [下载 v0.1.10 APK](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.10/FreeMangnetPlayer-v0.1.10.apk)
- [完整操作截图与四项功能优化报告](docs/usage-zh.md)
- [下载离线 HTML 截图报告](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.10/FreeMangnetPlayer-v0.1.10-report.html)
- [下载 FreeMangnetPlayer-v0.1.10-source.zip](https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.10/FreeMangnetPlayer-v0.1.10-source.zip)

## 本轮结果

**22/22 项实际检查通过。** 包括未完成下载时真实播放、暂停/继续、取消后续传、后台与普通熄屏下载、多文件选择和视频切换、自定义下载/缓存目录、逐文件 SHA-256、倍速与跳转、横屏恢复、排队、缓存上限淘汰及清空后的文件和下载列表检查。

测试使用真实 BitTorrent 做种端和 Blender CC BY 视频，未模拟下载或播放画面。[实际工作流](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40805567443)；[原始 JSON](docs/evidence/optimization-verification.json)。测试故意限速，低于视频码率时仍会缓冲，不宣称任意链接均可流畅播放。

下载与缓存由前台服务持有，资源页面退出后仍运行。首页提供设置；六个内置引擎和一个自定义引擎、默认引擎、可写下载/缓存目录、最大并发下载数和最大缓存任务数可配置。

## 安装与范围

**从 v0.1.9 首次升级需卸载旧版，先记录重要收藏/磁力。** 新版改用固定签名，后续版本沿用。Android API 24+；已验证 API 35 手机模拟器及横屏。电视和平板未实机验证，Android 公共目录与后台服务仍受系统权限和省电策略限制，详见报告。

本轮未重复测试六个公网搜索引擎或用户“哪吒”磁力；[上一轮报告](docs/archive-v0.1.9.md)记录 dobt 访问失败和用户资源未取得传输数据的限制。

## 构建与文档

JDK 17、Android SDK 35、Gradle 8.9，执行 `gradle assembleDebug`。内核 jlibtorrent 1.2.19.0；播放器 AndroidX Media3 1.5.1。MIT 会话桥已包含为源码，[第三方许可](third-party/simple-torrent-android-LICENSE.txt)。固定签名由 GitHub Actions secrets 提供，不在仓库保存私钥。

提交后检查原始 Git 对象无透明加密内容、严格 UTF-8；GitHub CI 检查 Markdown 图片和实际渲染。离线报告包含真实截图，可直接下载阅读。
