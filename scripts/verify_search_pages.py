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
adb('shell','am','start','-W','-n',PACKAGE+'/com.omniplayer.MainActivity','--es','test-query',shlex.quote('哪吒'),'--es','test-engine',shlex.quote('分页测试'))
expected=json.loads(Path('search-pages-expected.json').read_text(encoding='utf-8'));checks=[]
for page in [1,2,3]:
    st=wait(page,len(expected[str(page)]))
    assert all(r.get('bytes')==(1500000 if 'detail-' in r['magnet'] else 2300000000) for r in st['results']),'Resource sizes differ from fixture metadata'
    assert all(r.get('publishedAt')==('2025-03-01' if 'detail-' in r['magnet'] else '2026-10-29') for r in st['results']),'Resource date differs from source metadata'
    actual={r['magnet'] for r in st['results']};wanted={r[1].replace('2362347540B27D8A4142AF2BBE0A071F26A3B7A1','2362347540b27d8a4142af2bbe0a071f26a3b7a1') for r in expected[str(page)]}
    assert actual==wanted,(actual-wanted,wanted-actual)
    names={m.replace('2362347540B27D8A4142AF2BBE0A071F26A3B7A1','2362347540b27d8a4142af2bbe0a071f26a3b7a1'):n for n,m in expected[str(page)]}
    assert all(row['title']==names[row['magnet']] for row in st['results']),'Real filename was not preserved'
    require_visible('上一页','下一页','返回搜索',f'{page} / 3 页','排序：匹配度')
    screenshot('pagination-'+str(page)+'.png')
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    ui=ET.fromstring(adb('shell','cat','/sdcard/window.xml'))
    assert sum(1 for n in ui.iter('node') if n.attrib.get('text')=='搜索结果')==1,'duplicate search-result heading'
    assert sum(1 for n in ui.iter('node') if n.attrib.get('text')=='设置' or n.attrib.get('content-desc')=='设置')<=1,'duplicate settings button'
    checks.append({'check':'source-page-'+str(page),'passed':True,'result_count':len(st['results'])})
    if page==1:
        require_visible('跳转页面；当前第 1 页',st['results'][0]['title'],st['results'][-1]['title'])
        screenshot('all-results-one-page.png')
        find_and_tap('排序：匹配度');require_visible('时间','热度');find_and_tap('时间')
        find_and_tap('排序：时间');find_and_tap('热度');find_and_tap('排序：热度');find_and_tap('匹配度')
        checks.append({'check':'single-page-controls-and-time-heat-match-sorting','passed':True,'result_count':len(st['results'])})
    if page<3:find_and_tap('下一页')
find_and_tap('上一页');st=wait(2,len(expected['2']));checks.append({'check':'previous-page','passed':True,'result_count':len(st['results'])});screenshot('pagination-back.png')
find_and_tap('跳转页面；当前第 2 页');time.sleep(1);adb('shell','input','text','1');adb('shell','input','keyevent','4');find_and_tap('跳转');wait(1,len(expected['1']))
checks.append({'check':'jump-to-known-page','passed':True})
find_and_tap('返回搜索');require_visible('解析资源');checks.append({'check':'back-restores-search-not-home','passed':True})
(OUT/'pagination-verification.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
print('Search pagination and sorting checks passed')