"""Read-only HTTP availability observation, not a playback-success check."""
import concurrent.futures,json,urllib.request
from pathlib import Path
h='F1006D95049A840AA34CEC95967EA590BF5FFEAF'
urls=['https://itorrents.net/torrent/'+h+'.torrent','https://itorrents.org/torrent/'+h+'.torrent','https://btcache.me/torrent/'+h,'https://torrage.info/torrent.php?h='+h]
def probe(url):
    try:
        request=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36'})
        with urllib.request.urlopen(request,timeout=8) as r:
            b=r.read(2*1024*1024)
            return {'url':url,'status':r.status,'bytes':len(b),'bencode_candidate':b[:1]==b'd'}
    except Exception as error:
        return {'url':url,'error':type(error).__name__+': '+str(error)}
if __name__=='__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        rows=list(pool.map(probe,urls))
        text=json.dumps({'environment':'Windows workspace HTTP client with app User-Agent; not the user phone','results':rows},indent=2)
        Path('docs/evidence/peer-recovery-metadata-http.json').write_text(text,encoding='utf-8')
        print(text)
