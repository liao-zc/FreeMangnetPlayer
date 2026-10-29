"""Unlimited real BitTorrent seed and an independent native download baseline."""
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

root = Path('performance-data')
root.mkdir(exist_ok=True)
source = root / 'bunny.mp4'
urllib.request.urlretrieve('https://media.w3.org/2010/05/bunny/trailer.mp4', source)
folder = root / 'Unlimited-Bunny'
folder.mkdir(exist_ok=True)
video = folder / 'Bunny-Performance.mp4'
subprocess.run(['ffmpeg', '-y', '-stream_loop', '-1', '-i', str(source), '-t', '180',
    '-vf', 'scale=640:-2', '-c:v', 'libx264', '-preset', 'ultrafast', '-b:v', '8M',
    '-minrate', '8M', '-maxrate', '8M', '-bufsize', '16M', '-x264-params', 'nal-hrd=cbr:force-cfr=1',
    '-an', '-movflags', '+faststart', str(video)], check=True)
storage = lt.file_storage()
lt.add_files(storage, str(folder))
creator = lt.create_torrent(storage, 256 * 1024, lt.create_torrent.v1_only)
lt.set_piece_hashes(creator, str(root))
generated = creator.generate()
info = lt.torrent_info(generated)
digest = hashlib.sha1(lt.bencode(generated[b'info'])).hexdigest()
seed = lt.session({'listen_interfaces': '0.0.0.0:6881', 'upload_rate_limit': 0,
    'download_rate_limit': 0, 'local_upload_rate_limit': 0, 'local_download_rate_limit': 0,
    'enable_dht': True, 'enable_lsd': True, 'enable_upnp': False, 'enable_natpmp': False})
handle = seed.add_torrent({'ti': info, 'save_path': str(root.resolve())})
handle.resume()
deadline = time.time() + 60
while not handle.status().is_seeding:
    if time.time() > deadline:
        raise RuntimeError('Seed verification failed')
    time.sleep(.2)

# Native baseline over the same seed, outside the Android emulator.
baseline_folder = root / 'baseline'
baseline_folder.mkdir(exist_ok=True)
baseline = lt.session({'listen_interfaces': '0.0.0.0:0', 'download_rate_limit': 0,
    'upload_rate_limit': 0, 'enable_dht': False, 'enable_lsd': False})
download = baseline.add_torrent({'ti': info, 'save_path': str(baseline_folder.resolve())})
download.connect_peer(('127.0.0.1', 6881))
start = time.monotonic()
samples = []
while not download.status().is_seeding:
    status = download.status()
    samples.append({'seconds': time.monotonic() - start, 'bytes': status.total_payload_download,
        'payload_rate': status.download_payload_rate})
    if time.monotonic() - start > 120:
        raise RuntimeError('Unlimited native baseline failed')
    time.sleep(.2)
elapsed = time.monotonic() - start
baseline.pause()
actual = hashlib.sha256((baseline_folder / 'Unlimited-Bunny/Bunny-Performance.mp4').read_bytes()).hexdigest()
expected = hashlib.sha256(video.read_bytes()).hexdigest()
assert actual == expected
result = {'id': digest, 'plain_magnet': f'magnet:?xt=urn:btih:{digest}',
    'magnet': f'magnet:?xt=urn:btih:{digest}&tr=http%3A%2F%2F10.0.2.2%3A8888%2Fannounce',
    'direct_magnet': f'magnet:?xt=urn:btih:{digest}&x.pe=10.0.2.2%3A6881',
    'path': info.files().file_path(0), 'size': video.stat().st_size, 'sha256': expected,
    'seed_limits': {k: seed.get_settings()[k] for k in ['upload_rate_limit', 'download_rate_limit', 'local_upload_rate_limit', 'local_download_rate_limit']},
    'baseline': {'seconds': elapsed, 'average_bytes_per_second': video.stat().st_size / elapsed,
        'peak_payload_bytes_per_second': max(s['payload_rate'] for s in samples), 'sha256': actual, 'samples': samples}}

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith('/announce'):
            body = lt.bencode({'interval': 30, 'complete': 1, 'incomplete': 0,
                'peers': bytes([10, 0, 2, 2]) + struct.pack('!H', 6881)})
        else:
            body = b'<html><body>No resources</body></html>'
        self.send_response(200)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

threading.Thread(target=ThreadingHTTPServer(('0.0.0.0', 8888), Handler).serve_forever, daemon=True).start()
Path('performance-fixture.json').write_text(json.dumps(result, indent=2))
print('Unlimited seed and verified native baseline ready', flush=True)
while True:
    for alert in seed.pop_alerts():
        print(alert, flush=True)
    print('Seed peers/bytes:', handle.status().num_peers, handle.status().total_upload, flush=True)
    time.sleep(2)
