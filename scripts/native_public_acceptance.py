"""Independent native BT observations; not a substitute for actual APK playback."""
import json,time
from pathlib import Path
import libtorrent as lt
targets=json.loads(Path('scripts/public_acceptance_targets.json').read_text(encoding='utf-8'))
session=lt.session({'listen_interfaces':'0.0.0.0:0','download_rate_limit':0,'upload_rate_limit':0,'enable_dht':True,
    'dht_bootstrap_nodes':'router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881'})
trackers=['udp://tracker.opentrackr.org:1337/announce','https://tracker.tamersunion.org:443/announce','udp://open.stealth.si:80/announce','udp://tracker.torrent.eu.org:451/announce','udp://exodus.desync.com:6969/announce','udp://open.demonii.com:1337/announce','https://tracker.gbitt.info/announce','http://tracker.opentrackr.org:1337/announce']
handles=[];rows=[]
for target in targets:
    params=lt.parse_magnet_uri('magnet:?xt=urn:btih:'+target['hash']);params.save_path=str(Path('native-public-data',target['hash']).resolve())
    params.trackers=trackers;handles.append(session.add_torrent(params));rows.append(dict(target,trackers=trackers,nativeVersion=lt.version,samples=[]))
start=time.monotonic()
while time.monotonic()-start<180:
    for row,handle in zip(rows,handles):
        s=handle.status();row['samples'].append(dict(seconds=round(time.monotonic()-start,2),metadata=s.has_metadata,peers=s.num_peers,seeds=s.num_seeds,payloadBytes=s.total_payload_download,payloadRate=s.download_payload_rate,dhtNodes=session.status().dht_nodes))
        if s.total_payload_download>8*1024*1024:handle.pause()
    time.sleep(1)
session.pause()
for row in rows:row['final']=row['samples'][-1]
Path('native-public-acceptance.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
