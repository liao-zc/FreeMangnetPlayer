"""Capture actual reference/our APK behavior on the same emulator and user magnet."""
import json,os,re,shlex,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
from capture_usage_screenshots import adb,screenshot,visible_text,PACKAGE,OUT
from test_resource import USER_MAGNET

def snap(name):
    screenshot(name+'.png');adb('shell','uiautomator','dump','/sdcard/window.xml')
    (OUT/(name+'.xml')).write_text(adb('shell','cat','/sdcard/window.xml'),encoding='utf-8')

def nodes():
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    return list(ET.fromstring(adb('shell','cat','/sdcard/window.xml')).iter('node'))

def tap(node):
    b=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
    if len(b)==4:adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(1)

badging=subprocess.check_output([os.environ['ANDROID_HOME']+'/build-tools/35.0.0/aapt','dump','badging','reference.apk'],text=True)
ref=re.search(r"package: name='([^']+)'",badging)[1]
result={'magnet':USER_MAGNET,'reference_package':ref,'reference_badging':badging,'native_bridge':adb('shell','getprop','ro.dalvik.vm.native.bridge'),'observations':[]}
install=subprocess.run(['adb','install','-r','reference.apk'],capture_output=True,text=True)
result['reference_install']={'returncode':install.returncode,'output':install.stdout+install.stderr}
if install.returncode==0:
    adb('shell','monkey','-p',ref,'1');time.sleep(15);snap('compare-01-reference-home')
    launch=subprocess.run(['adb','shell','am','start','-W','-a','android.intent.action.VIEW','-d',USER_MAGNET,'-p',ref],capture_output=True,text=True)
    result['reference_magnet_intent']=launch.stdout+launch.stderr
    time.sleep(4)
    # If the app has no magnet intent, open its visible import control, then type.
    if 'Error' in launch.stdout+launch.stderr:
        for node in nodes():
            label=node.get('text','')+' '+node.get('content-desc','')
            if node.get('clickable')=='true' and any(t in label for t in ['添加磁力','磁力链接','添加链接','新增','添加','Add','+']):tap(node);break
        time.sleep(2)
    edits=[n for n in nodes() if n.get('class')=='android.widget.EditText']
    if edits:
        tap(edits[0]);adb('shell','input','keycombination','113','29');adb('shell','input','text',shlex.quote(USER_MAGNET));adb('shell','input','keyevent','KEYCODE_BACK')
        for node in nodes():
            if node.get('clickable')=='true' and node.get('text','') in ['确定','确认','解析','播放','添加','OK','Play']:tap(node);break
    snap('compare-02-reference-import')
    start=time.monotonic();samples=[]
    for i in range(24):
        time.sleep(5);samples.append({'seconds':time.monotonic()-start,'ui':visible_text()})
    snap('compare-03-reference-resource')
    result['observations'].append({'app':'Magnet_player','samples':samples,'actual_ui':visible_text()})
    adb('shell','am','force-stop',ref)
adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
adb('shell','am','start','-W','-n',PACKAGE+'/.PlayerActivity','--es','source',shlex.quote(USER_MAGNET),'--es','mode','play')
start=time.monotonic();samples=[]
for i in range(24):
    time.sleep(5)
    try:st=json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
    except Exception:st={}
    samples.append({'seconds':time.monotonic()-start,'state':st})
snap('compare-04-our-resource')
result['observations'].append({'app':'FreeMangnetPlayer','samples':samples,'actual_ui':visible_text()})
result['logcat']=adb('logcat','-d','-t','3000')
(OUT/'comparison-probe.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print('Actual comparison probe captured',ref,result['native_bridge'])
