"""Append observed comparison results without claiming playback from search success."""
import argparse,json,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--shots',required=True);p.add_argument('--run',required=True);a=p.parse_args()
root=Path(__file__).resolve().parent.parent;shots=Path(a.shots);target=root/'docs/screenshots/current'
report=f'''\n## 8. 同关键词搜索与立即起播核查（2026-10-29）

用户只有固定磁力，没有 `.torrent`。本轮采用同关键词 `Avengers`（复仇者联盟）搜索对照，不再以提供种子作为前提。[实际工作流](https://github.com/liao-zc/FreeMangnetPlayer/actions/runs/{a.run})。

### 起播条件核查

- `PlayerActivity` 在取得视频文件列表后创建播放器，不检查下载百分比。
- Media3 起播缓冲设置为 300 ms；LibVLC 网络及文件缓冲设置为 300 ms。这些值是播放器时间缓冲设置，不是固定文件缓存比例，也不是端到端起播时间保证。
- `TorrentFileDataSource` 按读取偏移等待已校验并写盘的分片，跳转时调整分片期限；视频头部和尾部索引优先请求，不等完整下载。
- 既有受控回归在约 **4.57%** 下载量时起播，但从任务开始到播放事件约 **20.7 秒**，不能标为“立马播”。指定电影上次仍为 **0 视频字节**，同样不能标为通过。

### 真实搜索与参考 APP 启动结果

两个版本使用相同关键词。只有能取得相同 BTIH 的实际搜索结果并播放，才能比较同资源起播；不同版本或不同节点的数据不可直接作速度排名。渲染尝试为默认、关闭 Impeller、软件渲染，均使用原 APK 的启动参数，没有修改参考安装包。
'''
files=list(shots.rglob('same-search-comparison.json'))
assert files,'No actual comparison evidence'
for file in files:
    data=json.loads(file.read_text(encoding='utf-8'));api=data['api']
    (root/f'docs/evidence/current-same-search-api{api}.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    report+=f'\n### Android API {api}\n\n| 参考 APP 渲染尝试 | 保持进程 |\n| --- | --- |\n'
    for attempt in data['reference_attempts']:report+=f"| {attempt['renderer']} | {'有进程，是否可用见截图/UI' if attempt['process'] else '未保持运行'} |\n"
    if data.get('reference_search_error'):report+='\n参考搜索未完成：`'+data['reference_search_error']+'`。此环境不能验证双方实际播放差异。\n'
    report+='\n| 本 APP 实际请求引擎 | 实际报告引擎 | 磁力结果数量 |\n| --- | --- | --- |\n'
    for s in data['searches']:
        if s['app']=='FreeMangnetPlayer':
            state=s['state'];report+=f"| {s['requested_engine']} | {state.get('engine','未获得状态')} | {len(state.get('results',[]))} |\n"
    report+=f'\n[实际状态、UI 与诊断证据](evidence/current-same-search-api{api}.json)\n\n'
    for image in file.parent.glob('*.png'):
        name=f'same-search-api{api}-{image.name}';shutil.copyfile(image,target/name)
        report+=f'![API {api} {image.stem} 实际截图](screenshots/current/{name})\n\n'
report+='\n**立即起播及同资源播放对照尚未通过，不能把本轮搜索执行完成写成播放优化完成。**如参考 APP 仍无法运行，此测试环境缺少可用对标客户端，需要在可运行参考 APP 的安卓设备继续验证。\n'
for name in ['report.md','usage-zh.md']:
    path=root/'docs'/name;s=path.read_text(encoding='utf-8').split('\n## 8. 同关键词')[0];path.write_text(s+report,encoding='utf-8')
print('Observed comparison added to current report')
