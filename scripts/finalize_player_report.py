from pathlib import Path

for name in ('docs/player-v0.1.12.md', 'docs/usage-zh.md'):
    path = Path(name)
    text = path.read_text(encoding='utf-8').replace('APK 实際运行', 'APK 实际运行')
    text = text.replace('## 5. 白屏与闪退：修复和验证范围', '## 5. 白屏与闪退：修复和验证范围')
    text = text.replace('修复内容：\n', '修复内容：\n\n视频现在位于资源详情上方；退出全屏恢复进入前的屏幕方向，避免横向资源信息把视频挤出可视区域。启用备用解码器，并对不支持或损坏的文件显示错误，停止无限准备重试。\n')
    text = text.replace('两行按实际观察填写', '**本次公网测试中，两种客户端都成功取得元数据并实际下载。APP 在 75 秒观察结束时接入 102 个节点、累计有效载荷 544,818,141 字节（约 519.6 MiB）。这是 Ubuntu 安装镜像的网络与解析测试，不是影视播放测试。用户先前提供的影视 BTIH 未在本轮重新验证，不能据此声称该影视资源已成功播放。**\n\n两行按实际观察填写')
    path.write_text(text, encoding='utf-8')
