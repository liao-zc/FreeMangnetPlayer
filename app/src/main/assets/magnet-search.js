(function () {
  if (window.__fmpScan) { window.__fmpScan(); return; }
  const visited = new Set(), emitted = new Set(), failed = new Set();
  const clean = x => (x || '').replace(/\s+/g, ' ').trim();
  const decode = x => { const e = document.createElement('textarea'); e.innerHTML = x; return e.value; };
  let running = false, pending = 0;
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
    window.FreeMagnetResults.onPageInfo(JSON.stringify({url:location.href,next:nextUrl(),pending:pending,failures:failed.size}));
  }
  function emit(uri, title) {
    uri=decode(uri).trim(); if (!/^magnet:\?/i.test(uri) || emitted.has(uri)) return;
    emitted.add(uri); let dn=''; const m=/(?:[?&])dn=([^&]+)/i.exec(uri);
    if(m)try{dn=decodeURIComponent(m[1].replace(/\+/g,' '));}catch(_){}
    window.FreeMagnetResults.onResults(JSON.stringify([{title:clean(title)||dn||'磁力资源',source:uri}]));
  }
  function parse(doc, fallback) {
    [...doc.querySelectorAll('a[href],[data-magnet]')].forEach(a=>{
      const uri=a.getAttribute('data-magnet')||a.getAttribute('href')||'';
      const row=a.closest('article,.result,.search-item,.item,li,tr');
      const heading=row && row.querySelector('h1,h2,h3,h4,.title');
      const label=clean(a.title||a.textContent);
      emit(uri,heading ? heading.textContent : /^(磁力|下载|复制|播放|magnet)(链接)?$/i.test(label) ? fallback : label||fallback);
    });
    const pattern=/magnet:\?[^"'<>\s]+/ig; let m;
    const html=doc.documentElement?doc.documentElement.outerHTML:'';
    while((m=pattern.exec(html)))emit(m[0],fallback);
  }
  window.__fmpScan=function(){
    parse(document,document.title); if(running){info();return;} running=true;
    const links=[...document.querySelectorAll('a[href]')].filter(a=>{
      try{
        const u=new URL(a.href,location.href);
        return /^https?:$/.test(u.protocol)&&u.origin===location.origin&&u.href!==location.href&&!visited.has(u.href)&&
          !a.closest('header,footer,nav,.pagination,.pager,.pages,.page-numbers')&&a.rel!=='next'&&a.rel!=='prev'&&
          !/^(首页|上一页|下一页|上页|下页|返回|登录|注册|next|previous|\d+)$/i.test(clean(a.textContent))&&
          clean(a.textContent||a.title).length>0;
      }catch(_){return false;}
    });
    const urls=[...new Map(links.map(a=>[a.href,a])).values()]; pending=urls.length; info();
    async function worker(){
      while(urls.length){
        const a=urls.shift();visited.add(a.href);const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),12000);
        try{const response=await fetch(a.href,{credentials:'include',signal:controller.signal});if(!response.ok)throw Error(response.status);
          parse(new DOMParser().parseFromString(await response.text(),'text/html'),a.title||a.textContent);failed.delete(a.href);
        }catch(_){failed.add(a.href);}finally{clearTimeout(timer);pending--;info();}
      }
    }
    Promise.all(Array.from({length:4},worker)).finally(()=>{running=false;info();});
  };
  window.__fmpScan();
  const observer=new MutationObserver(()=>window.__fmpScan());
  if(document.documentElement)observer.observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['href','data-magnet']});
  setTimeout(()=>observer.disconnect(),40000);
})();
