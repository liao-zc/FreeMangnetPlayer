"""Cold-start playback check for the exact MKV magnet supplied by the user."""
import json,time
from pathlib import Path
from capture_usage_screenshots import adb,PACKAGE

MAGNET='magnet:?xt=urn:btih:172241866c3c439cab4548115ed1a0250b09fa52&dn=Spider-Man.Brand.New.Day.2026.2160p.iT.WEB-DL.H.265.DV.DDP5.1.Atmos-PandaQT.mkv&so=0'
OUT=Path('specific-magnet-evidence');OUT.mkdir(exist_ok=True)
adb('shell','am','force-stop',PACKAGE)
adb('shell','pm','clear',PACKAGE)
adb('shell','am','start','-W','-n',PACKAGE+'/com.omniplayer.MainActivity','-a','android.intent.action.VIEW','-d',MAGNET,'--es','mode','play')
started=time.monotonic();samples=[];first_frame=None
while time.monotonic()-started<180:
    try: row=json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
    except Exception: row={}
    row['elapsedSeconds']=round(time.monotonic()-started,2);samples.append(row)
    if row.get('renderedFrameAt',0)>0 and row.get('videoHeight',0)>0 and row.get('payloadBytes',0)>0 and row.get('position',0)>500:
        first_frame=row;break
    time.sleep(1)
if samples:
    (OUT/'result.json').write_text(json.dumps({'magnet':MAGNET,'firstFrame':first_frame,'final':samples[-1],'samples':samples},ensure_ascii=False,indent=2),encoding='utf-8')
adb('shell','screencap','-p','/sdcard/specific-magnet.png')
adb('pull','/sdcard/specific-magnet.png',str(OUT/'screen.png'))
if not first_frame:
    raise AssertionError('No decoded video frame with verified payload within 180 seconds; inspect result.json peer, metadata, download, decoder and error fields')
print(json.dumps(first_frame,ensure_ascii=False,indent=2))
