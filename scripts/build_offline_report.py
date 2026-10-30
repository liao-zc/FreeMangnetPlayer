"""Build a standalone report with real screenshots embedded, using pip install markdown."""
import argparse
import base64
import re
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument("--output", default=str(ROOT / "dist/FreeMangnetPlayer-v0.1.9-report.html"))
parser.add_argument("--source", default="docs/usage-zh.md")
args = parser.parse_args()
source = (ROOT / args.source).resolve()
body = markdown.markdown(source.read_text(encoding="utf-8"), extensions=["tables", "fenced_code"])


def embed(match):
    target = (source.parent / match.group(1)).resolve()
    if not target.is_relative_to(ROOT) or not target.is_file():
        raise RuntimeError(f"Missing report screenshot: {target}")
    return 'src="data:image/png;base64,' + base64.b64encode(target.read_bytes()).decode("ascii") + '"'


body = re.sub(r'src="([^":]+\.png)"', embed, body)
body = re.sub(r'href="(?!https?:|#)([^"]+)"',
              lambda m: 'href="https://github.com/liao-zc/OmniPlayer/blob/main/docs/' + m.group(1) + '"', body)
html = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>FreeMangnetPlayer 实测报告</title><style>
body{font-family:system-ui,sans-serif;line-height:1.7;max-width:1000px;margin:24px auto;padding:0 20px;color:#14202b}
img{display:block;max-width:100%;width:360px;height:auto;margin:20px 0;border:1px solid #ddd}
table{border-collapse:collapse;width:100%;font-size:14px}td,th{border:1px solid #ccc;padding:8px;text-align:left}
pre{overflow:auto;background:#f3f8fa;padding:16px}a{color:#087f79}h2{margin-top:36px}
@media print{img{max-height:650px;width:auto}table{font-size:10px}}
</style><body>''' + body + '</body></html>'
output = Path(args.output)
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(html, encoding="utf-8")
print(f"Standalone HTML report: {output} ({output.stat().st_size} bytes)")
