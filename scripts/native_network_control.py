import argparse,json,time
from pathlib import Path
import libtorrent as lt
p=argparse.ArgumentParser();p.add_argument('torrent');p.add_argument('--bind',required=True);p.add_argument('--seconds',type=int,default=180);a=p.parse_args()
out=Path('dist/network-control');out.mkdir(parents=True,exist_ok=True)
settings=dict(listen_interfaces=a.bind+':0',outgoing_interfaces=a.bind,enable_lsd=False,download_rate_limit=0,upload_rate_limit=0,alert_mask=int(lt.alert.category_t.all_categories))
session=lt.session(settings);info=lt.torrent_info(a.torrent)
params=lt.add_torrent_params();params.ti=info;params.save_path=str((out/'data').resolve());params.flags &= ~lt.torrent_flags.paused;params.flags &= ~lt.torrent_flags.auto_managed
h=session.add_torrent(params);rows=[];events=[];start=time.monotonic()
while time.monotonic()-start<a.seconds:
 s=h.status();rows.append(dict(seconds=round(time.monotonic()-start,2),peers=s.num_peers,payload=s.total_payload_download,rate=s.download_payload_rate))
 for alert in session.pop_alerts():
  if any(t in type(alert).__name__ for t in ['tracker_reply','tracker_error','listen_failed']):events.append(str(alert))
 (out/'results.json').write_text(json.dumps(dict(name=info.name(),hash=str(info.info_hash()),settings=settings,samples=rows,events=events),indent=2),encoding='utf-8')
 if s.total_payload_download>32*1024*1024:break
 time.sleep(5)
session.pause();print(rows[-1])
