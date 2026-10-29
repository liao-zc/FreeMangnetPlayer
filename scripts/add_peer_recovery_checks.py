from pathlib import Path
p=Path('app/src/main/java/com/freemangnetplayer/TorrentTasks.kt');s=p.read_text(encoding='utf-8').replace(r'\\\\d',r'\\d');p.write_text(s,encoding='utf-8')
p=Path('scripts/player_revision_fixture.py');s=p.read_text(encoding='utf-8')
at=s.index("exec(compile")
s=s[:at]+'''source = source.replace("class Handler(BaseHTTPRequestHandler):", "metadata_served = False\\nclass Handler(BaseHTTPRequestHandler):")
source = source.replace("    def do_GET(self):", "    def do_GET(self):\\n        global metadata_served")
source = source.replace("        if self.path.startswith('/announce'):", "        if self.path.startswith('/metadata.torrent'):\\n            body = handles[0].torrent_file().metadata() if False else lt.bencode({b'info': lt.bdecode(handles[0].torrent_file().metadata())})\\n            metadata_served = True\\n        elif self.path.startswith('/fallback-announce'):\\n            body = lt.bencode({'interval': 5, 'complete': 1, 'incomplete': 0, 'peers': bytes([10, 0, 2, 2]) + struct.pack('!H', 6881) if metadata_served else b''})\\n        elif self.path.startswith('/announce'):")
''' +s[at:]
p.write_text(s,encoding='utf-8')
p=Path('scripts/verify_player_revision.py');s=p.read_text(encoding='utf-8')
needle="    initial_pid=adb('shell','pidof',PACKAGE).strip()"
insert='''    # No resource endpoint is returned until exact-source metadata is served.
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
'''
s=s.replace(needle,insert+needle)
p.write_text(s,encoding='utf-8')
