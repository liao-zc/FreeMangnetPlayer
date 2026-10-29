"""Real container/codec clips downloaded via .torrent URLs, separate from film acceptance."""
import hashlib,json,struct,subprocess,time,urllib.request
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
import libtorrent as lt
root=Path('format-data');root.mkdir(exist_ok=True)
source=root/'bunny.mp4';urllib.request.urlretrieve('https://media.w3.org/2010/05/bunny/trailer.mp4',source)
formats=[('mp4','libx264','aac',[]),('mkv','libx265','aac',['-preset','ultrafast']),('webm','libvpx-vp9','libopus',['-deadline','realtime']),('avi','mpeg4','libmp3lame',[]),('flv','flv','libmp3lame',[]),('wmv','wmv2','wmav2',[]),('mpg','mpeg2video','mp2',[]),('ts','libx264','aac',[]),('m2ts','mpeg2video','ac3',[]),('mov','libx264','aac',[]),('3gp','mpeg4','aac',[]),('rm','rv20','real_144',['-ar','8000','-ac','1']),('ogv','libtheora','libvorbis',[]),('av1.mp4','libaom-av1','aac',['-cpu-used','8','-crf','40']),('vp8.webm','libvpx','libvorbis',['-deadline','realtime']),('h263.3gp','h263','aac',['-ar','22050','-ac','1'])]
session=lt.session({'listen_interfaces':'0.0.0.0:6882','enable_dht':False,'enable_lsd':False,'enable_upnp':False,'enable_natpmp':False,'upload_rate_limit':0})
handles=[];torrents={};rows=[]
for ext,video,audio,extra in formats:
    path=root/('Video.'+ext)
    command=['ffmpeg','-y','-ss','5','-i',str(source),'-t','8','-vf','scale=352:288' if video=='h263' else 'scale=320:240','-r','25','-c:v',video,'-c:a',audio,'-ac','2']+extra
    if ext.endswith(('mp4','mov')):command+=['-movflags','+faststart']
    if ext=='3gp':command+=['-ar','22050','-ac','1']
    subprocess.run(command+[str(path)],check=True)
    fs=lt.file_storage();lt.add_files(fs,str(path));creator=lt.create_torrent(fs,32768,lt.create_torrent.v1_only);creator.add_tracker('http://10.0.2.2:8898/announce');lt.set_piece_hashes(creator,str(root));data=creator.generate();ti=lt.torrent_info(data)
    handle=session.add_torrent({'ti':ti,'save_path':str(root.resolve())});handle.resume();handles.append(handle);torrents[ext]=lt.bencode(data)
    rows.append({'extension':ext,'video_codec':video,'audio_codec':audio,'source':'http://10.0.2.2:8898/'+ext+'.torrent','sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'size':path.stat().st_size})
end=time.time()+60
while not all(h.status().is_seeding for h in handles):
    if time.time()>end:raise RuntimeError('format seed verification timed out')
    time.sleep(.2)
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body=lt.bencode({'interval':5,'peers':bytes([10,0,2,2])+struct.pack('!H',6882)}) if self.path.startswith('/announce') else torrents[self.path.strip('/').removesuffix('.torrent')]
        self.send_response(200);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
Path('format-fixtures.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
ThreadingHTTPServer(('0.0.0.0',8898),Handler).serve_forever()
