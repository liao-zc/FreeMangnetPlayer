"""Deterministic multi-page search site for relevance, deduplication, and 10-item paging checks."""
import hashlib,html,json,threading,urllib.parse
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path

def magnet(key,dn):
    digest=hashlib.sha1(key.encode('utf-8')).hexdigest()
    return 'magnet:?xt=urn:btih:'+digest+'&dn='+urllib.parse.quote(dn)

pages={1:[(f'哪吒资源文件 {i}',magnet(f'p1-{i}',f'nezha-p1-{i}.mkv')) for i in range(37)],2:[(f'哪吒第二页资源 {i}',magnet(f'p2-{i}',f'nezha-p2-{i}.mp4')) for i in range(17)],3:[]}
# A repeated BTIH with different dn/title must not create another search result.
duplicate=("哪吒重复资源标题",pages[1][0][1].replace('nezha-p1-0.mkv','duplicate-name.mkv'))
details=[('完全不同的文件名甲',magnet('unrelated-a','unrelated-a.mkv')),('不同译名乙',magnet('unrelated-b','unrelated-b.zip'))]
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        u=urllib.parse.urlparse(self.path);p=int(urllib.parse.parse_qs(u.query).get('page',['1'])[0])
        if u.path.startswith('/detail/'):
            title,uri=details[int(u.path.rsplit('/',1)[-1])]
            body='<title>'+title+'</title><time datetime="2025-03-01">2025-03-01</time><span>1.5MB 剧集 热度：9.0</span><a href="'+html.escape(uri,quote=True)+'">查看详情</a>'
        else:
            body='<title>搜索测试页</title><div>共3页</div><nav class="pagination">'+''.join('<a href="/search?page='+str(n)+'">'+str(n)+'</a>' for n in [1,2,3])+'</nav>'
            body+=''.join('<article><h2>'+title+'</h2><time datetime="2026-10-29">2026-10-29</time><span>2.3GB 电影 热度：8.5</span><a href="'+html.escape(uri,quote=True)+'">查看详情</a></article>' for title,uri in pages[p])
            if p==1:
                title,uri=duplicate
                body+='<article><h2>'+title+'</h2><time datetime="2026-10-29">2026-10-29</time><span>2.3GB 电影 热度：8.5</span><a href="'+html.escape(uri,quote=True)+'">复制磁力</a></article>'
                body+=''.join('<article><a href="/detail/'+str(i)+'">'+d[0]+'</a></article>' for i,d in enumerate(details))
            if p<3:body+='<nav class="pagination"><a rel="next" href="/search?page='+str(p+1)+'">下一页</a></nav>'
        body+='<footer>页面总计 34.03GB</footer>'
        data=('<html><meta charset="utf-8"><body>'+body+'</body></html>').encode()
        self.send_response(200);self.send_header('Content-Type','text/html;charset=utf-8');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
Path('search-pages-expected.json').write_text(json.dumps({1:pages[1],2:pages[2],3:pages[3]},ensure_ascii=False),encoding='utf-8')
ThreadingHTTPServer(('0.0.0.0',8899),Handler).serve_forever()