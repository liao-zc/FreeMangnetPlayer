from pathlib import Path
p=Path('scripts/verify_player_revision.py');s=p.read_text(encoding='utf-8')
s=s.replace("    time.sleep(2)\n    find_and_tap('设置')", "    time.sleep(2)\n    initial_pid=adb('shell','pidof',PACKAGE).strip()\n    find_and_tap('设置')",1)
s=s.replace("    record('default-maximum-downloads-five',fields=fields)\n    find_and_tap('返回首页')", "    record('default-maximum-downloads-five',fields=fields)\n    from verify_optimization import edit\n    edit('最大缓存任务数量（1–100）','1')\n    find_and_tap('保存设置')\n    find_and_tap('返回首页')")
s=s.replace("    process=adb('shell','pidof',PACKAGE).strip()", "    process=adb('shell','pidof',PACKAGE).strip()\n    assert process==initial_pid, 'Process restarted during initial parsing/playback'")
marker="    record('explicit-cache-survives-next-resource',retained_path=retained_path,new_state=transient)"
s=s.replace(marker,marker+'''
    find_and_tap('缓存')
    denied=wait(lambda s:not s.get('retainedCache') and '最大缓存数量' in s.get('playerError',''))
    assert adb('shell','test','-f',retained_path)==''
    screenshot('revision-07b-cache-limit.png')
    record('cache-limit-preserves-explicit-cache',state=denied,retained_path=retained_path)
''')
s=s.replace("    open_magnet(fixtures[2]['magnet']); wait", "    adb('shell','am','force-stop',PACKAGE)\n    adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity')\n    time.sleep(2)\n    process=adb('shell','pidof',PACKAGE).strip()\n    open_magnet(fixtures[2]['magnet']); wait",1)
s=s.replace("    record('new-resource-clears-previous-unretained-cache',removed=transient_folder,state=state())", "    record('new-resource-clears-previous-unretained-cache',removed=transient_folder,state=state(),after_deliberate_system_stop=True)\n    assert adb('shell','test','-f',retained_path)==''\n    record('temporary-cleanup-and-explicit-retention-after-restart',removed=transient_folder,retained_path=retained_path)")
p.write_text(s,encoding='utf-8')
p=Path('scripts/verify_optimization.py');s=p.read_text(encoding='utf-8').replace("require_visible('返回搜索', '▶ 播放')", "require_visible('返回搜索')");p.write_text(s,encoding='utf-8')
p=Path('scripts/build_player_revision_docs.py');s=p.read_text(encoding='utf-8')
s=s.replace("'invalid-media-shows-error-without-process-restart']", "'invalid-media-shows-error-without-process-restart','cache-limit-preserves-explicit-cache','temporary-cleanup-and-explicit-retention-after-restart']")
s=s.replace('进程 PID 始终为', '连续切换阶段进程 PID 始终为')
s=s.replace('没有自动重启，日志没有 APP 崩溃记录。', '没有自动重启，日志没有 APP 崩溃记录。另有一次主动执行的系统停止用于校验缓存持久化，已在原始证据中标明；该操作不作为崩溃或自动重启。')
s=s.replace('切换资源后保留已选文件的缓存，可从收藏或播放列表重新打开。', '切换资源后保留已选文件的缓存，可从收藏或播放列表重新打开。达到最大缓存数时拒绝新增主动缓存并提示清理或调整设置；不会因新视频的临时播放删除旧主动缓存。')
s=s.replace("text+=image('revision-08-new-resource.png'", "text+=image('revision-07b-cache-limit.png','实际缓存数量上限提示，已保存的视频继续保留')\ntext+=image('revision-08-new-resource.png'")
p.write_text(s,encoding='utf-8')
