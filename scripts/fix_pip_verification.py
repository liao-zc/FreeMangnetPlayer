from pathlib import Path
p = Path('scripts/verify_performance.py')
s = p.read_text(encoding='utf-8')
old = "    adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/.PlayerActivity', '--es', 'source', shlex.quote(fixture['direct_magnet']), '--es', 'mode', 'play')"
assert old in s
new = '''    # Expand through the real system PiP menu, preserving the same player instance.
    windows = adb('shell', 'dumpsys', 'window', 'windows')
    section = windows[windows.find('com.freemangnetplayer/.PlayerActivity'):]
    frame = re.search(r'mFrame=\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]', section)
    if frame:
        x1, y1, x2, y2 = map(int, frame.groups())
    else:
        import struct
        width, height = struct.unpack('>II', (OUT / 'perf-07-floating-over-home.png').read_bytes()[16:24])
        x1, y1, x2, y2 = width - 350, height - 450, width - 30, height - 150
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)
    adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml')
    menu = adb('shell', 'cat', '/sdcard/window.xml')
    (OUT / 'pip-system-menu.xml').write_text(menu)
    tree = ET.fromstring(menu)
    expand = [n for n in tree.iter('node') if 'expand' in n.get('resource-id', '').lower()
        or any(label in n.get('content-desc', '').lower() for label in ['full screen', 'fullscreen', 'expand'])]
    assert expand, 'System PiP expand control missing: ' + menu
    bounds = list(map(int, re.findall(r'\\d+', expand[0].get('bounds'))))
    adb('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))'''
s = s.replace(old, new)
p.write_text(s, encoding='utf-8')
print('PiP test now uses actual system expand control')
