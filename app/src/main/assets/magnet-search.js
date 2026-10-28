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
    Array.from(doc.querySelectorAll('a[href], [data-magnet]')).forEach(a => {
      const href = a.getAttribute('data-magnet') || a.getAttribute('href') || '';
      if (/^magnet:/i.test(href)) emit(href, a.title || a.textContent, fallback);
    });
    const html = doc.documentElement ? doc.documentElement.outerHTML : '';
    const pattern = /magnet:\?[^"'<>\s]+/ig;
    let match;
    while ((match = pattern.exec(html)) !== null) emit(match[0], '', fallback);
  }
  let running = false;
  window.__fmpScan = function () {
    parse(document, document.title);
    if (running || !query) return;
    running = true;
    try {
      const candidates = Array.from(document.querySelectorAll('a[href]')).filter(a => {
        try {
          const url = new URL(a.href, location.href);
          const text = clean(a.textContent || a.title || '');
          return /^https?:/.test(url.protocol) && url.origin === location.origin && url.href !== location.href &&
            !visited.has(url.href) && text.includes(query);
        } catch (_) { return false; }
      });
      // Process all matching detail links in small batches; no fixed result truncation.
      function batch(i) {
        if (i >= candidates.length) { running = false; return; }
        Promise.all(candidates.slice(i, i + 4).map(a => {
          visited.add(a.href);
          return fetch(a.href, {credentials: 'include'}).then(response => {
            if (!response.ok) throw new Error('HTTP ' + response.status);
            return response.text();
          }).then(html => parse(new DOMParser().parseFromString(html, 'text/html'), a.title || a.textContent))
            .catch(() => visited.delete(a.href));
        })).then(() => batch(i + 4));
      }
      batch(0);
    } catch (_) { running = false; }
  };
  window.__fmpScan();
  const observer = new MutationObserver(() => window.__fmpScan());
  if (document.documentElement) observer.observe(document.documentElement, {childList: true, subtree: true, attributes: true, attributeFilter: ['href', 'data-magnet']});
  setTimeout(() => observer.disconnect(), 40000);
})();
