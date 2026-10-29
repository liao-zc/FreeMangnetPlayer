from pathlib import Path
p=Path('app/src/main/java/com/freemangnetplayer/MainActivity.kt')
s=p.read_text(encoding='utf-8').replace('"ws", "x.pe")', '"ws", "x.pe", "so")')
p.write_text(s,encoding='utf-8')
