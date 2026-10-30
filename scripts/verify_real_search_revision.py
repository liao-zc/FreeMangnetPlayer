"""Compare six real browser searches with actual APK results; retain access failures."""
import json,re,shlex,time
from pathlib import Path
from capture_usage_screenshots import adb,screenshot,require_visible,visible_text,PACKAGE,OUT
from magnet_hash import canonical_hash

baseline=json.loads((OUT/'browser-baseline.json').read_text(encoding='utf-8'));rows=[]
adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
for i,browser in enumerate(baseline,1):
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','am','start','-W','-n',PACKAGE+'/com.omniplayer.MainActivity','--es','test-engine',shlex.quote(browser['engine']),'--es','test-query',shlex.quote('哪吒'))
    end=time.monotonic()+48;s={}
    while time.monotonic()<end:
        try:s=json.loads(adb('shell','run-as',PACKAGE,'cat','files/search-check.json'))
        except Exception:s={}
        if s.get('engine')==browser['engine'] and s.get('complete'):break
        time.sleep(1)
    require_visible('搜索结果','返回')
    text=visible_text()
    assert '查看本页原网页' not in text and '来自' not in text and '正在连接' not in text,text
    screenshot('real-search-app-'+str(i)+'.png')
    assert s.get('engine')==browser['engine'], 'Search ran with the wrong engine: '+str(s.get('engine'))
    hashes={canonical_hash(r['magnet']) for r in s.get('results',[])}-{None};expected=set(browser['hashes'])
    comparable=not browser.get('error') and s.get('state') in ['results','empty'] and s.get('complete')
    # Empty pages with access challenges are not proof of successful search.
    comparable=comparable and (bool(expected) or bool(hashes))
    difference=abs(len(hashes)-len(expected))/max(1,len(expected)) if comparable else None
    rows.append(dict(engine=browser['engine'],query='哪吒',browserHashCount=len(expected),appHashCount=len(hashes),
        comparable=comparable,differenceRatio=difference,withinTenPercent=difference<=.1 if difference is not None else None,
        missing=sorted(expected-hashes),extra=sorted(hashes-expected),appState=s,browser=browser))
    (OUT/'real-search-comparison.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
print('Observed real source comparisons:',[(r['engine'],r['comparable'],r['withinTenPercent']) for r in rows])
