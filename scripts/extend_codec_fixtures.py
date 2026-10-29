from pathlib import Path
p=Path('scripts/video_format_fixture.py');s=p.read_text(encoding='utf-8')
s=s.replace("('ogv','libtheora','libvorbis',[])]", "('ogv','libtheora','libvorbis',[]),('av1.mp4','libaom-av1','aac',['-cpu-used','8','-crf','40']),('vp8.webm','libvpx','libvorbis',['-deadline','realtime']),('h263.3gp','h263','aac',['-ar','22050','-ac','1'])]")
s=s.replace("'-vf','scale=320:240'", "'-vf','scale=352:288' if video=='h263' else 'scale=320:240'")
s=s.replace("if ext in ['mp4','mov']", "if ext.endswith(('mp4','mov'))")
s=s.replace("torrents[self.path.strip('/').split('.')[0]]", "torrents[self.path.strip('/').removesuffix('.torrent')]")
p.write_text(s,encoding='utf-8')
p=Path('scripts/verify_video_formats.py');s=p.read_text(encoding='utf-8').replace('len(rows)==13','len(rows)==16');p.write_text(s,encoding='utf-8')
