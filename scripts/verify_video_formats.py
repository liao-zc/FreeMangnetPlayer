import hashlib,json,re,shlex,subprocess,time
from pathlib import Path
from capture_usage_screenshots import adb,screenshot,PACKAGE,OUT

# Google APIs emulator is userdebug; root is only used by the verification harness
# to hash app-scoped external files without scoped-storage shell restrictions.
adb('root')
adb('wait-for-device')
rows=[]
for fixture in json.loads(Path('format-fixtures.json').read_text()):
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','am','start','-W','-n',PACKAGE+'/com.omniplayer.MainActivity','--es','test-torrent-url',shlex.quote(fixture['source']),'--es','mode','play','--es','title','Video.'+fixture['extension'])
    start=time.monotonic();st={};played=False
    original_pid=adb("shell","pidof",PACKAGE).strip()
    while time.monotonic()-start<90:
        try:st=json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
        except Exception:st={}
        if fixture.get('invalid') and st.get('source')==fixture['source'] and not st.get('playRequested') and '播放失败' in st.get('playerError',''):break
        if st.get('source')==fixture['source'] and not st.get('playRequested') and '播放失败' in st.get('playerError',''):break
        if st.get('source')==fixture['source'] and st.get('playing') and st.get('position',0)>800:played=True;break
        time.sleep(.5)
    screenshot('format-'+fixture['extension']+'.png')
    row={'check':'format-'+fixture['extension'],'passed':played,'fixture':fixture,'state':st,'seconds':time.monotonic()-start}
    if fixture.get('invalid'):
        row['passed']=not played and not st.get('playRequested') and '播放失败' in st.get('playerError','') and adb('shell','pidof',PACKAGE).strip()==original_pid
    elif played:
        end=time.monotonic()+30
        while time.monotonic()<end:
            st=json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
            if st.get('downloaded',0)>=fixture['size']:break
            time.sleep(.5)
        path=st['folder']+'/Video.'+fixture['extension']
        checksum=adb('shell','sha256sum',path).split()[0]
        row['device_sha256']=checksum
        row['device_file_size']=int(adb('shell','wc','-c',path).split()[0])
        row['download_sha256_matches']=checksum==fixture['sha256'] and row['device_file_size']==fixture['size']
        row['passed']=played and row['download_sha256_matches']
    rows.append(row);(OUT/'formats-verification.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
    if not row['passed']:print('Actual format failure',fixture['extension'],st)
assert len(rows)==17 and all(r['passed'] for r in rows),[(r['check'],r['state']) for r in rows if not r['passed']]
