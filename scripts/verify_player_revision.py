"""Actual APK checks for every v0.1.12 player/lifecycle request."""
import json
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from capture_usage_screenshots import adb, screenshot, find_and_tap, require_visible, PACKAGE, OUT

fixtures = json.loads(Path('optimization-fixture.json').read_text())
checks = []

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/window.xml'))

def state():
    try:
        return json.loads(adb('shell', 'run-as', PACKAGE, 'cat', 'files/torrent-check.json'))
    except Exception:
        return {}

def wait(predicate, timeout=100):
    start = time.monotonic()
    while time.monotonic() - start < timeout:
        current = state()
        if predicate(current):
            return current
        time.sleep(.2)
    raise AssertionError(state())

def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))

def tap(node):
    x1,y1,x2,y2 = bounds(node)
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def controller(id=None):
    root = tree()
    target = next((n for n in root.iter('node') if n.get('resource-id','').endswith('/'+(id or 'player_position'))), None)
    if target is None:
        surface = next(n for n in root.iter('node') if n.get('resource-id','').endswith('/video_surface'))
        tap(surface)
        root = tree()
        target = next(n for n in root.iter('node') if n.get('resource-id','').endswith('/'+(id or 'player_position')))
    return root,target

def control(id):
    _, node = controller(id)
    tap(node)
    time.sleep(.4)

def open_magnet(value):
    find_and_tap('磁力链接输入框')
    focused = next(n for n in tree().iter('node') if n.get('class')=='android.widget.EditText' and n.get('focused')=='true')
    adb('shell','input','keyevent','KEYCODE_MOVE_END')
    adb('shell','input','keyevent',*(['KEYCODE_DEL']*(len(focused.get('text',''))+5)))
    adb('shell','input','text',shlex.quote(value))
    adb('shell','input','keyevent','KEYCODE_BACK')
    find_and_tap('解析资源')

def record(name, **data):
    checks.append({'check':name,'passed':True,**data})
    (OUT/'player-revision-verification.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')

