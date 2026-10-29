import json
from pathlib import Path
rows=json.loads(Path('docs/evidence/peer-recovery-verification.json').read_text(encoding='utf-8'))
by={r['check']:r for r in rows}
public=by['public-pure-magnet-discovery']['state']
summary='''## 本轮结果与未完成事项

- 播放专项 21 项通过，下载管理回归 22 项通过；下文附真实画面和原始记录。
- 已新增目标文件磁力复制；修复元数据替换时丢失连接入口、停滞连接恢复和默认选片问题；新增 RM/RMVB 解码。
- **用户更正的 236234… 磁力仍未实际起播。**新旧版本都解析到了文件，但观察期间均为 0 资源节点、0 下载字节。不能把本轮修复称为已经解决这条电影的播放问题，也没有这条电影的播放截图。
- 仍需要在能播放它的 Magnet_player 中取得这条资源的完整分享磁力或 .torrent，并在同一设备网络对照。当前消息没有实际附上参考播放器截图，不能断言该资源失效。

'''
network=f'''### 公网下载实测与限速结论

同一模拟器另外测试纯 BTIH 的 Ubuntu 官方种子，实际取得 {public.get('peers',0)} 个资源节点和 {public.get('payloadBytes',0):,} 下载字节。应用上传、下载限速均为 0。这说明本轮测试环境和应用并非普遍只能下载 10 B/s；它不能证明用户电影的资源发现入口已经与另一播放器一致，也不能保证用户手机上的相同速度。

![公网种子实际下载状态](screenshots/peer-recovery/revision-10-public-magnet.png)

'''
for filename in ['docs/peer-v0.1.13.md','docs/usage-zh.md']:
    p=Path(filename);s=p.read_text(encoding='utf-8')
    s=s.replace('## 1. 为什么取得文件列表仍然无法播放',summary+'## 1. 为什么取得文件列表仍然无法播放',1)
    s=s.replace('## 4. 选择文件并复制磁力链接',network+'## 4. 选择文件并复制磁力链接',1)
    p.write_text(s,encoding='utf-8')
p=Path('README.md');s=p.read_text(encoding='utf-8')
s=s.replace('Kotlin、', '**本轮仍未完成：用户指定的 236234… 电影磁力没有取得视频分片，尚未成功播放；详见报告。**\n\nKotlin、')
p.write_text(s,encoding='utf-8')
