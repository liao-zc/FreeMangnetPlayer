from pathlib import Path
p=Path('scripts/build_current_report.py')
s=p.read_text(encoding='utf-8')
s=s.replace("a=parser.parse_args();root=", "parser.add_argument('--regression',required=True)\na=parser.parse_args();root=")
s=s.replace("text+='''\n![实际 MP4", "text+='''\n![实际 MP4")
needle="(root/'docs/report.md').write_text(text,encoding='utf-8');"
extra="""regdir=Path(a.regression)
reg=[r for r in json.loads((regdir/'player-revision-verification.json').read_text(encoding='utf-8')) if 'passed' in r]
assert len(reg)==21 and all(r['passed'] for r in reg)
(e/'current-regression.json').write_text(json.dumps(reg,ensure_ascii=False,indent=2),encoding='utf-8')
for p in regdir.glob('*.png'):
    if p.read_bytes().startswith(b'\\x89PNG\\r\\n\\x1a\\n'):shutil.copyfile(p,target/('reg-'+p.name))
text+='\\n## 7. 播放与缓存回归点检\\n\\nAndroid 15 / API 35 模拟器实际执行 [回归工作流](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/40848325867)，21 项通过。受控做种素材用于验证生命周期与下载行为，不代替指定电影。\\n\\n| 检查项 | 结果 |\\n| --- | --- |\\n'
for r in reg:text+=f"| `{r['check']}` | 通过 |\\n"
text+='\\n[逐项状态与测量数据](evidence/current-regression.json)\\n\\n时间指标采用约 500 ms 状态轮询及开始播放事件；不是精确视频首帧计时。短暂 Peer 连接可能未采到，出现已下载数据但 Peer 耗时为 -1 不代表没有连接。\\n'
"""
s=s.replace(needle,extra+needle)
s=s.replace("'build_current_report.py','build_offline_report.py'", "'build_current_report.py','build_offline_report.py'")
p.write_text(s,encoding='utf-8')
