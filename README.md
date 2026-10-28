# FreeMangnetPlayer

FreeMangnetPlayer 是个人使用的 Android 磁力搜索、下载与播放应用。关键词搜索结果显示在首页的“磁力搜索结果”区域；从右上角菜单选择搜索引擎。首页也可直接粘贴 magnet 链接并解析。

**下载：**[GitHub Releases](https://github.com/liao-zc/FreeMangnetPlayer/releases)

## 使用流程

1. 在顶部大号搜索框输入关键词，点击“搜索”。搜索结果留在首页；每条识别到的磁力链接提供“解析 / 播放”和“下载 / 缓存”。
2. 搜索站点没有公开 magnet 链接时，页面会给出状态、返回和“打开搜索网站”按钮；不会一直留在空白页。可在网站复制 magnet 链接后粘贴到首页输入框。
3. 解析页面会在 45 秒未取得种子元数据时显示超时原因和大号返回按钮。获取元数据后可预览、下载或播放。
4. 播放页可将当前资源加入“收藏”。底部“下载”和“收藏”入口分别显示已开始的下载任务和已收藏资源。

## 搜索引擎

内置引擎：clg.im、BT搜索（dobt）、BD电影首发站、Pomo、美剧粉、高清族，以及原有磁力狗、聚合吧、磁力搜索、Seedog、BT世界和影视时光。右上角菜单可切换；自定义搜索地址可用 `%s` 作为关键词占位符。

## 当前范围和实测情况

- 面向 Android 7.0 及以上手机、平板和 Android TV。首页搜索框、搜索、引擎菜单和返回按钮均已加大；底部只保留有实际页面的搜索、下载和收藏入口。
- 搜索结果提取依赖搜索站点公开磁力 URI。搜索结果只给影片标题、普通详情页或被验证码拦截时，应用无法凭空生成 magnet 链接。
- 本次实际检查了新增站点：clg.im 返回验证页；mg.dobt.cc 首页未包含可直接提取的 magnet；BD电影首发站和 Pomo 可返回“哪吒”搜索结果，磁力链接位于同站详情页。应用现在会扫描搜索页并抓取最多 12 个同站结果详情页，提取磁力 URI 和文件名。网页抓取已核对到真实详情页磁力条目；尚未完成你提供的 BTIH 对应种子元数据连接、播放和下载闭环。
- 磁力连接不再等待固定数量的 DHT 节点后才加入任务；无可连接的 tracker / DHT 节点或没有做种者时仍可能无法取得种子文件列表。超时页面会允许用户立即返回。
- 下载任务及收藏条目保存在本机应用数据中。当前下载实现依赖播放/任务页面存活，不提供可靠的退出应用后后台续传；卸载或清除应用数据会清除收藏和任务记录。
- 播放器目前自动选择种子中的最大文件。只下载和播放你有权使用的内容。

## 实际界面截图

截图由 GitHub Actions Android 模拟器流程生成，展示当前应用界面；磁力链接截图使用虚构信息，仅展示交互，不代表资源能连接。

![首页](docs/screenshots/01-home-keyword.png)
![关键词](docs/screenshots/02-keyword-entered.png)
![搜索引擎菜单](docs/screenshots/03-engine-settings.png)
![首页内搜索状态](docs/screenshots/04-in-app-results.png)
![粘贴磁力](docs/screenshots/05-magnet-pasted.png)
![解析状态](docs/screenshots/06-torrent-progress.png)

## 构建

使用 Android Studio（JDK 17、Android SDK 35）打开项目，或执行 `gradlew.bat assembleDebug`。发布标签会触发 GitHub Actions 构建 APK 和命名源码压缩包。

## 第三方依赖

应用使用 simple-torrent-android / FrostWire jlibtorrent 和 AndroidX / Media3；许可证及来源见 [第三方依赖说明](THIRD_PARTY_NOTICES.md)。
