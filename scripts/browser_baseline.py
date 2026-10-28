"""Independent Chromium search baseline; preserve failures instead of inventing results."""
import json
import re
from pathlib import Path
from urllib.parse import quote, urljoin, urlparse
from playwright.sync_api import sync_playwright
from magnet_hash import canonical_hash

QUERY = "哪吒"
ENGINES = [
    ("clg.im", "https://clg.im/", True),
    ("BT搜索（dobt）", "https://mg.dobt.cc/", True),
    ("BD电影首发站", "https://www.bdflixs.com/?s=" + quote(QUERY), False),
    ("Pomo", "https://pomo.mom/?keyword=" + quote(QUERY), False),
    ("美剧粉", "https://www.mjf2020.com/ss/?s=" + quote(QUERY), False),
    ("高清族", "https://hdzu.org/movie/filter?act=title&wd=" + quote(QUERY), False),
]
OUT = Path("manual-shots")
OUT.mkdir(exist_ok=True)
baseline = []
with sync_playwright() as pw:
    browser = pw.chromium.launch(headless=True)
    # Use a normal mobile browser UA, matching the app. HeadlessChrome's default
    # UA triggers access blocks that a user's ordinary browser does not see.
    context = browser.new_context(viewport={"width": 360, "height": 800}, device_scale_factor=3, is_mobile=True,
        user_agent="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
    for index, (name, url, form) in enumerate(ENGINES, 1):
        page = context.new_page()
        row = {"engine": name, "query": QUERY, "url": url, "hashes": [], "detail_pages": [], "error": ""}
        try:
            response = page.goto(url, wait_until="domcontentloaded", timeout=30000)
            row["http_status"] = response.status if response else None
            row["http_attempts"] = [row["http_status"]]
            for retry in range(2):
                if not response or response.status != 403:
                    break
                page.wait_for_timeout(2000)
                response = page.goto(url, wait_until="domcontentloaded", timeout=30000)
                row["http_status"] = response.status if response else None
                row["http_attempts"].append(row["http_status"])
            if response and response.status >= 400:
                raise RuntimeError(f"Website returned HTTP {response.status}")
            page.wait_for_timeout(4000)
            challenge = page.locator(".challenge-c")
            if challenge.count():
                challenge.first.click()
                page.wait_for_timeout(5000)
            if index == 2 and len(page.frames) > 1:
                frame_url = next((f.url for f in page.frames[1:] if f.url.startswith("http")), "")
                if frame_url:
                    page.goto(frame_url, wait_until="domcontentloaded", timeout=30000)
            if form:
                field = page.locator('input[type=search],input[type=text]').filter(visible=True).first
                field.fill(QUERY, timeout=10000)
                field.press("Enter")
                page.wait_for_timeout(7000)
            html = page.content()
            (OUT / f"browser-{index}.html").write_text(html, encoding="utf-8")
            page.screenshot(path=str(OUT / f"browser-{index}.png"), full_page=True)
            anchors = page.locator("a[href]").evaluate_all("els=>els.map(a=>({url:a.href,title:a.textContent||a.title||''}))")
            links = list(dict.fromkeys(a["url"] for a in anchors if QUERY in a["title"] and
                         urlparse(a["url"]).netloc == urlparse(page.url).netloc and a["url"] != page.url))
            texts = [html]
            for link in links:
                try:
                    response = context.request.get(link, timeout=15000)
                    if response.ok:
                        texts.append(response.text())
                        row["detail_pages"].append(link)
                except Exception as error:
                    row.setdefault("detail_errors", []).append(str(error))
            magnets = re.findall(r"(?i)magnet:\?[^\s\"'<>]+", "\n".join(texts))
            row["hashes"] = sorted(set(h for h in map(canonical_hash, magnets) if h))
            row["page_title"] = page.title()
            row["final_url"] = page.url
        except Exception as error:
            row["error"] = str(error)
            try:
                row["final_url"] = page.url
                (OUT / f"browser-{index}.html").write_text(page.content(), encoding="utf-8")
                page.screenshot(path=str(OUT / f"browser-{index}.png"))
            except Exception:
                pass
        baseline.append(row)
        print(json.dumps(row, ensure_ascii=False), flush=True)
        page.close()
    browser.close()
(OUT / "browser-baseline.json").write_text(json.dumps(baseline, ensure_ascii=False, indent=2), encoding="utf-8")
