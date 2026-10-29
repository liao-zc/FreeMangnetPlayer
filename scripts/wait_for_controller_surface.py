from pathlib import Path
p=Path('scripts/verify_player_revision.py');s=p.read_text(encoding='utf-8')
start=s.index('def controller(id=None):');end=s.index('\ndef control(id):',start)
s=s[:start]+'''def controller(id=None):
    deadline=time.monotonic()+10
    while time.monotonic()<deadline:
        root=tree()
        target=next((n for n in root.iter('node') if n.get('resource-id','').endswith('/'+(id or 'player_position'))),None)
        if target is not None: return root,target
        surface=next((n for n in root.iter('node') if n.get('resource-id','').endswith('/video_surface')),None)
        if surface is not None: tap(surface)
        time.sleep(.2)
    raise AssertionError('Video/controller did not become visible after orientation transition')
'''+s[end:]
p.write_text(s,encoding='utf-8')
