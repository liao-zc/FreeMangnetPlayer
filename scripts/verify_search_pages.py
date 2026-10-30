import json,shlex,time,xml.etree.ElementTree as ET
from pathlib import Path
from capture_usage_screenshots import adb,find_and_tap,require_visible,screenshot,PACKAGE,OUT

def state():
    try:return json.loads(adb('shell','run-as',PACKAGE,'cat','files/search-check.json'))
    except Exception:return {}
def wait(page,count):
    end=time.monotonic()+60
    while time.monotonic()<end:
        s=state()
        if s.get('page')==page and len(s.get('results',[]))==count:return s
        time.sleep(1)
    raise AssertionError({'state':state(),'scan':adb('shell','run-as',PACKAGE,'cat','files/search-scan.json')})
adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
adb('shell','am','force-stop',PACKAGE)
prefs='''<map><string name="search-name">分页测试</string><string name="search-url">http://10.0.2.2:8899/search?q=%s</string><string name="custom-name">分页测试</string><string name="custom-url">http://10.0.2.2:8899/search?q=%s</string></map>'''
Path('search-test-prefs.xml').write_text(prefs,encoding='utf-8');adb('push','search-test-prefs.xml','/data/local/tmp/search-test-prefs.xml')
adb('shell','run-as',PACKAGE,'sh','-c',shlex.quote('mkdir -p shared_prefs; cat /data/local/tmp/search-test-prefs.xml > shared_prefs/player-settings.xml'))
adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity','--es','test-query',shlex.quote('哪吒'),'--es','test-engine',shlex.quote('分页测试'))
expected=json.loads(Path('search-pages-expected.json').read_text(encoding='utf-8'));checks=[]
for page in [1,2,3]:
    st=wait(page,len(expected[str(page)]))
    assert all(r.get('bytes')== (1500000 if 'detail-' in r['magnet'] else 2300000000) for r in st['results']), 'Resource sizes differ from fixture metadata'
    actual={r['magnet'] for r in st['results']};wanted={r[1].replace('2362347540B27D8A4142AF2BBE0A071F26A3B7A1','2362347540b27d8a4142af2bbe0a071f26a3b7a1') for r in expected[str(page)]}
    assert actual==wanted,(actual-wanted,wanted-actual)
    expected_names={magnet.replace('2362347540B27D8A4142AF2BBE0A071F26A3B7A1','2362347540b27d8a4142af2bbe0a071f26a3b7a1'):name for name,magnet in expected[str(page)]}
    assert all(row['title']==expected_names[row['magnet']] for row in st['results']), 'Action text or display-name parameter replaced a real file name'

    require_visible('上一页','下一页','‹ 返回');screenshot('pagination-'+str(page)+'.png')
    checks.append({'check':'source-page-'+str(page)+'-complete','passed':True,'state':st,'expected_count':len(wanted)})
    if page==1:
        assert all(r.get('bytes',0)>0 for r in st['results']),st
        require_visible('第 1 / 3 页','跳转网站页')
        for screen in range(1,3):
            require_visible(f'结果 {screen} / 2 页 · 39 条', st['results'][(screen-1)*20]['title'])
            screenshot('local-results-'+str(screen)+'.png')
            checks.append({'check':'local-twenty-result-screen-'+str(screen),'passed':True,'source_page':1,'screen':screen,'total_results':len(st['results'])})
            if screen<2:find_and_tap('后 20 条')
        for _ in range(1):find_and_tap('前 20 条')
        require_visible('结果 1 / 2 页 · 39 条')
        checks.append({'check':'local-page-back-restores-first-screen','passed':True})
    if page<3:find_and_tap('下一页')
find_and_tap('上一页');st=wait(2,17);checks.append({'check':'previous-page-restores-all-results','passed':True,'state':st});screenshot('pagination-back.png')
find_and_tap('跳转网站页');time.sleep(1);adb('shell','input','text','3');adb('shell','input','keyevent','4');find_and_tap('跳转');wait(3,0)
find_and_tap('跳转网站页');time.sleep(1);adb('shell','input','keyevent','123');adb('shell','input','keyevent','67');adb('shell','input','text','1');adb('shell','input','keyevent','4');find_and_tap('跳转');wait(1,39)
checks.append({'check':'website-page-jump-three-and-one','passed':True})
find_and_tap('跳转结果页');time.sleep(1);adb('shell','input','text','2');adb('shell','input','keyevent','4');find_and_tap('跳转');require_visible('结果 2 / 2 页 · 39 条')
checks.append({'check':'local-result-page-jump','passed':True});screenshot('local-jump-page-2.png')
find_and_tap('‹ 返回');require_visible('解析资源');checks.append({'check':'empty-page-and-back-responsive','passed':True})
(OUT/'pagination-verification.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
print('Complete pagination checks passed')
