from pathlib import Path
p=Path('scripts/video_format_fixture.py');s=p.read_text(encoding='utf-8').replace("'-c:a',audio]+extra","'-c:a',audio,'-ac','2']+extra");p.write_text(s,encoding='utf-8')
