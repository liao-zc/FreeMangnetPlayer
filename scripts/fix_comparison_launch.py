from pathlib import Path
p=Path('scripts/compare_reference_player.py');s=p.read_text(encoding='utf-8')
s=s.replace("    adb('shell','am','force-stop',ref)","    result['reference_logcat']=adb('logcat','-d','-t','3000')\n    result['reference_process']=subprocess.run(['adb','shell','pidof',ref],capture_output=True,text=True).stdout.strip()\n    (OUT/'comparison-probe.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')\n    adb('shell','am','force-stop',ref)",1)
s=s.replace("PACKAGE+'/.PlayerActivity','--es','source',shlex.quote(USER_MAGNET)","PACKAGE+'/.MainActivity','-a','android.intent.action.VIEW','-d',shlex.quote(USER_MAGNET)")
p.write_text(s,encoding='utf-8')
p=Path('scripts/verify_video_formats.py');s=p.read_text(encoding='utf-8').replace("PACKAGE+'/.PlayerActivity','--es','source'","PACKAGE+'/.MainActivity','--es','test-torrent-url'");p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/MainActivity.kt');s=p.read_text(encoding='utf-8')
needle='        inbound?.let { openTorrent(MagnetResult(magnetTitle(it), it), intent.getStringExtra("mode") ?: "parse") }'
assert needle in s;s=s.replace(needle,needle+'''
        if (debug) intent.getStringExtra("test-torrent-url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let {
            openTorrent(MagnetResult(intent.getStringExtra("title").orEmpty(), it), intent.getStringExtra("mode") ?: "parse")
        }''')
p.write_text(s,encoding='utf-8')
