(function () {
  if (window.__fmpScan) { window.__fmpScan(); return; }
  const visited = new Set(), emitted = new Set(), failed = new Set();
  const clean = x => (x || '').replace(/\s+/g, ' ').trim();
  const resourceName=x=>{const t=clean(x);return /magnet:\?|^https?:|^link_blocked$|^(?:(?:磁力|magnet)(?:下载|链接)?|下载|复制|播放|查看详[情细]|详情|详细|查看|更多|点击查看|点击下载)$/i.test(t)?'':t;};
  const decode = x => { const e = document.createElement('textarea'); e.innerHTML = x; return e.value; };
  let running = false, pending = 0; const linkErrors=[]; const metadata = new Map();
  function nextUrl() {
    const anchors = [...document.querySelectorAll('a[href]')];
    const explicit = anchors.find(a => a.rel === 'next' || /^(下一页|下页|下一頁|next(?:\s*page)?|›|»|>)$/i.test(clean(a.textContent || a.getAttribute('aria-label'))) || /\bnext\b/i.test(a.className));
    if (explicit && !explicit.closest('[aria-disabled=true],.disabled')) {
      const u = new URL(explicit.href, location.href); if (/^https?:$/.test(u.protocol) && u.href !== location.href) return u.href;
    }
    const current = document.querySelector('.pagination .current,.pagination .active,.pager .active,[aria-current=page]');
    const n = current ? parseInt(clean(current.textContent),10) : NaN;
    const numbered = anchors.find(a => a.closest('.pagination,.pager,.pages,.page-numbers') && parseInt(clean(a.textContent),10) === n+1);
    return numbered ? numbered.href : '';
  }
  function info() {
    const pages=[...document.querySelectorAll('.pagination a[href],.pager a[href],.pages a[href],a.page-numbers[href]')].map(a=>({page:parseInt(clean(a.textContent),10),url:a.href})).filter(p=>p.page>0);
    const text=clean(document.body.textContent);const totalMatch=/(?:共|total)\s*(\d+)\s*(?:页|pages)/i.exec(text);
    window.FreeMagnetResults.onPageInfo(JSON.stringify({url:location.href,next:nextUrl(),pending:pending,failures:failed.size,errors:[...failed],linkErrors:linkErrors,running:running,pages:pages,totalPages:totalMatch?+totalMatch[1]:0,emitted:emitted.size,visited:visited.size,candidates:[...document.querySelectorAll('a[href]')].filter(a=>a.href.indexOf('detail')>=0).map(a=>({href:a.href,text:clean(a.textContent),origin:a.origin,locOrigin:location.origin,protocol:a.protocol,pagination:!!a.closest('.pagination,.pager,.pages,.page-numbers'),rel:a.rel})),complete:document.readyState==="complete"&&!running&&pending===0}));
  }
  function resourceTime(node, count) {
    if(!node||count>1)return '';
    const time=node.querySelector('time[datetime],time');
    let value=time&&(time.getAttribute('datetime')||time.textContent);
    if(!value&&node.ownerDocument!==document) {
      const meta=node.ownerDocument.querySelector('meta[property="article:published_time"],meta[itemprop="datePublished"],meta[name="date"]');
      value=meta&&meta.content;
    }
    if(!value) {
      const match=/(?:发布时间|发布日期|发布于|上传时间|添加时间|创建时间|日期|时间)\s*[:：]?\s*((?:19|20)\d{2}[-/.年]\d{1,2}[-/.月]\d{1,2}日?(?:[ T]\d{1,2}:\d{2}(?::\d{2})?)?|刚刚|今天|昨天|前天|\d+\s*(?:分钟|小时|天|周|个月|年)前)/.exec(clean(node.textContent));
      value=match&&match[1];
    }
    return clean(value).replace(/T(?=\d{2}:)/,' ').replace(/Z$/,' UTC');
  }
  function details(node, uri) {
    const chunks=[];if(node){const walker=document.createTreeWalker(node,NodeFilter.SHOW_TEXT);let part;while((part=walker.nextNode()))if(!part.parentElement.closest('script,style,noscript'))chunks.push(part.textContent);}
    const text=clean(chunks.join(' ')).replace(/magnet:\?[^\s<>]+/ig,'').replace(/https?:\/\/[^\s<>]+/ig,'');
    const occurrences=node?(node.innerHTML.match(/magnet:\?/ig)||[]).length:0;
    const m=/(?:[?&])xl=(\d+)/i.exec(uri), sizeMatch=occurrences>1?null:/(?<![a-z0-9])(\d+(?:[.,]\d+)?)\s*(GiB|MiB|KiB|TiB|GB|MB|KB|TB|B|\u5b57\u8282)(?![a-z0-9])/i.exec(text);
    let bytes=m?+m[1]:0, sizeText='';
    if(sizeMatch){const unit=sizeMatch[2].toUpperCase();const power=unit.startsWith('T')?4:unit.startsWith('G')?3:unit.startsWith('M')?2:unit.startsWith('K')?1:0;
      sizeText=sizeMatch[1].replace(',','.')+sizeMatch[2];if(!bytes)bytes=Math.round(parseFloat(sizeMatch[1].replace(',','.'))*Math.pow(unit.includes('I')?1024:1000,power));}
    const category=/纪录片/.test(text)?'纪录片':/动漫|动画/.test(text)?'动漫':/剧集|电视剧|连续剧/.test(text)?'剧集':/电影/.test(text)?'电影':'视频';
    const score=/(?:评分|热度|score)\s*[:：]?\s*(\d+(?:\.\d+)?)/i.exec(text);
    return {bytes:bytes,sizeText:sizeText,type:category,heat:score?score[1]:'',publishedAt:resourceTime(node,occurrences)};
  }
  function emit(uri, title, node) {
    uri=decode(uri).trim(); if (!/^magnet:\?/i.test(uri)) return;
    let dn=''; const m=/(?:[?&])dn=([^&]+)/i.exec(uri);
    if(m)try{dn=decodeURIComponent(m[1].replace(/\+/g,' '));}catch(_){}
    const data=details(node,uri), old=metadata.get(uri);
    const validTitle=resourceName(title);
    const value={title:validTitle||resourceName(dn)||'待获取文件名',source:uri,...data};
    if(old){if(!data.bytes && old.bytes) {value.bytes=old.bytes;value.sizeText=old.sizeText;} if(value.title==='待获取文件名')value.title=old.title;if(!value.publishedAt)value.publishedAt=old.publishedAt;}
    if(old && JSON.stringify(old)===JSON.stringify(value))return;
    emitted.add(uri);metadata.set(uri,value);window.FreeMagnetResults.onResults(JSON.stringify([value]));
  }
  function parse(doc, fallback) {
    [...doc.querySelectorAll('a[href],[data-magnet],input[value],textarea')].forEach(a=>{
      const uri=a.getAttribute('data-magnet')||a.getAttribute('href')||a.value||'';
      const row=a.closest('article,.result,.search-item,.item,li,tr,.card,.panel');
      const heading=row && row.querySelector('h1,h2,h3,h4,.title,.name,.item-title,.result-title,.search-title');
      const name=[heading && heading.textContent,a.textContent,a.title,fallback,doc!==document?doc.title:''].map(resourceName).find(Boolean)||'';
      emit(uri,name,row || (doc!==document?doc.body:null));
    });
    const pattern=/magnet:\?[^"'<>\s]+/ig; let m;
    const html=doc.documentElement?doc.documentElement.outerHTML:'';
    while((m=pattern.exec(html))) {if(!emitted.has(decode(m[0])))emit(m[0],fallback,null);}
  }
  window.__fmpScan=function(){
    if(window.__fmpStopped)return;parse(document,document.title); if(running&&pending>0){info();return;} running=true;
    const links=[...document.querySelectorAll('a[href]')].filter(a=>{
      try{
        const tests={protocol:/^https?:$/.test(a.protocol),origin:a.origin===location.origin,
          different:a.href!==location.href,unvisited:!visited.has(a.href),
          notPager:!a.closest('.pagination,.pager,.pages,.page-numbers'),
          notNext:a.rel!=='next'&&a.rel!=='prev',
          name:!/^(首页|上一页|下一页|上页|下页|返回|登录|注册|next|previous|\d+)$/i.test(clean(a.textContent)),
          text:clean(a.textContent||a.title).length>0};
        if(a.href.indexOf('detail')>=0)linkErrors.push(JSON.stringify({href:a.href,tests:tests}));
        return Object.values(tests).every(Boolean);
      }catch(e){linkErrors.push(String(e));return false;}
    });
    const urls=[...new Map(links.map(a=>[a.href,a])).values()]; pending=urls.length; linkErrors.push('detail-candidates='+urls.length); if(!pending){running=false;info();return;} info();
    async function worker(){
      while(urls.length){
        const a=urls.shift();visited.add(a.href);const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),12000);
        try{const response=await fetch(a.href,{credentials:'include',signal:controller.signal});if(!response.ok)throw Error(response.status);
          if(!window.__fmpStopped)parse(new DOMParser().parseFromString(await response.text(),'text/html'),[a.title,a.textContent,a.closest('article,.result,.item,li,.card')?.querySelector('h1,h2,h3,h4,.title,.entry-title')?.textContent].map(resourceName).find(Boolean)||'');failed.delete(a.href);
        }catch(_){failed.add(a.href);}finally{clearTimeout(timer);pending--;info();}
      }
    }
    Promise.all(Array.from({length:6},worker)).then(()=>{running=false;info();},e=>{linkErrors.push('worker:'+String(e));running=false;info();});
  };
  window.__fmpScan();
  const observer=new MutationObserver(()=>window.__fmpScan());
  if(document.documentElement)observer.observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['href','data-magnet']});
  setTimeout(()=>observer.disconnect(),40000);
})();
