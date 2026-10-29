from pathlib import Path
p = Path('.github/workflows/docs.yml')
s = p.read_text(encoding='utf-8-sig')
s = s.replace('          import json, subprocess', '          import json, subprocess, time')
old = "              result = subprocess.run(['gh', 'api', 'markdown', '--input', '-'], input=json.dumps(payload), text=True, capture_output=True, check=True)"
new = '''              for attempt in range(5):
                  result = subprocess.run(['gh', 'api', 'markdown', '--input', '-'], input=json.dumps(payload), text=True, capture_output=True)
                  if result.returncode == 0:
                      break
                  print('GitHub render request failed; retry:', filename, attempt + 1, result.stderr[-400:])
                  time.sleep(2 ** attempt)
              if result.returncode != 0:
                  raise RuntimeError('GitHub rendering failed: ' + filename + ': ' + result.stderr[-400:])'''
assert old in s
s = s.replace(old, new)
p.write_text(s, encoding='utf-8')
print('Markdown rendering retries transient API failures without skipping validation')
