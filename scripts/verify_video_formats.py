import hashlib,json,shlex,subprocess,time
from pathlib import Path
from capture_usage_screenshots import adb,screenshot,PACKAGE,OUT

rows=[]
for fixture in json.loads(Path('format-fixtures.json').read_text()):
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity','--es','test-torrent-url',shlex.quote(fixture['source']),'--es','mode','play','--es','title','Video.'+fixture['extension'])
    start=time.monotonic();st={};played=False
    while time.monotonic()-start<90:
        try:st=json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
        except Exception:st={}
        if st.get('source')==fixture['source'] and st.get('playing') and st.get('position',0)>800:played=True;break
        time.sleep(.5)
    screenshot('format-'+fixture['extension']+'.png')
    row={'check':'format-'+fixture['extension'],'passed':played,'fixture':fixture,'state':st,'seconds':time.monotonic()-start}
    if played:
        end=time.monotonic()+30
        while time.monotonic()<end:
            st=json.loads(adb('shell','run-as',PACKAGE,'cat','files/torrent-check.json'))
            if st.get('done',0)>=fixture['size']:break
            time.sleep(.5)
        path=st['folder']+'/Video.'+fixture['extension'];data=subprocess.check_output(['adb','exec-out','run-as',PACKAGE,'cat',path])
        row['download_sha256_matches']=hashlib.sha256(data).hexdigest()==fixture['sha256']
        row['passed']=played and row['download_sha256_matches']
    rows.append(row);(OUT/'formats-verification.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
    if not row['passed']:print('Actual format failure',fixture['extension'],st)
assert len(rows)==13 and all(r['passed'] for r in rows),[(r['check'],r['state']) for r in rows if not r['passed']]
