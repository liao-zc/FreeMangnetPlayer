"""Try stock Flutter renderer options, then compare actual same-keyword searches."""
import json,os,re,shlex,subprocess,time,xml.etree.ElementTree as ET
from xml.sax.saxutils import escape
from pathlib import Path
from capture_usage_screenshots import adb,screenshot,visible_text,PACKAGE,OUT

keyword='Avengers'
result={'keyword':keyword,'api':adb('shell','getprop','ro.build.version.sdk').strip(),'reference_attempts':[],'searches':[]}
badging=subprocess.check_output([os.environ['ANDROID_HOME']+'/build-tools/35.0.0/aapt','dump','badging','reference.apk'],text=True)
ref=re.search(r"package: name='([^']+)'",badging)[1]
activity=re.search(r"launchable-activity: name='([^']+)'",badging)[1]
adb('install','-r','reference.apk')
def save():
    (OUT/'same-search-comparison.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
def tree():
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    return list(ET.fromstring(adb('shell','cat','/sdcard/window.xml')).iter('node'))
def tap(n):
    b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
    if len(b)==4:adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(1)
running=False
for name,flags in [('default',[]),('opengl',['--ez','enable-impeller','false']),('software',['--ez','enable-software-rendering','true','--ez','enable-impeller','false'])]:
    adb('shell','am','force-stop',ref);adb('logcat','-c')
    launch=adb('shell','am','start','-W','-n',ref+'/'+activity,*flags)
    time.sleep(10)
    pid=adb('shell','pidof',ref).strip() if subprocess.run(['adb','shell','pidof',ref],capture_output=True).returncode==0 else ''
    ui=visible_text();screenshot('search-reference-'+name+'.png')
    log=adb('logcat','-d','-t','2000')
    # Keep renderer/process diagnostics only; avoid unrelated accounts or network tokens.
    diagnostic='\n'.join(l for l in log.splitlines() if any(w in l for w in [ref,'flutter','Fatal signal','FATAL EXCEPTION','ndk_translation','UnsatisfiedLinkError']) and not any(w in l.lower() for w in ['token','bearer','authorization']))
    result['reference_attempts'].append({'renderer':name,'launch':launch,'process':pid,'ui':ui,'diagnostic':diagnostic});save()
    if pid and any(n.get('package')==ref for n in tree()):running=True;break
if running:
    # Enter a genuine visible search field; never treat a launcher as search results.
    for n in tree():
        if '搜索' in n.get('text','')+n.get('content-desc','') and n.get('clickable')=='true':tap(n);break
    edits=[n for n in tree() if n.get('class')=='android.widget.EditText']
    if edits:
        tap(edits[0]);adb('shell','input','text',keyword);adb('shell','input','keyevent','KEYCODE_ENTER')
        for n in tree():
            if n.get('text') in ['搜索','Search'] and n.get('clickable')=='true':tap(n);break
        time.sleep(30);ui=visible_text();screenshot('search-reference-results.png')
        result['searches'].append({'app':'Magnet_player','ui':ui,'hashes':sorted(set(re.findall(r'(?i)btih:([a-f0-9]{40})',ui))),'submitted':True})
    else:result['reference_search_error']='No accessible editable search field; no search comparison claimed.'
else:result['reference_search_error']='Reference process exits before usable UI on this emulator.'
save();adb('shell','am','force-stop',ref)
adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
source=Path('app/src/main/java/com/freemangnetplayer/MainActivity.kt').read_text(encoding='utf-8')
engines=re.findall(r'SearchProvider\("([^"]+)",',source)[:6]
for engine in engines:
    adb('shell','am','force-stop',PACKAGE)
    homepage=re.search(r'SearchProvider\("'+re.escape(engine)+r'", "([^"]+)"',source)[1]
    prefs='<map><string name="search-name">'+escape(engine)+'</string><string name="search-url">'+escape(homepage)+'</string></map>'
    Path('same-search-prefs.xml').write_text(prefs,encoding='utf-8')
    adb('push','same-search-prefs.xml','/data/local/tmp/same-search-prefs.xml')
    adb('shell','run-as',PACKAGE,'sh','-c',shlex.quote('mkdir -p shared_prefs; cat /data/local/tmp/same-search-prefs.xml > shared_prefs/player-settings.xml; rm -f files/search-check.json'))
    adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity','--es','test-query',keyword,'--es','test-engine',shlex.quote(engine))
    start=time.monotonic();state={}
    while time.monotonic()-start<45:
        try:state=json.loads(adb('shell','run-as',PACKAGE,'cat','files/search-check.json'))
        except Exception:state={}
        time.sleep(1)
    screenshot('search-ours-'+str(len(result['searches']))+'.png')
    result['searches'].append({'app':'FreeMangnetPlayer','requested_engine':engine,'seconds':time.monotonic()-start,'state':state,'ui':visible_text()});save()
print('Observed same-keyword search attempts; comparison requires matching actual hashes, not titles.')
