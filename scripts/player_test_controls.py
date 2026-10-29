"""Real touch helpers for automatically hidden PlayerView controls."""
import re
from capture_usage_screenshots import adb
import xml.etree.ElementTree as ET

def reveal_for_label(label):
    names={'全屏播放':'player_fullscreen','退出全屏':'player_fullscreen','悬浮播放':'player_pip',
           '切换视频':'player_files','▶ 播放':'exo_play_pause','后退 15 秒':'exo_rew','前进 15 秒':'exo_ffwd'}
    id=names.get(label)
    if re.fullmatch(r'\d+(?:\.\d+)?×',label): id='player_speed'
    if id is None: return None
    def read():
        adb('shell','uiautomator','dump','/sdcard/window.xml')
        return ET.fromstring(adb('shell','cat','/sdcard/window.xml'))
    root=read()
    node=next((n for n in root.iter('node') if n.get('resource-id','').endswith('/'+id)),None)
    if node is None:
        surface=next((n for n in root.iter('node') if n.get('resource-id','').endswith('/video_surface')),None)
        if surface is None: return None
        x1,y1,x2,y2=map(int,re.findall(r'\d+',surface.get('bounds')))
        adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
        root=read()
        node=next((n for n in root.iter('node') if n.get('resource-id','').endswith('/'+id)),None)
    if node is None: return None
    return list(map(int,re.findall(r'\d+',node.get('bounds'))))

def tap_player(label):
    b=reveal_for_label(label)
    if b is None: return False
    x1,y1,x2,y2=b
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    return True
