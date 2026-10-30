import argparse,concurrent.futures,json,os,socket,struct,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--bind',required=True);a=p.parse_args()
targets=[('dht','router.bittorrent.com',6881),('dht','dht.transmissionbt.com',6881),('dht','router.utorrent.com',6881),('tracker','tracker.opentrackr.org',1337),('tracker','tracker.torrent.eu.org',451)]
def probe(target):
 kind,host,port=target;start=time.monotonic();row=dict(kind=kind,host=host,port=port,bind=a.bind)
 try:
  ip=socket.gethostbyname(host);row['resolved']=ip
  with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as s:
   s.bind((a.bind,0));s.settimeout(5);row['localPort']=s.getsockname()[1]
   if kind=='dht':
    transaction=os.urandom(2);payload=b'd1:ad2:id20:'+os.urandom(20)+b'e1:q4:ping1:t2:'+transaction+b'1:y1:qe'
   else:
    transaction=os.urandom(4);payload=struct.pack('!QI',0x41727101980,0)+transaction
   s.sendto(payload,(ip,port));response,remote=s.recvfrom(65536)
   row.update(responseBytes=len(response),remote=str(remote),transactionMatched=transaction in response)
 except Exception as e:row['error']=str(e)
 row['seconds']=round(time.monotonic()-start,3);return row
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:rows=list(pool.map(probe,targets))
Path('dist/udp-discovery-results.json').write_text(json.dumps(rows,indent=2),encoding='utf-8');print(json.dumps(rows,indent=2))
