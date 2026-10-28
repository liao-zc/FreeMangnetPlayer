"""Real BitTorrent seed for reproducible Android integration checks.

The media is a clip of Blender's CC BY Big Buck Bunny, not the user's resource.
Metadata must travel over BitTorrent; no .torrent file is given to the app.
"""
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

ROOT = Path("fixture-data")
ROOT.mkdir(exist_ok=True)
VIDEO = ROOT / "BigBuckBunny-CCBY.mp4"
source = ROOT / "bunny-trailer.mp4"
urllib.request.urlretrieve("https://media.w3.org/2010/05/bunny/trailer.mp4", source)
subprocess.run([
    "ffmpeg", "-y", "-ss", "5", "-i", str(source),
    "-t", "45", "-vf", "scale=320:-2", "-c:v", "libx264", "-preset", "ultrafast",
    "-an", "-movflags", "+faststart", str(VIDEO),
], check=True)
storage = lt.file_storage()
lt.add_files(storage, str(VIDEO))
creator = lt.create_torrent(storage, 32 * 1024, lt.create_torrent.v1_only)
lt.set_piece_hashes(creator, str(ROOT))
generated = creator.generate()
info = lt.torrent_info(generated)
# libtorrent 2.x info_hash() may return a truncated v2 SHA256 even for hybrids.
# A btih magnet must contain the SHA1 of the canonical v1 info dictionary.
v1_hash = hashlib.sha1(lt.bencode(generated[b"info"])).hexdigest()
assert str(info.info_hashes().v1) == v1_hash
session = lt.session({"listen_interfaces": "0.0.0.0:6881", "upload_rate_limit": max(16 * 1024, VIDEO.stat().st_size // 30), "enable_dht": False,
                      "enable_lsd": False, "enable_upnp": False, "enable_natpmp": False})
handle = session.add_torrent({"ti": info, "save_path": str(ROOT.resolve())})
handle.resume()
deadline = time.time() + 60
while not handle.status().is_seeding:
    if time.time() > deadline:
        raise RuntimeError("Fixture did not become a real seed")
    time.sleep(0.5)
magnet = f"magnet:?xt=urn:btih:{v1_hash}&tr=http%3A%2F%2F10.0.2.2%3A8888%2Fannounce"
Path("fixture.json").write_text(json.dumps({"magnet": magnet, "file": VIDEO.name,
    "sha256": hashlib.sha256(VIDEO.read_bytes()).hexdigest(), "size": VIDEO.stat().st_size}), encoding="utf-8")


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/announce"):
            body = lt.bencode({"interval": 5, "complete": 1, "incomplete": 0,
                               "peers": bytes([10, 0, 2, 2]) + struct.pack("!H", 6881)})
            mime = "text/plain"
        elif self.path.startswith("/empty"):
            body = b"<html><body>No search results</body></html>"
            mime = "text/html"
        else:
            body = f'<html><body><a href="{magnet}">Big Buck Bunny CC BY</a></body></html>'.encode()
            mime = "text/html"
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


threading.Thread(target=ThreadingHTTPServer(("0.0.0.0", 8888), Handler).serve_forever, daemon=True).start()
print("Real seed and HTTP tracker ready", flush=True)
while True:
    for alert in session.pop_alerts():
        print(alert, flush=True)
    time.sleep(2)
