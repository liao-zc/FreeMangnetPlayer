"""Probe the same targets with a modern native engine on the user's network."""
import argparse,json,time,urllib.request
from pathlib import Path
import libtorrent as lt
p=argparse.ArgumentParser();p.add_argument('--seconds',type=int,default=600);p.add_argument('--output',default='dist/local-peer-probe');a=p.parse_args()
out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
targets=json.loads(Path('scripts/public_acceptance_targets.json').read_text(encoding='utf-8'))
trackers=['udp://tracker.opentrackr.org:1337/announce','https://tracker.tamersunion.org:443/announce','udp://tracker.torrent.eu.org:451/announce']
try:
    text=urllib.request.urlopen('https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_all.txt',timeout=20).read().decode()
    trackers=list(dict.fromkeys(trackers+[x.strip() for x in text.splitlines() if x.startswith(('udp://','https://','http://'))]))[:80]
except Exception as e:print('Tracker list fetch:',e,flush=True)
settings={'listen_interfaces':'0.0.0.0:0,[::]:0','download_rate_limit':0,'upload_rate_limit':0,'connections_limit':600,'connection_speed':40,'enable_dht':True,'enable_lsd':True,'enable_upnp':True,'enable_natpmp':True,'enable_incoming_tcp':True,'enable_outgoing_tcp':True,'enable_incoming_utp':True,'enable_outgoing_utp':True,'announce_to_all_trackers':True,'announce_to_all_tiers':True,'dht_bootstrap_nodes':'router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881'}
session=lt.session(settings);rows=[];handles=[];seen=set();start=time.monotonic();events=[]
for target in targets:
    params=lt.parse_magnet_uri('magnet:?xt=urn:btih:'+target['hash']);params.trackers=trackers;params.save_path=str((out/'data'/target['hash']).resolve())
    params.flags &= ~lt.torrent_flags.auto_managed;params.flags &= ~lt.torrent_flags.paused
    h=session.add_torrent(params);handles.append(h);rows.append(dict(target,samples=[],files=[],metadataSeconds=None,firstPayloadSeconds=None))
while time.monotonic()-start<a.seconds:
    for row,h in zip(rows,handles):
        s=h.status();elapsed=round(time.monotonic()-start,2)
        if s.has_metadata and row['hash'] not in seen:
            seen.add(row['hash']);row['metadataSeconds']=elapsed;info=h.torrent_file();fs=info.files()
            row['files']=[{'index':i,'path':fs.file_path(i),'size':fs.file_size(i)} for i in range(fs.num_files())]
            (out/(row['hash']+'.torrent')).write_bytes(lt.bencode(lt.create_torrent(info).generate()))
            videos=[f for f in row['files'] if Path(f['path']).suffix.lower() in ['.mp4','.mkv','.avi','.rmvb','.rm','.ts','.mov','.wmv','.webm','.mpg','.mpeg','.m4v']]
            selected=max(videos or row['files'],key=lambda f:f['size'])['index'];row['selectedFile']=selected
            h.prioritize_files([4 if i==selected else 0 for i in range(fs.num_files())]);h.set_sequential_download(True)
            first=fs.file_offset(selected)//info.piece_length();last=(fs.file_offset(selected)+fs.file_size(selected)-1)//info.piece_length()
            for piece in list(range(first,min(last+1,first+16)))+list(range(max(first,last-3),last+1)):h.set_piece_deadline(piece,1000)
            print('Metadata',row['hash'],row['files'][selected],flush=True)
        if s.total_payload_download>0 and row['firstPayloadSeconds'] is None:row['firstPayloadSeconds']=elapsed;print('First payload',row['hash'],elapsed,flush=True)
        peers=[]
        for peer in h.get_peer_info()[:20]:
            peers.append(dict(endpoint=str(peer.ip),flags=int(peer.flags),source=int(peer.source),downloadSpeed=peer.payload_down_speed,downloadQueue=peer.download_queue_length,remoteQueue=peer.upload_queue_length))
        row['samples'].append(dict(seconds=elapsed,metadata=s.has_metadata,peers=s.num_peers,seeds=s.num_seeds,payloadBytes=s.total_payload_download,payloadRate=s.download_payload_rate,state=str(s.state),wanted=s.total_wanted,wantedDone=s.total_wanted_done,error=str(s.errc),peerDetails=peers))
        row['final']=row['samples'][-1]
        if s.total_payload_download>=32*1024*1024:h.pause()
    for alert in session.pop_alerts():
        if any(x in type(alert).__name__ for x in ['error','blocked','disconnected','listen_succeeded']):events.append({'seconds':round(time.monotonic()-start,2),'type':type(alert).__name__,'message':str(alert)})
    report=dict(version=lt.version,seconds=round(time.monotonic()-start,2),settings=settings,trackers=trackers,targets=rows,events=events[-1000:])
    (out/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    time.sleep(5)
session.pause();print('Native observation finished:',out/'results.json',flush=True)