def main():
    adb('wait-for-device')
    adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
    adb('shell','wm','size','1080x2400')
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','pm','clear',PACKAGE)
    adb('shell','pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
    adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity')
    time.sleep(2)
    # No resource endpoint is returned until exact-source metadata is served.
    # The .torrent contains NO trackers: losing the original magnet tracker
    # means this real seed cannot be connected after replacement.
    fallback = f"magnet:?xt=urn:btih:{fixtures[0]['id']}&tr=http%3A%2F%2F10.0.2.2%3A8888%2Ffallback-announce&xs=http%3A%2F%2F10.0.2.2%3A8888%2Fmetadata.torrent"
    begun=time.monotonic(); open_magnet(fallback)
    recovered=wait(lambda s:s.get('metadata') and s.get('metadataReplacements')==1 and s.get('downloaded',0)>0)
    assert recovered['restoredTrackers']>0 and recovered['outgoingUtp'] is True
    find_and_tap('▶ 播放'); wait(lambda s:s.get('playing'))
    screenshot('revision-00-metadata-peer-recovery.png')
    record('metadata-fallback-preserves-trackers-and-downloads',seconds=time.monotonic()-begun,state=state())
    # Real system clipboard round trip through a focused Android EditText.
    video=next(f for f in state()['files'] if f['index']==state()['currentFile'])
    control('player_files'); find_and_tap('复制磁力：'+video['path'].split('/')[-1]); find_and_tap('完成')
    find_and_tap('返回搜索'); find_and_tap('磁力链接输入框')
    adb('shell','input','keyevent','KEYCODE_MOVE_END')
    focused=next(n for n in tree().iter('node') if n.get('class')=='android.widget.EditText' and n.get('focused')=='true')
    adb('shell','input','keyevent',*(['KEYCODE_DEL']*(len(focused.get('text',''))+5)))
    adb('shell','input','keyevent','KEYCODE_PASTE')
    pasted=next(n.get('text','') for n in tree().iter('node') if n.get('class')=='android.widget.EditText' and n.get('focused')=='true')
    assert 'xt=urn:btih:'+fixtures[0]['id'] in pasted and '&so='+str(video['index']) in pasted
    assert 'fallback-announce' in pasted and '&dn=' in pasted
    screenshot('revision-00b-file-magnet-copy.png')
    record('file-magnet-system-clipboard-roundtrip',copied=pasted,file=video)
    adb('shell','input','keyevent','KEYCODE_BACK')
    find_and_tap('解析资源'); wait(lambda s:s.get('metadata') and s.get('currentFile')==video['index'])
    record('copied-magnet-restores-selected-file',state=state())
    adb('shell','am','force-stop',PACKAGE); adb('shell','pm','clear',PACKAGE)
    adb('shell','pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
    adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity'); time.sleep(2)
    initial_pid=adb('shell','pidof',PACKAGE).strip()
    find_and_tap('设置')
    root=tree()
    fields=[n.get('text') for n in root.iter('node') if n.get('class')=='android.widget.EditText']
    assert '5' in fields, fields
    screenshot('revision-01-default-settings.png')
    record('default-maximum-downloads-five',fields=fields)
    from verify_optimization import edit
    edit('最大缓存任务数量（1–100）','1')
    find_and_tap('保存设置')
    find_and_tap('返回首页')
    start=time.monotonic()
    open_magnet(fixtures[0]['magnet'])
    parsed=wait(lambda s:s.get('id')==fixtures[0]['id'] and s.get('metadata'))
    screenshot('revision-02-real-file-list.png')
    record('pasted-magnet-real-metadata',state=parsed,seconds=time.monotonic()-start)
    find_and_tap('▶ 播放')
    first=wait(lambda s:s.get('playing') and s.get('position',0)>100)
    ratio=first['downloaded']/first['total']
    assert ratio < .20, first
    record('stream-start-below-twenty-percent',state=first,ratio=ratio,seed_upload_limit=128*1024)
    process=adb('shell','pidof',PACKAGE).strip()
    assert process==initial_pid, 'Process restarted during initial parsing/playback'
    root,_=controller()
    surface=next(n for n in root.iter('node') if n.get('resource-id','').endswith('/video_surface'))
    area=bounds(surface)
    ids=['exo_rew','exo_play_pause','exo_ffwd','player_speed','player_files','player_pip','player_fullscreen','player_position','player_duration','player_progress']
    actual={}
    for id in ids:
        node=next(n for n in root.iter('node') if n.get('resource-id','').endswith('/'+id))
        b=bounds(node)
        assert area[0]<=b[0]<b[2]<=area[2] and area[1]<=b[1]<b[3]<=area[3], (id,b,area)
        actual[id]={'bounds':b,'text':node.get('text'),'description':node.get('content-desc')}
    assert state()['duration']>0
    screenshot('revision-03-integrated-controls.png')
    record('all-player-controls-inside-video',surface=area,controls=actual)
    control('exo_play_pause'); wait(lambda s:not s.get('playing'))
    control('exo_play_pause'); wait(lambda s:s.get('playing'))
    control('player_speed'); wait(lambda s:s.get('speed')!=1)
    seek_start=state().get('position',0)
    control('exo_ffwd'); forward=wait(lambda s:s.get('position',0)>seek_start+12000)
    control('exo_rew'); rewind=wait(lambda s:s.get('position',0)<forward['position']-8000)
    assert rewind['position']<forward['position'],(forward,rewind)
    record('embedded-pause-play-speed-and-seek',forward=forward,rewind=rewind)
    _, bar = controller('player_progress')
    x1,y1,x2,y2 = bounds(bar)
    target = state()['duration']*.10
    density=int(re.findall(r'\d+',adb('shell','wm','density'))[-1])
    padding=round(8*density/160)
    adb('shell','input','tap',str(x1+padding+int((x2-x1-2*padding)*.10)),str((y1+y2)//2))
    scrubbed=wait(lambda s:s.get('playing') and abs(s.get('position',0)-target)<6000)
    record('playback-time-duration-and-scrubbable-progress',duration=scrubbed['duration'],target=target,actual=scrubbed['position'],controls=actual)

    control('player_fullscreen'); wait(lambda s:s.get('fullscreen') and s.get('playing'))
    if 'Got it' in ' '.join(n.get('text','') for n in tree().iter('node')): find_and_tap('Got it')
    controller()
    screenshot('revision-04-fullscreen-icons.png')
    record('fullscreen-embedded-small-icons',state=state())
    control('player_pip'); wait(lambda s:s.get('floating') and s.get('playing'))
    before=state()['position']; begun=time.monotonic()
    progressed=wait(lambda s:s.get('floating') and s.get('playing') and s.get('position',0)>before+2000,45)
    after=progressed['position']; pip_elapsed=time.monotonic()-begun
    screenshot('revision-05-real-pip.png')
    windows=adb('shell','dumpsys','window','windows')
    assert 'mode=pinned' in adb('shell','dumpsys','activity','activities')
    player=next(s for s in re.split(r'(?m)^  Window #',windows) if 'com.freemangnetplayer.PlayerActivity' in s and 'mWindowingMode=pinned' in s)
    frame=re.search(r' frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]',player); x1,y1,x2,y2=map(int,frame.groups())
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(.5)
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    wait(lambda s:not s.get('floating') and s.get('playing'))
    record('pip-continues-and-restores',position_before=before,position_after=after,elapsed_seconds=pip_elapsed)
    control('player_fullscreen'); wait(lambda s:not s.get('fullscreen') and s.get('playing'))
    # Switch an unretained file; the previous native writer must stop before deletion.
    first_index=state()['currentFile']
    previous=next(f for f in state()['files'] if f['index']==first_index)
    old_path=state()['folder']+'/'+previous['path']
    control('player_files')
    other=next(f for f in state()['files'] if f['path'].endswith('.mp4') and f['size']>1024 and f['index']!=first_index)
    find_and_tap('切换播放：'+other['path'].split('/')[-1])
    changed=wait(lambda s:s.get('currentFile')==other['index'] and s.get('playing'))
    assert adb('shell','test','!','-e',old_path)==''
    screenshot('revision-06-second-video.png')
    record('switch-file-releases-previous-temporary-bytes',state=changed,removed=old_path)
    # Explicitly retain this torrent, then open another actual magnet through the input.
    find_and_tap('缓存'); wait(lambda s:s.get('retainedCache'))
    retained_path=state()['folder']+'/'+other['path']
    screenshot('revision-07-explicit-cache.png')
    find_and_tap('返回搜索')
    open_magnet(fixtures[1]['magnet']); wait(lambda s:s.get('id')==fixtures[1]['id'] and s.get('metadata'))
    find_and_tap('▶ 播放'); wait(lambda s:s.get('playing'))
    transient=state(); transient_folder=transient['folder']
    assert adb('shell','test','-f',retained_path)==''
    record('explicit-cache-survives-next-resource',retained_path=retained_path,new_state=transient)
    find_and_tap('缓存')
    denied=wait(lambda s:not s.get('retainedCache') and '最大缓存数量' in s.get('playerError',''))
    assert adb('shell','test','-f',retained_path)==''
    screenshot('revision-07b-cache-limit.png')
    record('cache-limit-preserves-explicit-cache',state=denied,retained_path=retained_path)

    find_and_tap('返回搜索')
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity')
    time.sleep(2)
    process=adb('shell','pidof',PACKAGE).strip()
    open_magnet(fixtures[2]['magnet']); wait(lambda s:s.get('id')==fixtures[2]['id'] and s.get('metadata'))
    find_and_tap('▶ 播放'); wait(lambda s:s.get('playing'))
    time.sleep(2)
    assert adb('shell','test','!','-d',transient_folder)==''
    screenshot('revision-08-new-resource.png')
    record('new-resource-clears-previous-unretained-cache',removed=transient_folder,state=state(),after_deliberate_system_stop=True)
    assert adb('shell','test','-f',retained_path)==''
    record('temporary-cleanup-and-explicit-retention-after-restart',removed=transient_folder,retained_path=retained_path)
    # Stress the real fullscreen + decoder lifecycle, preserving the same process.
    for i in range(6):
        control('player_fullscreen'); wait(lambda s:s.get('fullscreen') and s.get('playing'))
        control('player_fullscreen'); wait(lambda s:not s.get('fullscreen') and s.get('playing'))
        assert adb('shell','pidof',PACKAGE).strip()==process
    logs=adb('logcat','-d')
    assert not re.search(r'FATAL EXCEPTION.*|Fatal signal.*com\.freemangnetplayer',logs)
    screenshot('revision-09-lifecycle-stress.png')
    record('no-crash-or-process-restart-during-playback-stress',pid=process,cycles=6)
    control('player_files')
    find_and_tap('切换播放：Broken.mp4')
    failed=wait(lambda s:not s.get('playing') and not s.get('playRequested') and '播放失败' in s.get('playerError',''))
    time.sleep(5)
    assert adb('shell','pidof',PACKAGE).strip()==process
    screenshot('revision-09b-media-error.png')
    record('invalid-media-shows-error-without-process-restart',state=failed,pid=process)

    find_and_tap('返回搜索')
    # Same exact user hash, latest app and previous stable APK, fresh app data.
    import urllib.request
    user_magnet='magnet:?xt=urn:btih:f1006d95049a840aa34cec95967ea590bf5ffeaf'
    def user_observation(version):
        begun=time.monotonic(); open_magnet(user_magnet); samples=[]; requested=False; first_frame=None
        for _ in range(150):
            st=state(); samples.append({**st,'seconds':time.monotonic()-begun})
            if st.get('metadata') and not requested:
                find_and_tap('▶ 播放'); requested=True
            if st.get('playing') and st.get('position',0)>100:
                first_frame=time.monotonic()-begun; break
            time.sleep(1)
        screenshot('user-resource-'+version+'.png')
        checks.append({'check':'user-resource-'+version,'measured':True,'magnet':user_magnet,'first_frame_seconds':first_frame,'state':state(),'samples':samples})
        (OUT/'player-revision-verification.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    user_observation('v0.1.13')
    urllib.request.urlretrieve('https://github.com/liao-zc/FreeMangnetPlayer/releases/download/v0.1.12/FreeMangnetPlayer-v0.1.12.apk','previous-app.apk')
    adb('shell','am','force-stop',PACKAGE); adb('shell','pm','clear',PACKAGE)
    adb('install','-r','-d','previous-app.apk'); adb('shell','pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
    adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity'); time.sleep(2)
    user_observation('v0.1.12')
    adb('shell','am','force-stop',PACKAGE); adb('shell','pm','clear',PACKAGE)
    adb('install','-r','app/build/outputs/apk/debug/app-debug.apk'); adb('shell','pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
    adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity'); time.sleep(2)
    # A real pure hash may not be publicly discoverable; preserve the actual observation.
    public=json.loads(Path('public-network-baseline.json').read_text())
    open_magnet(public['plain_magnet'])
    samples=[]
    for i in range(75):
        samples.append(state()); time.sleep(1)
    screenshot('revision-10-public-magnet.png')
    checks.append({'check':'public-pure-magnet-discovery','measured':True,'state':state(),'samples':samples,'native_baseline':public})
    find_and_tap('重试解析 / 连接'); require_visible('返回搜索')
    screenshot('revision-11-retry-responsive.png')
    find_and_tap('返回搜索'); require_visible('解析资源')
    record('magnet-retry-and-back-remain-responsive')

try:
    main()
finally:
    screenshot('revision-last-screen.png')
    (OUT/'revision-last-ui.xml').write_text(ET.tostring(tree(),encoding='unicode'),encoding='utf-8')
    (OUT/'player-revision-verification.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'player-revision-last-state.json').write_text(json.dumps(state(),ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'player-revision-logcat.txt').write_text(adb('logcat','-d'),encoding='utf-8')
