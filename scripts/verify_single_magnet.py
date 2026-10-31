"""End-to-end playback probe for the exact magnet supplied by the user."""
import json,shlex,subprocess,time
from pathlib import Path
from capture_usage_screenshots import adb,screenshot,PACKAGE,OUT

MAGNET='magnet:?xt=urn:btih:3b1f5ed610886c02802f0f8d814e06c7a6934bda&dn=Spider-Man.Brand.New.Day.2026.HD1080P.X265.AC3.DDP5.1.English.CHS-ENG.JKYY.srt&so=1'
adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
adb('shell','am','force-stop',PACKAGE);adb('shell','pm','clear',PACKAGE)
adb('shell','pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
adb('shell','am','start','-W','-n',PACKAGE+'/com.omniplayer.MainActivity','-a','android.intent.action.VIEW','-d',shlex.quote(MAGNET),'--es','mode','play')
start=time.monotonic();samples=[];first=None;first_snapshot=None;sustained=False;metadata_snapshot=None
while time.monotonic()-start<150:
    try:s=json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
    except Exception:s={}
    s['elapsedSeconds']=round(time.monotonic()-start,1);samples.append(s)
    if s.get('metadata') and metadata_snapshot is None:metadata_snapshot=dict(s)
    if s.get('playing') and s.get('position',0)>500 and s.get('payloadBytes',0)>0 and s.get('renderedFrameAt',0)>0 and s.get('videoHeight',0)>0:
        if first is None:first=time.monotonic()-start;first_snapshot=dict(s);first_position=s.get('position',0)
        if time.monotonic()-start-first>=20 and s.get('position',0)-first_position>=18000:sustained=True;break
    time.sleep(1)
screenshot('single-magnet.png')
result={'magnet':MAGNET,'metadataParsed':bool(metadata_snapshot),'files':(metadata_snapshot or {}).get('files',[]),'metadataState':metadata_snapshot,'firstFrameSeconds':first,'firstFrameSnapshot':first_snapshot,'sustainedPlayback':sustained,'lastState':samples[-1] if samples else {},'sampleCount':len(samples),'elapsedSeconds':round(time.monotonic()-start,1)}
(OUT/'single-magnet.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
with (OUT/'single-magnet-logcat.txt').open('w',encoding='utf-8') as log:subprocess.run(['adb','logcat','-d','-t','4000'],stdout=log,stderr=subprocess.STDOUT,check=True,text=True)
print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
assert result['metadataParsed'],'Torrent metadata/file list was not obtained; inspect tracker/DHT and network evidence'
assert sustained,'No sustained video first-frame playback while the file was still incomplete'