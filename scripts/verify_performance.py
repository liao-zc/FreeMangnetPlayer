"""Measure real APK throughput and exercise pasted magnets, fullscreen and PiP."""
import base64
import hashlib
import json
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from capture_usage_screenshots import adb, screenshot, require_visible, find_and_tap, PACKAGE, OUT

fixture = json.loads(Path('performance-fixture.json').read_text())
report = []

def state():
    try:
        return json.loads(adb('shell', 'run-as', PACKAGE, 'cat', 'files/torrent-check.json'))
    except Exception:
        return {}

def wait(predicate, timeout=90):
    start = time.monotonic()
    while time.monotonic() - start < timeout:
        s = state()
        if predicate(s):
            return s
        time.sleep(.5)
    raise RuntimeError('App condition failed: ' + json.dumps(state(), ensure_ascii=False))

def clear_app():
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'pm', 'clear', PACKAGE)
    adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
    adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/.MainActivity')
    time.sleep(2)

def paste(value):
    # Actual focus + input events; no Activity source extra or .torrent injection.
    find_and_tap('磁力链接输入框')
    adb('shell', 'input', 'text', shlex.quote(value))
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    screenshot('perf-02-pasted-magnet.png')

def main():
    adb('wait-for-device')
    adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
    adb('shell', 'wm', 'size', '1080x2400')
    clear_app()
    require_visible('设置', '解析资源', '播放', '下载', '收藏')
    adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml')
    tree = ET.fromstring(adb('shell', 'cat', '/sdcard/window.xml'))
    settings = next(n for n in tree.iter('node') if n.get('text') == '设置')
    bounds = list(map(int, re.findall(r'\d+', settings.get('bounds'))))
    assert bounds[1] > 1800, bounds
    screenshot('perf-01-bottom-settings.png')
    find_and_tap('设置')
    require_visible('个性化设置', '返回首页')
    screenshot('perf-01b-settings.png')
    find_and_tap('返回首页')
    report.append({'check': 'bottom-settings-entry', 'passed': True, 'bounds': bounds})

    # A direct peer parameter tests a tracker-free magnet through the normal UI.
    paste(fixture['direct_magnet'])
    started = time.monotonic()
    find_and_tap('解析资源')
    metadata = wait(lambda s: s.get('id') == fixture['id'] and s.get('metadata'))
    metadata_seconds = time.monotonic() - started
    screenshot('perf-03-real-metadata.png')
    assert metadata['downloadLimit'] == 0 and metadata['uploadLimit'] == 0, metadata
    report.append({'check': 'pasted-tracker-free-magnet-metadata', 'passed': True,
        'seconds': metadata_seconds, 'source': fixture['direct_magnet'], 'state': metadata})
    find_and_tap('▶ 播放')
    first = wait(lambda s: s.get('playing'))
    first_seconds = time.monotonic() - started
    screenshot('perf-04-unlimited-playing.png')
    samples = [metadata, first]
    started_sample = time.monotonic()
    while time.monotonic() - started_sample < 20:
        current = state()
        samples.append(current)
        if current.get('downloaded', 0) >= current.get('total', 1):
            break
        time.sleep(.5)
    peak = max(s.get('payloadRate', 0) for s in samples)
    assert peak > 64 * 1024, ('Throughput still unusable', peak, samples)
    assert all(s.get('downloadLimit') == 0 and s.get('uploadLimit') == 0 for s in samples), samples
    report.append({'check': 'unlimited-app-throughput', 'passed': True,
        'peak_payload_bytes_per_second': peak, 'first_play_seconds': first_seconds,
        'played_before_complete': first['downloaded'] < first['total'], 'first_play_state': first,
        'seed_limits': fixture['seed_limits'], 'native_baseline': fixture['baseline'], 'samples': samples})
    find_and_tap('网络诊断')
    require_visible('真实网络诊断', '0 为不限速', '有效载荷速度', 'TCP / uTP')
    screenshot('perf-05-native-diagnostics.png')
    find_and_tap('关闭')
    report.append({'check': 'real-native-limits-and-network-diagnostics', 'passed': True})

    # Use the real rewind control to show a representative movie scene.
    for attempt in range(2):
        if state().get('position', 0) > 25000:
            find_and_tap('后退 15 秒')
    wait(lambda s: s.get('playing'))
    find_and_tap('全屏播放')
    full = wait(lambda s: s.get('fullscreen') and s.get('playing'))
    from capture_usage_screenshots import visible_text
    if 'Got it' in visible_text():
        find_and_tap('Got it')
    require_visible('退出全屏', '悬浮播放', '切换视频')
    time.sleep(2)
    screenshot('perf-06-fullscreen-film.png')
    p1 = state()['position']
    time.sleep(3)
    p2 = state()['position']
    assert p2 > p1 + 1000, (p1, p2)
    report.append({'check': 'immersive-fullscreen-real-playback', 'passed': True, 'state': full, 'position_before': p1, 'position_after': p2})
    find_and_tap('悬浮播放')
    floating = wait(lambda s: s.get('floating') and s.get('playing'))
    activities = adb('shell', 'dumpsys', 'activity', 'activities')
    assert 'mode=pinned' in activities or 'windowingMode=2' in activities, activities[-5000:]
    time.sleep(2)
    screenshot('perf-07-floating-over-home.png')
    p1 = state()['position']
    time.sleep(5)
    p2 = state()['position']
    assert p2 > p1 + 2000, (p1, p2)
    report.append({'check': 'system-pip-over-home-continues-playing', 'passed': True, 'state': floating,
        'position_before': p1, 'position_after': p2})
    # Expand through the real system PiP menu, preserving the same player instance.
    windows = adb('shell', 'dumpsys', 'window', 'windows')
    section = windows[windows.find('com.freemangnetplayer/.PlayerActivity'):]
    frame = re.search(r'mFrame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]', section)
    import struct
    width, height = struct.unpack('>II', (OUT / 'perf-07-floating-over-home.png').read_bytes()[16:24])
    if frame:
        x1, y1, x2, y2 = map(int, frame.groups())
    if not frame or x2 - x1 > width * .8 or y2 - y1 > height * .8:
        x1, y1, x2, y2 = width - 350, height - 450, width - 30, height - 150
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)
    adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml')
    screenshot('perf-07b-system-menu.png')
    menu = adb('shell', 'cat', '/sdcard/window.xml')
    (OUT / 'pip-system-menu.xml').write_text(menu)
    tree = ET.fromstring(menu)
    expand = [n for n in tree.iter('node') if 'expand' in n.get('resource-id', '').lower()
        or any(label in n.get('content-desc', '').lower() for label in ['full screen', 'fullscreen', 'expand'])]
    if expand:
        bounds = list(map(int, re.findall(r'\d+', expand[0].get('bounds'))))
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
    adb('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
    restored = wait(lambda s: not s.get('floating') and s.get('playing'))
    screenshot('perf-08-return-from-floating.png')
    report.append({'check': 'return-from-pip', 'passed': True, 'state': restored})

    complete = wait(lambda s: s.get('downloaded', 0) >= fixture['size'], 120)
    file = complete['folder'] + '/' + fixture['path']
    output = subprocess.check_output(['adb', 'shell', 'run-as', PACKAGE, 'sha256sum', shlex.quote(file)], text=True)
    actual = output.split()[0]
    assert actual == fixture['sha256'], (actual, fixture['sha256'])
    report.append({'check': 'unlimited-download-complete-file-sha256', 'passed': True, 'size': fixture['size'], 'sha256': actual})

    # Cold import of an HTML-encoded link through the input field, with no saved metadata.
    clear_app()
    html = fixture['magnet'].replace('&', '&amp;')
    paste(html)
    find_and_tap('解析资源')
    parsed = wait(lambda s: s.get('metadata') and s.get('id') == fixture['id'])
    screenshot('perf-09-html-pasted-parsed.png')
    report.append({'check': 'html-encoded-paste-cold-parse', 'passed': True, 'state': parsed})

    # A pure hash without trackers or peers: parsing is tested; live discovery is recorded honestly.
    clear_app()
    paste(fixture['plain_magnet'])
    find_and_tap('解析资源')
    time.sleep(20)
    plain = state()
    require_visible('返回搜索', '网络诊断')
    screenshot('perf-10-plain-hash-discovery.png')
    assert plain.get('id') == fixture['id'] and plain.get('downloadLimit') == 0, plain
    report.append({'check': 'plain-hash-input-and-responsive-discovery', 'passed': True,
        'metadata_received': plain.get('metadata', False), 'state': plain,
        'note': 'The isolated seed is not guaranteed discoverable on public DHT; no injected metadata or peers.'})
    find_and_tap('返回搜索')
    require_visible('解析资源', '设置')
    report.append({'check': 'return-during-plain-magnet-discovery', 'passed': True})
    paste(fixture['direct_magnet'])
    find_and_tap('解析资源')
    enriched = wait(lambda s: s.get('id') == fixture['id'] and s.get('metadata'))
    assert '&x.pe=' in enriched['source'], enriched
    screenshot('perf-10b-reopen-with-new-peer.png')
    report.append({'check': 'existing-hash-merges-new-network-parameters', 'passed': True, 'state': enriched})
    find_and_tap('返回搜索')

    baseline_path = Path('public-network-baseline.json')
    if baseline_path.exists():
        public = json.loads(baseline_path.read_text())
        if public.get('plain_magnet'):
            clear_app()
            paste(public['plain_magnet'])
            find_and_tap('解析资源')
            started = time.monotonic()
            public_samples = []
            while time.monotonic() - started < 75:
                current = state()
                public_samples.append(current)
                if current.get('payloadBytes', 0) >= 8 * 1024 * 1024:
                    break
                time.sleep(1)
            screenshot('perf-11-public-dht-resource.png')
            report.append({'check': 'public-pure-hash-comparison', 'measured': True,
                'metadata_received': current.get('metadata', False),
                'payload_received': current.get('payloadBytes', 0),
                'peak_payload_bytes_per_second': max(s.get('payloadRate', 0) for s in public_samples),
                'native_baseline': public, 'state': current, 'samples': public_samples,
                'note': 'A public network measurement is not converted into a pass when no peers or bytes are received.'})
            find_and_tap('返回搜索')


try:
    main()
finally:
    (OUT / 'performance-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    (OUT / 'performance-fixture.json').write_text(json.dumps(fixture, indent=2), encoding='utf-8')
    (OUT / 'performance-last-state.json').write_text(json.dumps(state(), ensure_ascii=False, indent=2), encoding='utf-8')
    subprocess.run(['adb', 'logcat', '-d'], stdout=(OUT / 'performance-logcat.txt').open('w'), check=False)
