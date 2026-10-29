from pathlib import Path
p=Path('scripts/build_current_report.py');s=p.read_text(encoding='utf-8')
s=s.replace("for p in regdir.glob('*.png'):","for p in regdir.glob('revision-*.png'):")
needle="text+='\\n[逐项状态与测量数据]"
extra="""text+='\\n实际操作截图：\\n\\n'
for label,name in [('文件列表与选片','02-real-file-list'),('复制目标文件磁力','00b-file-magnet-copy'),('内置播放、暂停、倍速、跳转与进度条','03-integrated-controls'),('全屏','04-fullscreen-icons'),('悬浮画中画','05-real-pip'),('切换第二个文件','06-second-video'),('保留显式缓存','07-explicit-cache'),('缓存数量限制','07b-cache-limit'),('新资源清理临时缓存','08-new-resource'),('RealMedia 播放','09c-realmedia'),('损坏文件错误反馈','09b-media-error'),('重试与返回','11-retry-responsive')]:
    text+=f'### {label}\\n\\n![{label}](screenshots/current/reg-revision-{name}.png)\\n\\n'
"""
s=s.replace(needle,extra+needle)
p.write_text(s,encoding='utf-8')
