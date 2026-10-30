"""Verify committed Markdown bytes and actual GitHub rendering after every release."""
import base64,json,subprocess,time
def gh(*args,data=None):
    for attempt in range(5):
        p=subprocess.run(['gh',*args],input=data,capture_output=True)
        if p.returncode==0:return p.stdout
        time.sleep(2**attempt)
    raise RuntimeError(p.stderr.decode('utf-8',errors='replace'))
repo='liao-zc/OmniPlayer'
for path in ['README.md','docs/usage-zh.md','docs/report.md']:
    committed=subprocess.check_output(['git','show','HEAD:'+path])
    remote=json.loads(gh('api',f'repos/{repo}/contents/{path}'));raw=base64.b64decode(remote['content']);assert raw==committed,path
    text=raw.decode('utf-8');assert b'E-SafeNet' not in raw[:256] and '\x00' not in text
    rendered=gh('api','markdown','--input','-',data=json.dumps({'text':text,'mode':'gfm','context':repo}).encode('utf-8'))
    assert b'<h1' in rendered and b'E-SafeNet' not in rendered
    if path!='README.md':assert b'<img' in rendered
    print('GitHub plaintext and rendering verified:',path,len(raw))
