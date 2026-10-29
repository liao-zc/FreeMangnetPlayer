from pathlib import Path
p=Path('app/src/main/java/com/masterwok/simpletorrentandroid/TorrentSession.kt');s=p.read_text(encoding='utf-8').replace('        sessionManager.remove(handle)\n        sessionManager.download(info,', '''        val hash = info.infoHash()
        sessionManager.remove(handle)
        val deadline = android.os.SystemClock.elapsedRealtime() + 3000
        while (sessionManager.find(hash)?.isValid == true && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
        check(sessionManager.find(hash)?.isValid != true) { "Previous metadata task is still being removed" }
        sessionManager.download(info,''');p.write_text(s,encoding='utf-8')
p=Path('scripts/verify_player_revision.py');s=p.read_text(encoding='utf-8')
at=s.index("    # A real pure hash may not be publicly discoverable")
block='''    # Same exact user hash, latest app and previous stable APK, fresh app data.
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
'''
s=s[:at]+block+s[at:];p.write_text(s,encoding='utf-8')
