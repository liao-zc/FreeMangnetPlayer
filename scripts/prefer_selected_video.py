from pathlib import Path
for name in ['TorrentTasks.kt','PlayerActivity.kt']:
    p=Path('app/src/main/java/com/freemangnetplayer')/name
    s=p.read_text(encoding='utf-8')
    for owner in ['task','current']:
        s=s.replace(f'compareByDescending<TaskFile> {{ it.done >= it.size }}.thenByDescending {{ it.index in {owner}.selected }}', f'compareByDescending<TaskFile> {{ it.index in {owner}.selected }}.thenByDescending {{ it.done >= it.size }}')
    p.write_text(s,encoding='utf-8')
p=Path('scripts/verify_player_revision.py');s=p.read_text(encoding='utf-8')
s=s.replace("    screenshot('revision-02-real-file-list.png')", "    assert parsed['currentFile']==next(f['index'] for f in parsed['files'] if f.get('selected')), parsed\n    screenshot('revision-02-real-file-list.png')")
p.write_text(s,encoding='utf-8')
