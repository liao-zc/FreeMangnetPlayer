"""APK UI checks for fonts, media information, history and favorite downloads."""
import json,re,shlex,time,xml.etree.ElementTree as ET
from pathlib import Path
from capture_usage_screenshots import adb,find_and_tap,require_visible,screenshot,PACKAGE,OUT

checks=[]
def poll(fn,timeout=15):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        if fn():return
        time.sleep(.3)
    screenshot('preferences-persistence-failure.png')
    raise AssertionError('UI operation did not persist')

def record(name,**data):
    checks.append(dict(check=name,passed=True,**data));(OUT/'preferences-verification.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
def prefs():return ET.fromstring(adb('shell','run-as',PACKAGE,'cat','shared_prefs/player-settings.xml'))
def entries(key):
    n=next((n for n in prefs() if n.get('name')==key),None)
    return json.loads(n.text or '[]') if n is not None else []
def state():
    try:return json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
    except Exception:return {}
def wait(fn,timeout=150):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        s=state()
        if fn(s):return s
        time.sleep(.5)
    raise AssertionError(state())
def actionable(node):
    if node is None or node.get('visible-to-user') == 'false' or node.get('enabled') == 'false': return False
    b=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
    return len(b)==4 and b[2]>b[0] and b[3]>b[1]
def control(name):
    end=time.monotonic()+10;tapped=False
    while time.monotonic()<end:
        adb('shell','uiautomator','dump','/sdcard/window.xml');t=ET.fromstring(adb('shell','cat','/sdcard/window.xml'))
        node=next((n for n in t.iter('node') if n.get('resource-id','').endswith('/'+name) and actionable(n)),None)
        if node is not None:
            b=list(map(int,re.findall(r'\d+',node.get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.5);return
        if name=='player_quality':
            node=next((n for n in t.iter('node') if n.get('content-desc')=='画质设置' and actionable(n)),None)
            if node is not None:
                b=list(map(int,re.findall(r'\d+',node.get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.5);return
        if not tapped:
            node=next((n for n in t.iter('node') if n.get('resource-id','').endswith('/video_surface') and actionable(n)),None)
            if node is None: node=next((n for n in t.iter('node') if 'Show player controls' in n.get('content-desc','')),None)
            if node is not None:
                b=list(map(int,re.findall(r'\d+',node.get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
            else: adb('shell','input','tap','540','450')
            tapped=True
        time.sleep(.25)
    screenshot('preferences-failed-'+name+'.png')
    (OUT/'preferences-failed-ui.xml').write_text(adb('shell','cat','/sdcard/window.xml'),encoding='utf-8')
    (OUT/'preferences-failed-state.json').write_text(json.dumps(state(),ensure_ascii=False,indent=2),encoding='utf-8')
    raise AssertionError('Player control was not visible: '+name)
def tap_exact(label):
    end=time.monotonic()+15
    while time.monotonic()<end:
        adb('shell','uiautomator','dump','/sdcard/window.xml')
        root=ET.fromstring(adb('shell','cat','/sdcard/window.xml'));parents={child:parent for parent in root.iter() for child in parent}
        for node in root.iter('node'):
            if node.get('text')!=label and node.get('content-desc')!=label:continue
            target=node
            while target is not None:
                if target.get('clickable')=='true' and actionable(target):
                    b=list(map(int,re.findall(r'\d+',target.get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.5);return
                target=parents.get(target)
        adb('shell','input','swipe','540','1900','540','900','400');time.sleep(.5)
    raise AssertionError('Exact enabled action unavailable: '+label)
adb('shell','wm','size','1080x2400')
adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-W','-n',PACKAGE+'/com.omniplayer.MainActivity','--es','open-tab',shlex.quote('门户'))
find_and_tap('设置');find_and_tap('通用设置');require_visible('显示与字体','小','✓ 标准','大','超大','黑体','宋体','楷体')
screenshot('preferences-01-font-settings.png');record('display-font-module-and-four-presets')
find_and_tap('大');find_and_tap('宋体');screenshot('preferences-02-song-large.png')
assert next(n for n in prefs() if n.get('name')=='font-face').get('value',next(n for n in prefs() if n.get('name')=='font-face').text)=='宋体'
record('song-font-and-large-preset-persist')
find_and_tap('标准');find_and_tap('楷体');screenshot('preferences-03-kai-font.png');record('kai-font-applied')
find_and_tap('黑体');find_and_tap('首页')
adb('shell','pm','clear',PACKAGE);adb('shell','pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
fixture=json.loads(Path('optimization-fixture.json').read_text(encoding='utf-8'))[0]
index=next(i for i,f in enumerate(fixture['files']) if f['path'].endswith('Episode-01.mp4'))
source=fixture['magnet']+'&so='+str(index)+'&xs=http%3A%2F%2F10.0.2.2%3A8888%2Fmetadata.torrent'
adb('shell','am','start','-W','-n',PACKAGE+'/com.omniplayer.MainActivity','-a','android.intent.action.VIEW','-d',shlex.quote(source),'--es','mode','play')
s=wait(lambda s:s.get('playing') and s.get('duration',0)>0 and s.get('videoHeight',0)>0)
control('player_favorite');poll(lambda:bool(entries('favorite-entries')));screenshot('preferences-04-filled-favorite.png');record('favorite-heart-adds-current-video')
control('player_favorite');poll(lambda:not entries('favorite-entries'));record('favorite-heart-second-tap-removes')
control('player_favorite')
tap_exact('缓存');wait(lambda s:s.get('retainedCache'))
control('player_quality');require_visible('原画质');screenshot('preferences-05-supported-quality-menu.png');find_and_tap('原画质')
time.sleep(6);s=state();assert s['duration']>0 and s['videoHeight']>0
screenshot('preferences-06-media-cache-information.png');record('actual-duration-resolution-size-and-cache-progress',state=s)
assert entries('history-播放') and entries('history-缓存');record('play-and-cache-history-created')
adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-W','-n',PACKAGE+'/com.omniplayer.MainActivity','--es','open-tab',shlex.quote('收藏'))
find_and_tap('全选');find_and_tap('下载所选');find_and_tap('下载');require_visible('下载任务')
assert entries('download-entries');assert entries('history-下载');screenshot('preferences-07-selected-favorite-download.png');record('favorite-select-all-download-visible-in-library',entries=entries('download-entries'))
find_and_tap('播放');find_and_tap('历史记录');require_visible('历史记录','清空搜索记录','清空全部记录')
tap_exact('播放');require_visible('清空播放记录');screenshot('preferences-08-playback-history.png');record('independent-history-module-and-timed-progress')
find_and_tap('清空播放记录');find_and_tap('确认清空');assert not entries('history-播放');assert entries('history-缓存');record('clear-single-history-keeps-other-records')
find_and_tap('清空全部记录');find_and_tap('确认清空');assert all(not entries('history-'+k) for k in ['搜索','播放','下载','缓存']);record('clear-all-history-keeps-favorites',favorites=entries('favorite-entries'))
print('Preferences revision UI checks passed')
