"""Record a lawful public-swarm baseline without assuming Internet BT connectivity."""
import hashlib
import json
import re
import time
import urllib.request
from pathlib import Path
import libtorrent as lt

result = {'resource': 'Official Ubuntu LTS installer', 'samples': []}
try:
    base = 'https://releases.ubuntu.com/24.04/'
    page = urllib.request.urlopen(base, timeout=20).read().decode()
    filename = sorted(set(re.findall(r'ubuntu-24\.04[.\d]*-desktop-amd64\.iso\.torrent', page)))[-1]
    data = urllib.request.urlopen(base + filename, timeout=20).read()
    ti = lt.torrent_info(data)
    digest = hashlib.sha1(lt.bencode(lt.bdecode(data)[b'info'])).hexdigest()
    plain = f'magnet:?xt=urn:btih:{digest}'
    trackers = [item.url for item in ti.trackers()]
    result.update({'torrent_url': base + filename, 'id': digest, 'plain_magnet': plain, 'official_trackers': trackers})
    session = lt.session({'listen_interfaces': '0.0.0.0:0', 'download_rate_limit': 0,
        'upload_rate_limit': 0, 'enable_dht': True, 'enable_lsd': True,
        'dht_bootstrap_nodes': 'router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881'})
    params = lt.parse_magnet_uri(plain)
    params.save_path = str(Path('public-baseline-data').resolve())
    handle = session.add_torrent(params)
    start = time.monotonic()
    first_metadata = None
    while time.monotonic() - start < 75:
        status = handle.status()
        sample = {'seconds': time.monotonic() - start, 'metadata': status.has_metadata,
            'peers': status.num_peers, 'seeds': status.num_seeds, 'payload_bytes': status.total_payload_download,
            'payload_rate': status.download_payload_rate, 'dht_nodes': session.status().dht_nodes}
        result['samples'].append(sample)
        if status.has_metadata and first_metadata is None:
            first_metadata = time.monotonic() - start
        if status.total_payload_download >= 8 * 1024 * 1024:
            break
        time.sleep(1)
    session.pause()
    result.update({'metadata_seconds': first_metadata, 'final_state': result['samples'][-1],
        'peak_payload_bytes_per_second': max(s['payload_rate'] for s in result['samples']),
        'applicable_limit': 0, 'note': 'Pure hash, public DHT; no .torrent metadata supplied to native download.'})
except Exception as error:
    result['error'] = type(error).__name__ + ': ' + str(error)
Path('public-network-baseline.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result.get('final_state', result.get('error')), ensure_ascii=True), flush=True)
