from pathlib import Path
p = Path('scripts/verify_performance.py')
s = p.read_text(encoding='utf-8')
old = '''    assert expand, 'System PiP expand control missing: ' + menu
    bounds = list(map(int, re.findall(r'\\d+', expand[0].get('bounds'))))
    adb('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))'''
new = '''    if expand:
        bounds = list(map(int, re.findall(r'\\d+', expand[0].get('bounds'))))
    else:
        # The PiP menu is a nonfocusable SystemUI overlay and is not included in
        # uiautomator's active-window tree. Its actual screenshot shows the grey
        # menu panel with the fullscreen icon at its center. Locate that panel.
        from PIL import Image
        windows = adb('shell', 'dumpsys', 'window', 'windows')
        (OUT / 'pip-system-windows.txt').write_text(windows)
        assert 'PipMenuActivity' in windows, 'No real SystemUI PiP menu window'
        image = Image.open(OUT / 'perf-07b-system-menu.png').convert('RGB')
        width, height = image.size
        mask = Image.new('1', image.size)
        pixels = image.load()
        selected = mask.load()
        for y in range(height // 2, height):
            for x in range(width // 2, width):
                r, g, b = pixels[x, y]
                if 110 <= r <= 125 and max(r, g, b) - min(r, g, b) <= 2:
                    selected[x, y] = 1
        bounds = mask.getbbox()
        assert bounds and bounds[2] - bounds[0] > 200 and bounds[3] - bounds[1] > 100, 'PiP menu panel not detected'
        (OUT / 'pip-expand-target.json').write_text(json.dumps({'panel_bounds': bounds, 'method': 'actual SystemUI screenshot'}))
    adb('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))'''
assert old in s
s = s.replace(old, new)
p.write_text(s, encoding='utf-8')
p = Path('.github/workflows/capture-screenshots.yml')
s = p.read_text(encoding='utf-8').replace('python3-libtorrent ffmpeg', 'python3-libtorrent python3-pil ffmpeg')
p.write_text(s, encoding='utf-8')
p = Path('scripts/build_performance_docs.py')
s = p.read_text(encoding='utf-8').replace("text += image('perf-08-return-from-floating.png'", "text += image('perf-07b-system-menu.png', '实际系统悬浮窗菜单中的展开与关闭控制')\ntext += image('perf-08-return-from-floating.png'")
p.write_text(s, encoding='utf-8')
print('PiP expansion now locates the actual nonfocusable SystemUI menu from its screenshot')
