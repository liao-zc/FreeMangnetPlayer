"""Multiple real video files over BitTorrent for v0.1.10 checks."""
import hashlib
import json
import struct
import subprocess
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import libtorrent as lt

root = Path('optimization-data')
root.mkdir(exist_ok=True)
source = root / 'bunny.mp4'
urllib.request.urlretrieve('https://media.w3.org/2010/05/bunny/trailer.mp4', source)
session = lt.session({'listen_interfaces': '0.0.0.0:6881', 'upload_rate_limit': 16 * 1024,
    'local_upload_rate_limit': 16 * 1024, 'ignore_limits_on_local_network': False,
    'enable_dht': False, 'enable_lsd': False, 'enable_upnp': False, 'enable_natpmp': False})
classes = lt.ip_filter()
classes.add_rule('0.0.0.0', '255.255.255.255', 1)
session.set_peer_class_filter(classes)
handles = []
fixtures = []
for number in range(1, 4):
    folder = root / f'Bundle-{number}'
    folder.mkdir(exist_ok=True)
    for episode in [1, 2]:
        target = folder / f'Episode-0{episode}.mp4'
        subprocess.run(['ffmpeg', '-y', '-ss', '5', '-i', str(source), '-t', '45',
            '-vf', 'scale=320:-2' + (',hue=s=0' if episode == 2 else ''), '-c:v', 'libx264',
            '-preset', 'ultrafast', '-an', '-movflags', '+faststart', str(target)], check=True)
    (folder / 'License.txt').write_text('Blender Big Buck Bunny, CC BY 3.0. https://peach.blender.org/', encoding='utf-8')
    storage = lt.file_storage()
    lt.add_files(storage, str(folder))
    creator = lt.create_torrent(storage, 32 * 1024, lt.create_torrent.v1_only)
    lt.set_piece_hashes(creator, str(root))
    generated = creator.generate()
    info = lt.torrent_info(generated)
    digest = hashlib.sha1(lt.bencode(generated[b'info'])).hexdigest()
    handle = session.add_torrent({'ti': info, 'save_path': str(root.resolve())})
    handle.resume()
    handles.append(handle)
    fixtures.append({'id': digest, 'magnet': f'magnet:?xt=urn:btih:{digest}&tr=http%3A%2F%2F10.0.2.2%3A8888%2Fannounce',
        'files': [{'path': info.files().file_path(i), 'size': info.files().file_size(i),
            'sha256': hashlib.sha256((root / info.files().file_path(i)).read_bytes()).hexdigest()} for i in range(info.num_files())]})
deadline = time.time() + 60
while not all(handle.status().is_seeding for handle in handles):
    if time.time() > deadline:
        raise RuntimeError('Seeds did not finish verifying data')
    time.sleep(0.5)

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith('/announce'):
            body = lt.bencode({'interval': 5, 'complete': 1, 'incomplete': 0, 'peers': bytes([10, 0, 2, 2]) + struct.pack('!H', 6881)})
        elif self.path.startswith('/empty'):
            body = b'<html><body>No search results</body></html>'
        else:
            body = f'<html><body><a href="{fixtures[0]["magnet"]}">Big Buck Bunny multi-video CC BY</a></body></html>'.encode()
        self.send_response(200)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

threading.Thread(target=ThreadingHTTPServer(('0.0.0.0', 8888), Handler).serve_forever, daemon=True).start()
Path('optimization-fixture.json').write_text(json.dumps(fixtures, indent=2), encoding='utf-8')
print('Three real seeds ready', flush=True)
while True:
    for alert in session.pop_alerts():
        print(alert, flush=True)
    print([(h.status().num_peers, h.status().total_upload) for h in handles], flush=True)
    time.sleep(2)
