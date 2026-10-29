"""Real APK checks for cache rediscovery, queue, quality semantics and deletion."""
import json,re,shlex,time,xml.etree.ElementTree as ET
from pathlib import Path
from capture_usage_screenshots import adb,screenshot,find_and_tap,require_visible,PACKAGE,OUT

checks=[]
def state():
    try:return json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
    except Exception:return {}
def wait(fn,timeout=120):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        s=state()
        if fn(s):return s
        time.sleep(.3)
    raise AssertionError(state())
def record(name,**data):
    checks.append({'check':name,'passed':True,**data});(OUT/'library-verification.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
def tree():
    adb('shell','uiautomator','dump','/sdcard/window.xml');return ET.fromstring(adb('shell','cat','/sdcard/window.xml'))
def tap(n):
    b=list(map(int,re.findall(r'\d+',n.get('bounds',''))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
def control(name):
    for _ in range(15):
        t=tree();n=next((n for n in t.iter('node') if n.get('resource-id','').endswith('/'+name)),None)
        if n is not None:tap(n);time.sleep(.4);return
        n=next((n for n in t.iter('node') if n.get('resource-id','').endswith('/video_surface')),None)
        if n is not None:tap(n)
        time.sleep(.3)
    raise AssertionError('Missing built-in control '+name)
def open_cache(title):
    root=tree();parents={child:parent for parent in root.iter() for child in parent}
    node=next(n for n in root.iter('node') if n.get('text')==title)
    while node is not None:
        button=next((n for n in node.iter('node') if n.get('text')=='播放 / 打开'),None)
        if button is not None:tap(button);return
        node=parents.get(node)
    raise AssertionError('No playback action for cached file '+title)
def select_cache(title):
    root=tree();parents={child:parent for parent in root.iter() for child in parent}
    node=next(n for n in root.iter('node') if n.get('text')==title)
    while node is not None:
        checkbox=next((n for n in node.iter('node') if n.get('class')=='android.widget.CheckBox'),None)
        if checkbox is not None:tap(checkbox);return
        node=parents.get(node)
    raise AssertionError('No selector for '+title)
def prefs():
    return ET.fromstring(adb('shell','run-as',PACKAGE,'cat','shared_prefs/player-settings.xml'))
def entries(key):
    n=next((n for n in prefs() if n.get('name')==key),None);return json.loads(n.text or '[]') if n is not None else []
def caches():return entries('cache-entries')
def restart(tab='播放'):
    adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity','--es','open-tab',shlex.quote(tab));time.sleep(3)
def poll(fn,timeout=30):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        if fn():return
        time.sleep(.5)
    raise AssertionError('Persistence/cleanup condition failed')

adb('install','-r','app/build/outputs/apk/debug/app-debug.apk');adb('shell','pm','clear',PACKAGE)
adb('shell','wm','size','1080x2400');adb('shell','pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
fixture=json.loads(Path('optimization-fixture.json').read_text())[0]
episode=next(i for i,f in enumerate(fixture['files']) if f['path'].endswith('Episode-01.mp4'))
source=fixture['magnet']+'&so='+str(episode)+'&xs=http%3A%2F%2F10.0.2.2%3A8888%2Fmetadata.torrent'
adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity','-a','android.intent.action.VIEW','-d',shlex.quote(source),'--es','mode','play')
played=wait(lambda s:s.get('playing') and s.get('position',0)>800)
assert played['qualityHeight']==0;record('default-original-quality',state=played)
control('player_quality');require_visible('原画质','720p','480p');screenshot('library-01-quality-options.png');find_and_tap('原画质')
record('quality-control-inside-player-with-no-transcoding-claim')
control('player_files');find_and_tap('全选');find_and_tap('完成')
find_and_tap('缓存');poll(lambda:len(caches())>=2);screenshot('library-02-retained-files.png')
record('explicit-cache-records-files-before-completion',entries=caches())
partial=wait(lambda s:any(f['path'].endswith('Episode-02.mp4') and 0<f['done']<f['size'] for f in s.get('files',[])))
assert any(q['index']==next(f['index'] for f in partial['files'] if f['path'].endswith('Episode-02.mp4')) for q in partial['storedQueue'])
assert all(q['done']>0 for q in partial['storedQueue'])
record('partial-downloaded-video-auto-enters-queue',state=partial)
control('player_files');require_visible('已缓存 / 下载的视频','Episode-02.mp4');screenshot('library-03-stored-queue.png')
find_and_tap('完成');control('player_next');switched=wait(lambda s:s.get('playing') and any(f['index']==s.get('currentFile') and f['path'].endswith('Episode-02.mp4') for f in s.get('files',[])))
record('next-control-switches-to-partial-video',state=switched);screenshot('library-04-queue-playing.png')
old_folder=state()['folder'];find_and_tap('下载');wait(lambda s:s.get('folder')!=old_folder and s.get('playing'));poll(lambda:len(entries('download-entries'))>=2);record('download-library-indexes-individual-files',entries=entries('download-entries'))
find_and_tap('收藏');record('favorite-file-record',entries=entries('favorite-entries'))
restart();require_visible('缓存视频');find_and_tap('缓存视频');require_visible('Episode-01.mp4','Episode-02.mp4');screenshot('library-05-cache-after-restart.png')
record('cache-entry-discoverable-after-process-restart',entries=caches())
open_cache('Episode-01.mp4');resumed=wait(lambda s:s.get('playing'));record('cached-file-reopens-and-plays',state=resumed)
control('player_files');find_and_tap('切换播放：MultiQuality.mp4')
high=wait(lambda s:s.get('playing') and s.get('videoHeight')==720)
control('player_quality');find_and_tap('480p');low=wait(lambda s:s.get('playing') and s.get('qualityHeight')==480 and s.get('videoHeight')==360)
record('existing-lower-resolution-track-selects-real-360p',before=high,after=low);screenshot('library-10-low-quality-track.png')
control('player_quality');find_and_tap('原画质');wait(lambda s:s.get('qualityHeight')==0 and s.get('videoHeight')==720)
record('original-quality-restores-highest-track')
restart('收藏');require_visible('我的收藏');find_and_tap('全选');find_and_tap('删除所选');find_and_tap('确认删除');poll(lambda:not entries('favorite-entries'))
assert caches();record('favorite-delete-keeps-cache-files');screenshot('library-06-favorite-delete.png')
restart('播放');find_and_tap('全选');find_and_tap('删除所选');find_and_tap('确认删除');poll(lambda:not entries('playback-entries'))
assert caches();record('batch-history-delete-keeps-cache')
find_and_tap('缓存视频');first=next(r for r in caches() if r['title']=='Episode-01.mp4' and r['downloaded']>0);others=[r for r in caches() if r['source']!=first['source']]
tasks=next(n for n in prefs() if n.get('name')=='torrent-tasks');taskrow=next(t for t in json.loads(tasks.text) if t['id'].lower()==re.search(r'btih:([a-fA-F0-9]+)',first['source'])[1].lower())
fileindex=int(re.search(r'[?&]so=(\d+)',first['source'])[1]);path=taskrow['folder']+'/'+next(f['path'] for f in taskrow['files'] if f['index']==fileindex)
import subprocess
print('Checking stored file:',path,flush=True)
subprocess.run(['adb','shell','ls','-la',shlex.quote(taskrow['folder'])])
assert subprocess.run(['adb','shell','test','-f',shlex.quote(path)]).returncode==0
# Select exactly one checkbox, then delete that file rather than the whole torrent.
select_cache(first['title']);find_and_tap('删除所选');find_and_tap('确认删除')
poll(lambda:all(r['source']!=first['source'] for r in caches()))
assert all(any(c['source']==r['source'] for c in caches()) for r in others)
poll(lambda:subprocess.run(['adb','shell','test','-f',shlex.quote(path)]).returncode!=0)
record('single-cached-file-delete-preserves-other-files',deleted=first,remaining=caches());screenshot('library-07-single-delete.png')
find_and_tap('全选');find_and_tap('删除所选');find_and_tap('确认删除');poll(lambda:not caches());record('batch-cache-file-delete');screenshot('library-08-batch-delete.png')
(OUT/'library-final-state.json').write_text(json.dumps(state(),ensure_ascii=False,indent=2),encoding='utf-8')
restart('下载');find_and_tap('全选');find_and_tap('删除所选');find_and_tap('确认删除');poll(lambda:not entries('download-entries'));record('batch-download-file-deletion');screenshot('library-09-download-delete.png')
print('Observed library regression checks passed')
