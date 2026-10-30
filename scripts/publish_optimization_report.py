"""Upload decrypted report/source/evidence bytes and verify GitHub asset digests."""
import argparse
import hashlib
import io
import json
import subprocess
import urllib.request
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--version', required=True)
parser.add_argument('--html', required=True)
parser.add_argument('--evidence-prefix', default='optimization')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
repo = 'liao-zc/OmniPlayer'
token = subprocess.check_output(['gh', 'auth', 'token'], text=True).strip()
headers = {'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'}

def api(url, method='GET', data=None, mime=None):
    h = dict(headers)
    if data is not None:
        h['Content-Length'] = str(len(data))
        h['Content-Type'] = mime or 'application/octet-stream'
    with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=h, method=method), timeout=180) as response:
        body = response.read()
        return json.loads(body) if body else None

release = api(f'https://api.github.com/repos/{repo}/releases/tags/{args.version}')
archive = subprocess.check_output(['git', 'archive', '--format=zip', f'--prefix=OmniPlayer-{args.version}/', 'HEAD'], cwd=root)
buffer = io.BytesIO()
with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as bundle:
    for path in sorted((root / 'docs/evidence').glob(args.evidence_prefix + '*')):
        bundle.writestr(path.name, path.read_bytes())
assets = {
    f'OmniPlayer-{args.version}-report.html': (Path(args.html).read_bytes(), 'text/html; charset=utf-8'),
    f'OmniPlayer-{args.version}-source.zip': (archive, 'application/zip'),
    f'OmniPlayer-{args.version}-evidence.zip': (buffer.getvalue(), 'application/zip'),
}
for name, (data, mime) in assets.items():
    if data.startswith(b'E-SafeNet'):
        raise RuntimeError('Encrypted release payload: ' + name)
    previous = next((item for item in release['assets'] if item['name'] == name), None)
    digest = 'sha256:' + hashlib.sha256(data).hexdigest()
    if previous and previous.get('digest') == digest:
        print('Already current:', name)
        continue
    if previous:
        api(previous['url'], method='DELETE')
    result = api(f'https://uploads.github.com/repos/{repo}/releases/{release["id"]}/assets?name={name}', method='POST', data=data, mime=mime)
    if result['size'] != len(data) or result.get('digest') != digest:
        raise RuntimeError('GitHub payload digest does not match: ' + name)
    print('Verified:', name, len(data), digest)
