"""Deterministic website with more than one page; uses only the fixed user hash."""
import html,json,threading,urllib.parse
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from test_resource import USER_MAGNET

pages={1:[('资源文件 '+str(i),USER_MAGNET+'&dn=page1-'+str(i)) for i in range(37)],2:[('第二页文件 '+str(i),USER_MAGNET+'&dn=page2-'+str(i)) for i in range(17)],3:[]}
details=[('完全不同的文件名甲',USER_MAGNET+'&dn=detail-a'),('不同译名乙',USER_MAGNET+'&dn=detail-b')]
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        u=urllib.parse.urlparse(self.path);p=int(urllib.parse.parse_qs(u.query).get('page',['1'])[0])
        if u.path.startswith('/detail/'):
            title,magnet=details[int(u.path.rsplit('/',1)[-1])]
            body='<title>'+title+'</title><a href="'+html.escape(magnet,quote=True)+'">磁力下载</a>'
        else:
            body='<title>搜索测试页</title>' + ''.join('<article><h2>'+title+'</h2><a href="'+html.escape(magnet,quote=True)+'">磁力</a></article>' for title,magnet in pages[p])
            if p==1:body+=''.join('<article><a href="/detail/'+str(i)+'">'+d[0]+'</a></article>' for i,d in enumerate(details))
            if p<3:body+='<nav class="pagination"><a rel="next" href="/search?page='+str(p+1)+'">下一页</a></nav>'
        data=('<html><meta charset="utf-8"><body>'+body+'</body></html>').encode()
        self.send_response(200);self.send_header('Content-Type','text/html;charset=utf-8');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
Path('search-pages-expected.json').write_text(json.dumps({1:pages[1]+details,2:pages[2],3:pages[3]},ensure_ascii=False),encoding='utf-8')
ThreadingHTTPServer(('0.0.0.0',8899),Handler).serve_forever()
