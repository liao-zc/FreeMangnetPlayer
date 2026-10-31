import json,re,shlex,time,xml.etree.ElementTree as ET
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
def visible_node_texts():
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    ui=ET.fromstring(adb('shell','cat','/sdcard/window.xml'))
    return [n.attrib.get('text','') for n in ui.iter('node') if n.attrib.get('text')]
def verify_page(page,total,local_total,expected):
    s=wait(page,len(expected))
    assert all(r.get('bytes')==(1500000 if 'unrelated-' in r['magnet'] else 2300000000) for r in s['results']),'Resource sizes differ from fixture metadata'
    assert all(r.get('publishedAt')==('2025-03-01' if 'unrelated-' in r['magnet'] else '2026-10-29') for r in s['results']),'Resource date differs from source metadata'
    actual={r['magnet'] for r in s['results']};wanted={r[1] for r in expected}
    assert actual==wanted,(actual-wanted,wanted-actual)
    hashes=[re.search(r'btih:([0-9a-f]{40})',m).group(1) for m in actual]
    assert len(hashes)==len(set(hashes)),'Duplicate BTIH leaked into results'
    require_visible('上一页','下一页','返回搜索',f'源站第 {page} / 3 页','排序：匹配度')
    text=' '.join(visible_node_texts())
    assert f'第 1 / {local_total} 页' in text, text
    screenshot('pagination-source-'+str(page)+'.png')
    checks.append({'check':'source-page-'+str(page),'passed':True,'result_count':len(s['results']),'local_pages':local_total,'source_total':total})
    return s

adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
adb('shell','am','force-stop',PACKAGE)
prefs='''<map><string name="search-name">分页测试</string><string name="search-url">http://10.0.2.2:8899/search?q=%s</string><string name="custom-name">分页测试</string><string name="custom-url">http://10.0.2.2:8899/search?q=%s</string></map>'''
Path('search-test-prefs.xml').write_text(prefs,encoding='utf-8');adb('push','search-test-prefs.xml','/data/local/tmp/search-test-prefs.xml')
adb('shell','run-as',PACKAGE,'sh','-c',shlex.quote('mkdir -p shared_prefs; cat /data/local/tmp/search-test-prefs.xml > shared_prefs/player-settings.xml'))
adb('shell','am','start','-W','-n',PACKAGE+'/com.omniplayer.MainActivity','--es','test-query',shlex.quote('哪吒'),'--es','test-engine',shlex.quote('分页测试'))
expected=json.loads(Path('search-pages-expected.json').read_text(encoding='utf-8'));checks=[]
s=verify_page(1,3,4,expected['1'])
texts=visible_node_texts();assert sum('哪吒资源文件' in t for t in texts)<=10,'More than 10 resource titles are visible on a page'
adb('shell','input','swipe','500','1900','500','500','450');time.sleep(1);texts=visible_node_texts()
assert any('哪吒资源文件 9' in t for t in texts),'Tenth result cannot be reached on page one'
assert not any('哪吒资源文件 10' in t for t in texts),'Eleventh result leaked into page one'
screenshot('pagination-local-page-1.png')
require_visible('跳转结果页；当前第 1 页，共 4 页',s['results'][0]['title'])
find_and_tap('排序：匹配度');require_visible('时间','热度');screenshot('sort-options.png');find_and_tap('时间');find_and_tap('排序：时间');find_and_tap('热度');find_and_tap('排序：热度');find_and_tap('匹配度')
find_and_tap('下一页');require_visible('第 2 / 4 页');find_and_tap('下一页');find_and_tap('下一页');find_and_tap('下一页')
s=verify_page(2,3,2,expected['2'])
find_and_tap('下一页');require_visible('第 2 / 2 页');find_and_tap('下一页')
verify_page(3,3,1,expected['3'])
find_and_tap('上一页');s=wait(2,len(expected['2']));checks.append({'check':'previous-source-page','passed':True,'result_count':len(s['results'])})
find_and_tap('跳转结果页；当前第 1 页，共 2 页');require_visible('源站第 1 页');find_and_tap('源站第 1 页');wait(1,len(expected['1']))
checks.append({'check':'jump-to-source-page','passed':True})
find_and_tap('返回搜索');require_visible('解析资源');checks.append({'check':'back-restores-search-not-home','passed':True})
(OUT/'pagination-verification.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
print('Search relevance, BTIH de-duplication, 10-item local pages, source paging, jump, and sorting passed')
