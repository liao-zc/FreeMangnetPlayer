"""Real multi-file AV torrents with a deliberately modest seed rate for startup checks."""
from pathlib import Path

source = Path('scripts/optimization_fixture.py').read_text(encoding='utf-8')
source = source.replace("16 * 1024", "128 * 1024")
source = source.replace('range(1, 5)', 'range(1, 4)')
source = source.replace("'-ss', '5', '-i'", "'-stream_loop', '-1', '-ss', '5', '-i'")
source = source.replace("'-t', '45'", "'-t', '180'")
source = source.replace("'-preset', 'ultrafast', '-an'", "'-preset', 'ultrafast', '-c:a', 'aac', '-b:a', '64k'")
source = source.replace("    (folder / 'License.txt').write_text", "    (folder / 'Broken.mp4').write_bytes(b'Intentionally invalid media for error recovery test')\n    (folder / 'License.txt').write_text")
source = source.replace("print(alert, flush=True)", "pass  # drain native alerts without producing per-piece logs")
source = source.replace("class Handler(BaseHTTPRequestHandler):", "metadata_served = False\nclass Handler(BaseHTTPRequestHandler):")
source = source.replace("    def do_GET(self):", "    def do_GET(self):\n        global metadata_served")
source = source.replace("        if self.path.startswith('/announce'):", "        if self.path.startswith('/metadata.torrent'):\n            body = handles[0].torrent_file().metadata() if False else lt.bencode({b'info': lt.bdecode(handles[0].torrent_file().metadata())})\n            metadata_served = True\n        elif self.path.startswith('/fallback-announce'):\n            body = lt.bencode({'interval': 5, 'complete': 1, 'incomplete': 0, 'peers': bytes([10, 0, 2, 2]) + struct.pack('!H', 6881) if metadata_served else b''})\n        elif self.path.startswith('/announce'):")
exec(compile(source, 'player_revision_fixture', 'exec'))
