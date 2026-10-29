from pathlib import Path
p=Path('scripts/video_format_fixture.py');s=p.read_text(encoding='utf-8').replace("'-r','12'","'-r','25'");p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/TorrentTasks.kt');s=p.read_text(encoding='utf-8')
s=s.replace('val source = task.source + trackers.filter', 'val source = if (task.source.startsWith("magnet:", true)) task.source + trackers.filter',1)
s=s.replace('.joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") }\n                        engine.start(context', '.joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") } else task.source\n                        engine.start(context',1)
p.write_text(s,encoding='utf-8')
