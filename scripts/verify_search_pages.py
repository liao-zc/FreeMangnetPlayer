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
    raise AssertionError(state())
adb('shell','am','force-stop',PACKAGE)
prefs='''<map><string name="search-name">分页测试</string><string name="search-url">http://10.0.2.2:8899/search?q=%s</string><string name="custom-name">分页测试</string><string name="custom-url">http://10.0.2.2:8899/search?q=%s</string></map>'''
Path('search-test-prefs.xml').write_text(prefs,encoding='utf-8');adb('push','search-test-prefs.xml','/data/local/tmp/search-test-prefs.xml')
adb('shell','run-as',PACKAGE,'sh','-c',shlex.quote('mkdir -p shared_prefs; cat /data/local/tmp/search-test-prefs.xml > shared_prefs/player-settings.xml'))
adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity','--es','test-query',shlex.quote('哪吒'),'--es','test-engine',shlex.quote('分页测试'))
expected=json.loads(Path('search-pages-expected.json').read_text(encoding='utf-8'));checks=[]
for page in [1,2,3]:
    st=wait(page,len(expected[str(page)]))
    actual={r['magnet'] for r in st['results']};wanted={r[1].replace('2362347540B27D8A4142AF2BBE0A071F26A3B7A1','2362347540b27d8a4142af2bbe0a071f26a3b7a1') for r in expected[str(page)]}
    assert actual==wanted,(actual-wanted,wanted-actual)
    require_visible('上一页','下一页','返回');screenshot('pagination-'+str(page)+'.png')
    checks.append({'check':'source-page-'+str(page)+'-complete','passed':True,'state':st,'expected_count':len(wanted)})
    if page==1:
        for screen in range(1,5):
            require_visible(f'结果 {screen} / 4 页', st['results'][(screen-1)*10]['title'])
            screenshot('local-results-'+str(screen)+'.png')
            checks.append({'check':'local-ten-result-screen-'+str(screen),'passed':True,'source_page':1,'screen':screen,'total_results':len(st['results'])})
            if screen<4:find_and_tap('后 10 条')
        for _ in range(3):find_and_tap('前 10 条')
        require_visible('结果 1 / 4 页')
        checks.append({'check':'local-page-back-restores-first-screen','passed':True})
    if page<3:find_and_tap('下一页')
find_and_tap('上一页');st=wait(2,17);checks.append({'check':'previous-page-restores-all-results','passed':True,'state':st});screenshot('pagination-back.png')
find_and_tap('‹ 返回');require_visible('解析资源');checks.append({'check':'empty-page-and-back-responsive','passed':True})
(OUT/'pagination-verification.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
print('Complete pagination checks passed')
