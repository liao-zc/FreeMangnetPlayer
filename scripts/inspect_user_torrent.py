"""Validate public metadata info hash and inspect file/peer discovery facts."""
import hashlib,json,urllib.request
from pathlib import Path
HASH='2362347540B27D8A4142AF2BBE0A071F26A3B7A1'
request=urllib.request.Request('https://itorrents.net/torrent/'+HASH+'.torrent',headers={'User-Agent':'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36'})
data=urllib.request.urlopen(request,timeout=12).read(2*1024*1024)
info_span=None
def decode(pos=0,depth=0):
    global info_span
    assert depth<100
    char=data[pos:pos+1]
    if char==b'i':
        end=data.index(b'e',pos);return int(data[pos+1:end]),end+1
    if char in (b'd',b'l'):
        is_dict=char==b'd';out={} if is_dict else [];pos+=1
        while data[pos:pos+1]!=b'e':
            if is_dict:
                key,pos=decode(pos,depth+1);begin=pos;value,pos=decode(pos,depth+1);out[key]=value
                if depth==0 and key==b'info':info_span=data[begin:pos]
            else:
                value,pos=decode(pos,depth+1);out.append(value)
        return out,pos+1
    colon=data.index(b':',pos);length=int(data[pos:colon]);end=colon+1+length
    assert end<=len(data)
    return data[colon+1:end],end
t,end=decode();assert end==len(data)
digest=hashlib.sha1(info_span).hexdigest();assert digest==HASH.lower()
info=t[b'info'];files=info.get(b'files') or [{b'path':[info[b'name']],b'length':info[b'length']}]
result={'hash':digest,'hash_verified':True,'private':info.get(b'private',0),'piece_length':info[b'piece length'],
'files':[{'path':'/'.join(p.decode('utf-8','replace') for p in f[b'path']),'size':f[b'length']} for f in files],
'tracker_count':len(t.get(b'announce-list',[])), 'environment':'Windows workspace; HTTP metadata only, not playback'}
Path('docs/evidence/peer-recovery-user-metadata.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
