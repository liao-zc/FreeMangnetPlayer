"""Actual APK first-frame acceptance for the user's ten public magnets."""
import json,shlex,time
from pathlib import Path
from capture_usage_screenshots import adb,screenshot,PACKAGE,OUT

targets=json.loads(Path('scripts/public_acceptance_targets.json').read_text(encoding='utf-8'))
targets.append(dict(category='原指定资源',hash='2362347540B27D8A4142AF2BBE0A071F26A3B7A1'))
results=[]
adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
for index,target in enumerate(targets,1):
    # Each cold run has no earlier cached bytes or learned resource peers.
    adb('shell','am','force-stop',PACKAGE);adb('shell','pm','clear',PACKAGE);adb('shell','pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
    magnet='magnet:?xt=urn:btih:'+target['hash']
    start=time.monotonic();first=None;samples=[]
    adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity','-a','android.intent.action.VIEW','-d',shlex.quote(magnet),'--es','mode','play')
    while time.monotonic()-start<120:
        try:s=json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
        except Exception:s={}
        s['testSeconds']=round(time.monotonic()-start,2);samples.append(s)
        if s.get('playing') and s.get('position',0)>500 and s.get('payloadBytes',0)>0:
            if first is None:first=time.monotonic()-start
            if time.monotonic()-start-first>10:break
        time.sleep(1)
    screenshot('public-'+str(index).zfill(2)+'.png')
    row=dict(target,magnet=magnet,passed=first is not None,firstFrameSeconds=first,
        peakPayloadBytesPerSecond=max((s.get('payloadRate',0) for s in samples),default=0),
        final=samples[-1] if samples else {},samples=samples)
    results.append(row);(OUT/'public-acceptance.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    print(target['hash'],row['passed'],row['firstFrameSeconds'],flush=True)
passed=sum(r['passed'] for r in results[:10]);print('User resource acceptance:',passed,'/10',flush=True)
assert passed>=9,'Public playback acceptance below 90%; inspect actual peer and payload evidence'
