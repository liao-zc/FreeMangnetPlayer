from pathlib import Path
p=Path('scripts/build_peer_recovery_docs.py');s=p.read_text(encoding='utf-8')
s=s.replace('上述缺陷已修复；', '4. 默认选片此前把已完成的小视频排在用户选择的正片前面。现优先明确选择的文件，再考虑已缓存状态；测试放入一个已下载的损坏小视频，确认默认起播仍选择正片，手动选择坏文件时才显示媒体错误。\n\n上述缺陷已修复；')
s=s.replace("text+='\\n每一行按真实观察记录", "text+='\\n[本机 HTTP 元数据服务检查](evidence/peer-recovery-metadata-http.json)：使用与 APP 相同的 User-Agent，iTorrents .net/.org 对本哈希返回 404，另外两个入口返回的内容不是种子数据。这是 Windows 工作区的观察，不能当作用户手机网络实测，也不能据此断言资源已失效。若该磁力在其他播放器能播放，仍需在用户同一设备网络上对照，或取得其可分享的 .torrent/Tracker 信息，进一步判断发现入口的差异。\\n'\ntext+='\\n每一行按真实观察记录")
p.write_text(s,encoding='utf-8')
