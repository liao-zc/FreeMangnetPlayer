"""Actual APK first-frame acceptance for the user's ten public magnets."""
import json,shlex,time
from pathlib import Path
from capture_usage_screenshots import adb,screenshot,PACKAGE,OUT

targets=json.loads(Path('scripts/public_acceptance_targets.json').read_text(encoding='utf-8'))
targets.append(dict(category='原指定资源',hash='2362347540B27D8A4142AF2BBE0A071F26A3B7A1'))
targets.append(dict(category='本轮 MKV 起播回归',hash='172241866C3C439CAB4548115ED1A0250B09FA52',magnet='magnet:?xt=urn:btih:172241866c3c439cab4548115ed1a0250b09fa52&dn=Spider-Man.Brand.New.Day.2026.2160p.iT.WEB-DL.H.265.DV.DDP5.1.Atmos-PandaQT.mkv&so=0'))
results=[]
adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
for index,target in enumerate(targets,1):
    # Each cold run has no earlier cached bytes or learned resource peers.
    adb('shell','am','force-stop',PACKAGE);adb('shell','pm','clear',PACKAGE);adb('shell','pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
    magnet=target.get('magnet') or 'magnet:?xt=urn:btih:'+target['hash']
    start=time.monotonic();first=None;firstPosition=None;firstSnapshot=None;sustained=False;samples=[]
    adb('shell','am','start','-W','-n',PACKAGE+'/com.omniplayer.MainActivity','-a','android.intent.action.VIEW','-d',shlex.quote(magnet),'--es','mode','play')
    while time.monotonic()-start<120:
        try:s=json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
        except Exception:s={}
        s['testSeconds']=round(time.monotonic()-start,2);samples.append(s)
        if s.get('playing') and s.get('position',0)>500 and s.get('payloadBytes',0)>0 and s.get('renderedFrameAt',0)>0 and s.get('videoHeight',0)>0:
            if first is None:
                first=time.monotonic()-start;firstPosition=s['position'];firstSnapshot=dict(s)
            if time.monotonic()-start-first>=20 and s['position']-firstPosition>=18000:
                sustained=True;break
        time.sleep(1)
    screenshot('public-'+str(index).zfill(2)+'.png')
    row=dict(target,magnet=magnet,passed=sustained and firstSnapshot is not None and 0<firstSnapshot.get('downloaded',0)<firstSnapshot.get('total',0),firstFrameSeconds=first,firstFrameSnapshot=firstSnapshot,sustainedPlayback=sustained,
        peakPayloadBytesPerSecond=max((s.get('payloadRate',0) for s in samples),default=0),
        final=samples[-1] if samples else {},samples=samples)
    results.append(row);(OUT/'public-acceptance.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    print(target['hash'],row['passed'],row['firstFrameSeconds'],flush=True)
passed=sum(r['passed'] for r in results[:10]);regression=results[-1]
print('User resource acceptance:',passed,'/10',flush=True)
print('Exact MKV regression:',regression['passed'],'first-frame-seconds=',regression['firstFrameSeconds'],flush=True)
assert regression['passed'],'The exact reported MKV magnet must render and sustain playback while incomplete'
assert passed==10,'All ten previous user resources must render video while incomplete and sustain playback; inspect peer and payload evidence'
