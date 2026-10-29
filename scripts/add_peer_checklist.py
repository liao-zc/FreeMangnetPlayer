from pathlib import Path
checks=[
('补充元数据后保留 Tracker，取得分片并播放','revision-00-metadata-peer-recovery.png'),
('所选文件磁力写入系统剪贴板，粘贴回读一致','revision-00b-file-magnet-copy.png'),
('复制的磁力再次解析后恢复选片','revision-02-real-file-list.png'),
('默认最大下载数量为 5','revision-01-default-settings.png'),
('单独粘贴磁力解析真实文件列表','revision-02-real-file-list.png'),
('无需下载至 20% 即起播','revision-03-integrated-controls.png'),
('播放器按钮集成在视频控制条内','revision-03-integrated-controls.png'),
('内置暂停、播放、倍速和前后跳转','revision-03-integrated-controls.png'),
('当前时间、总时长与可拖动进度条','revision-03-integrated-controls.png'),
('全屏使用内置图标','revision-04-fullscreen-icons.png'),
('系统画中画继续播放并恢复','revision-05-real-pip.png'),
('切换文件后清除上一临时视频','revision-06-second-video.png'),
('主动缓存可在切换资源后保留','revision-07-explicit-cache.png'),
('缓存上限不删除主动保留的文件','revision-07b-cache-limit.png'),
('新资源释放旧临时缓存','revision-08-new-resource.png'),
('强制停止并重开后清临时缓存、保留主动缓存','revision-08-new-resource.png'),
('连续六次全屏切换无崩溃、进程未重启','revision-09-lifecycle-stress.png'),
('RealMedia 解码出现实际视频画面并持续播放','revision-09c-realmedia.png'),
('RealMedia 暂停、跳转、倍速、全屏操作','revision-09d-realmedia-fullscreen.png'),
('损坏文件显示错误，应用进程不重启','revision-09b-media-error.png'),
('解析重试与返回操作可响应','revision-11-retry-responsive.png')]
table='## 播放专项逐项点检\n\n所有截图为实际界面；截图展示界面状态，时间推进、文件删除与进程校验的断言数据见原始 JSON。\n\n| 操作或检查 | 结果 | 实际截图 |\n| --- | --- | --- |\n'
for label,shot in checks:table+=f'| {label} | 通过 | [截图](screenshots/peer-recovery/{shot}) |\n'
table+='\n用户电影：**解析通过，实际播放未通过**，0 节点、0 下载字节；[实际界面](screenshots/peer-recovery/user-resource-v0.1.13.png)。公网 Ubuntu 种子实际下载观察另见上文，未混入上述通过数量。\n\n'
for name in ['docs/peer-v0.1.13.md','docs/usage-zh.md']:
    p=Path(name);s=p.read_text(encoding='utf-8');s=s.replace('## 6. GitHub 文档与邮件通知',table+'## 6. GitHub 文档与邮件通知');p.write_text(s,encoding='utf-8')
