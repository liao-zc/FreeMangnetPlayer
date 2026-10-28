(function () {
  const query = __QUERY_JSON__;
  if (window.__fmpScan) { window.__fmpScan(); return; }
  const visited = new Set();
  const clean = value => (value || '').replace(/\s+/g, ' ').trim();
  const decode = value => { const el = document.createElement('textarea'); el.innerHTML = value; return el.value; };
  function emit(uri, label, fallback) {
    uri = decode(uri).trim();
    if (!/^magnet:\?/i.test(uri)) return;
    const dn = /(?:[?&])dn=([^&]+)/i.exec(uri);
    let name = '';
    if (dn) { try { name = decodeURIComponent(dn[1].replace(/\+/g, ' ')); } catch (_) {} }
    label = clean(label);
    if (/^(下载|磁力|magnet|复制|播放)(链接)?$/i.test(label)) label = '';
    window.FreeMagnetResults.onResults(JSON.stringify([{ title: label || clean(name) || clean(fallback) || '磁力资源', source: uri }]));
  }
  function parse(doc, fallback) {
    doc.querySelectorAll('a[href], [data-magnet]').forEach(a => {
      const href = a.getAttribute('data-magnet') || a.getAttribute('href') || '';
      if (/^magnet:/i.test(href)) emit(href, a.title || a.textContent, fallback);
    });
    const html = doc.documentElement ? doc.documentElement.outerHTML : '';
    for (const m of html.matchAll(/magnet:\?[^"'<>\s]+/ig)) emit(m[0], '', fallback);
  }
  let running = false;
  window.__fmpScan = async function () {
    parse(document, document.title);
    if (running || !query) return;
    running = true;
    try {
      const candidates = Array.from(document.querySelectorAll('a[href]')).filter(a => {
        try {
          const url = new URL(a.href, location.href);
          const card = a.closest('article,li,.item,.video-item,.module-item,.movie-item,.post');
          const text = clean((a.textContent || a.title || '') + ' ' + (card ? card.textContent : ''));
          return /^https?:/.test(url.protocol) && url.origin === location.origin && url.href !== location.href &&
            !visited.has(url.href) && text.includes(query);
        } catch (_) { return false; }
      });
      // Process all matching detail links in small batches; no fixed result truncation.
      for (let i = 0; i < candidates.length; i += 4) {
        await Promise.all(candidates.slice(i, i + 4).map(async a => {
          visited.add(a.href);
          try {
            const response = await fetch(a.href, {credentials: 'include'});
            if (!response.ok) return;
            const html = await response.text();
            parse(new DOMParser().parseFromString(html, 'text/html'), a.title || a.textContent);
          } catch (_) { visited.delete(a.href); }
        }));
      }
    } finally { running = false; }
  };
  window.__fmpScan();
  const observer = new MutationObserver(() => window.__fmpScan());
  observer.observe(document.documentElement, {childList: true, subtree: true, attributes: true, attributeFilter: ['href', 'data-magnet']});
  setTimeout(() => observer.disconnect(), 40000);
})();
