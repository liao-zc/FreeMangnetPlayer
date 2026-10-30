"""Independent native BT observations; not a substitute for actual APK playback."""
import json,time
from pathlib import Path
import libtorrent as lt
targets=json.loads(Path('scripts/public_acceptance_targets.json').read_text(encoding='utf-8'))
session=lt.session({'listen_interfaces':'0.0.0.0:0','download_rate_limit':0,'upload_rate_limit':0,'enable_dht':True,
    'dht_bootstrap_nodes':'router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881'})
handles=[];rows=[]
for target in targets:
    params=lt.parse_magnet_uri('magnet:?xt=urn:btih:'+target['hash']);params.save_path=str(Path('native-public-data',target['hash']).resolve())
    handles.append(session.add_torrent(params));rows.append(dict(target,samples=[]))
start=time.monotonic()
while time.monotonic()-start<180:
    for row,handle in zip(rows,handles):
        s=handle.status();row['samples'].append(dict(seconds=round(time.monotonic()-start,2),metadata=s.has_metadata,peers=s.num_peers,seeds=s.num_seeds,payloadBytes=s.total_payload_download,payloadRate=s.download_payload_rate,dhtNodes=session.status().dht_nodes))
        if s.total_payload_download>8*1024*1024:handle.pause()
    time.sleep(1)
session.pause()
for row in rows:row['final']=row['samples'][-1]
Path('native-public-acceptance.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
