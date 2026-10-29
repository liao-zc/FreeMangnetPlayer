"""Drive actual APK UI, then check real bytes and native task state."""
import hashlib
import json
import shlex
import subprocess
import time
from pathlib import Path
from capture_usage_screenshots import adb, screenshot, require_visible, find_and_tap, PACKAGE, OUT

report = []
fixtures = json.loads(Path('optimization-fixture.json').read_text())

def diagnostic(name='torrent'):
    try:
        return json.loads(adb('shell', 'run-as', PACKAGE, 'cat', f'files/{name}-check.json'))
    except Exception:
        return {} if name == 'torrent' else []

def wait(predicate, timeout=120):
    deadline = time.time() + timeout
    while time.time() < deadline:
        state = diagnostic()
        if predicate(state):
            return state
        time.sleep(1)
    raise RuntimeError('Condition failed: ' + json.dumps(diagnostic(), ensure_ascii=False))

def open_torrent(fixture, mode='parse'):
    adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/.MainActivity', '-a', 'android.intent.action.VIEW', '-d', shlex.quote(fixture['magnet']), '--es', 'mode', mode)
    time.sleep(3)
    return wait(lambda s: s.get('id') == fixture['id'] and (s.get('metadata') or s.get('queued')))

def write_prefs(values):
    # Used only to supply a deterministic search endpoint; task and setting tests use UI.
    subprocess.run(['adb', 'shell', 'run-as', PACKAGE, 'mkdir', '-p', 'shared_prefs'], check=True)
    subprocess.run(['adb', 'shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('cat > shared_prefs/player-settings.xml')], input=values.encode(), check=True)

def edit(label, text):
    find_and_tap(label)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    adb('shell', 'input', 'keyevent', '--longpress', 'KEYCODE_DEL')
    # CTRL+A selects all text in the native EditText/Compose field.
    adb('shell', 'input', 'keycombination', '113', '29')
    adb('shell', 'input', 'text', shlex.quote(text))
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(1)

def main():
    adb('wait-for-device')
    adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
    adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
    adb('shell', 'wm', 'size', '1080x2400')
    adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/.MainActivity')
    time.sleep(4)
    require_visible('设置', '解析资源', '播放', '下载', '收藏')
    screenshot('opt-01-home.png')
    find_and_tap('设置')
    require_visible('下载目录', '缓存目录', '最大同时下载', '默认搜索引擎')
    base = f'/storage/emulated/0/Android/data/{PACKAGE}/files'
    edit('下载目录（绝对路径）', base + '/CustomDownloads')
    edit('缓存目录（绝对路径）', base + '/CustomCache')
    edit('最大同时下载数量（1–8，含缓存）', '1')
    edit('最大缓存任务数量（1–100）', '1')
    screenshot('opt-02-settings-storage.png')
    # Scroll to engine settings and save using actual UI.
    adb('shell', 'input', 'swipe', '540', '1800', '540', '650', '600')
    find_and_tap('默认搜索引擎')
    find_and_tap('Pomo')
    find_and_tap('保存设置')
    require_visible('设置已保存')
    screenshot('opt-03-settings-saved.png')
    find_and_tap('返回首页')
    find_and_tap('选择搜索引擎')
    require_visible('搜索引擎：Pomo')
    screenshot('opt-03b-default-engine.png')
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    report.append({'check': 'settings-ui-save-and-default-engine', 'passed': True, 'download_path': base + '/CustomDownloads', 'cache_path': base + '/CustomCache', 'max_downloads': 1})

    state = open_torrent(fixtures[0])
    assert state['folder'].startswith(base + '/CustomCache/'), state
    assert len(state['files']) == 3, state
    screenshot('opt-04-real-multifile.png')
    find_and_tap('选择文件')
    find_and_tap('全不选')
    # Tap the checkbox by row bounds; file name label shares the checkbox row.
    find_and_tap('Episode-01.mp4')
    # Label clicks don't toggle a Checkbox by default; explicitly locate the nearest checkbox.
    select_checkbox('Episode-01.mp4')
    find_and_tap('完成')
    state = wait(lambda s: len([f for f in s.get('files', []) if f.get('selected')]) == 1)
    selected = next(f for f in state['files'] if f['selected'])
    assert selected['path'].endswith('Episode-01.mp4'), state
    report.append({'check': 'single-file-selection', 'passed': True, 'files': state['files']})
    find_and_tap('▶ 播放')
    state = wait(lambda s: s.get('playing') and 0 < s.get('downloaded', 0) < s.get('total', 0), 90)
    screenshot('opt-05-real-streaming.png')
    report.append({'check': 'stream-before-download-complete', 'passed': True, 'state': state})
    find_and_tap('暂停下载')
    time.sleep(5)
    before = diagnostic()
    time.sleep(6)
    after = diagnostic()
    assert after['paused'] and after['downloaded'] == before['downloaded'], (before, after)
    screenshot('opt-06-paused.png')
    report.append({'check': 'pause-stops-real-bytes', 'passed': True, 'before': before['downloaded'], 'after': after['downloaded']})
    find_and_tap('继续下载')
    wait(lambda s: not s.get('paused') and s.get('downloaded', 0) > after['downloaded'])
    report.append({'check': 'resume-download', 'passed': True})
    active_before = diagnostic()
    assert 0 < active_before['downloaded'] < active_before['total'], active_before
    find_and_tap('取消下载')
    wait(lambda s: s.get('cancelled'))
    time.sleep(5)
    a = diagnostic()['downloaded']; time.sleep(5); b = diagnostic()['downloaded']
    assert a == b and a > 0, (a, b)
    screenshot('opt-06b-active-cancel.png')
    report.append({'check': 'cancel-active-partial-download', 'passed': True, 'before': a, 'after': b})
    state = open_torrent(fixtures[0], 'download')
    wait(lambda s: s.get('downloaded', 0) > b and s.get('folder', '').startswith(base + '/CustomDownloads/'))
    report.append({'check': 'reopen-cancelled-task-resumes-from-verified-cache', 'passed': True})
    find_and_tap('下载')
    state = wait(lambda s: s.get('folder', '').startswith(base + '/CustomDownloads/'))
    before = state['downloaded']
    find_and_tap('返回搜索')
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(15)
    tasks = diagnostic('tasks')
    current = next(t for t in tasks if t['id'] == fixtures[0]['id'])
    assert current['done'] > before, (before, tasks)
    screenshot('opt-07-background-home.png')
    services = adb('shell', 'dumpsys', 'activity', 'services', PACKAGE)
    assert 'DownloadService' in services and 'isForeground=true' in services, services
    report.append({'check': 'background-after-back-and-home', 'passed': True, 'before': before, 'after': current['done'], 'foreground_service': True})

    state = open_torrent(fixtures[0], 'play')
    find_and_tap('选择文件')
    find_and_tap('全选')
    screenshot('opt-08-multi-select.png')
    find_and_tap('完成')
    state = wait(lambda s: len([f for f in s.get('files', []) if f.get('selected')]) == 3)
    report.append({'check': 'multi-file-selection', 'passed': True, 'files': state['files']})
    find_and_tap('选择文件')
    find_and_tap('切换播放：Episode-02.mp4')
    state = wait(lambda s: s.get('playing') and any(f['index'] == s.get('currentFile') and f['path'].endswith('Episode-02.mp4') for f in s.get('files', [])))
    screenshot('opt-09-switch-second-video.png')
    report.append({'check': 'switch-video-inside-player', 'passed': True, 'state': state})

    # Fill the single download slot, then prove another actual torrent waits.
    open_torrent(fixtures[2])
    state = open_torrent(fixtures[1], 'download')
    assert state.get('queued'), state
    screenshot('opt-10-queued-task.png')
    report.append({'check': 'maximum-downloads-queue', 'passed': True, 'state': state})
    find_and_tap('取消下载')
    wait(lambda s: s.get('cancelled'))
    time.sleep(4)
    a = diagnostic()['downloaded']; time.sleep(4); b = diagnostic()['downloaded']
    assert a == b, (a, b)
    screenshot('opt-11-cancelled.png')
    report.append({'check': 'cancel-download', 'passed': True, 'before': a, 'after': b})

    state = open_torrent(fixtures[0])
    state = wait(lambda s: s.get('downloaded', 0) >= s.get('total', 1) > 0, 240)
    for expected in fixtures[0]['files']:
        actual = adb('shell', 'sha256sum', state['folder'] + '/' + expected['path']).split()[0]
        assert actual == expected['sha256'], (actual, expected)
    screenshot('opt-12-complete-download.png')
    report.append({'check': 'all-selected-files-download-sha256', 'passed': True, 'folder': state['folder'], 'files': fixtures[0]['files']})
    find_and_tap('▶ 播放')
    wait(lambda s: s.get('playing'))
    find_and_tap('1.0×')
    wait(lambda s: s.get('speed') == 1.25)
    for _ in range(3): find_and_tap('后退 15 秒')
    before = diagnostic()['position']
    find_and_tap('前进 15 秒')
    wait(lambda s: s.get('position', 0) > before + 10000, 15)
    screenshot('opt-13-speed-seek.png')
    report.append({'check': 'speed-and-seek-after-switch', 'passed': True})
    find_and_tap('返回搜索')
    # Force-stop is a system stop, not a normal background operation. Reopen to check cache preference and persistence.
    adb('shell', 'am', 'force-stop', PACKAGE)
    state = open_torrent(fixtures[0], 'play')
    state = wait(lambda s: s.get('playing'))
    assert state['folder'].startswith(base + '/CustomDownloads/'), state
    screenshot('opt-14-local-cache-reopen.png')
    report.append({'check': 'prefer-configured-local-file-after-restart', 'passed': True, 'state': state})
    find_and_tap('返回搜索')
    find_and_tap('设置')
    require_visible('CustomDownloads', 'CustomCache')
    report.append({'check': 'settings-cold-persistence', 'passed': True})
    adb('shell', 'input', 'swipe', '540', '1800', '540', '400', '600')
    find_and_tap('一键清空下载')
    find_and_tap('确认清空')
    time.sleep(8)
    exists = adb('shell', 'test', '-d', state['folder']) if False else subprocess.run(['adb', 'shell', 'test', '-d', state['folder']]).returncode == 0
    assert not exists, state['folder']
    screenshot('opt-15-clear-downloads.png')
    report.append({'check': 'clear-downloads-removes-files', 'passed': True, 'deleted_folder': state['folder']})
    find_and_tap('返回首页')
    state = open_torrent(fixtures[2])
    cache_folder = state['folder']
    find_and_tap('返回搜索')
    find_and_tap('设置')
    adb('shell', 'input', 'swipe', '540', '1800', '540', '400', '600')
    find_and_tap('一键清空缓存')
    find_and_tap('确认清空')
    time.sleep(8)
    assert subprocess.run(['adb', 'shell', 'test', '-d', cache_folder]).returncode != 0
    screenshot('opt-16-clear-cache.png')
    report.append({'check': 'clear-cache-removes-files', 'passed': True, 'deleted_folder': cache_folder})
    find_and_tap('返回首页')
    first_cache = open_torrent(fixtures[2])
    find_and_tap('返回搜索')
    second_cache = open_torrent(fixtures[3])
    time.sleep(8)
    assert subprocess.run(['adb', 'shell', 'test', '-d', first_cache['folder']]).returncode != 0
    assert subprocess.run(['adb', 'shell', 'test', '-d', second_cache['folder']]).returncode == 0
    screenshot('opt-17-cache-limit.png')
    report.append({'check': 'max-cache-task-eviction', 'passed': True, 'deleted_folder': first_cache['folder'], 'retained_folder': second_cache['folder']})

def select_checkbox(filename):
    import xml.etree.ElementTree as ET
    import re
    adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml')
    tree = ET.fromstring(adb('shell', 'cat', '/sdcard/window.xml'))
    rows = [n for n in tree.iter('node') if filename in n.get('text', '')]
    assert rows
    def bounds(n): return list(map(int, re.findall(r'\d+', n.get('bounds'))))
    row = bounds(rows[0])
    controls = [n for n in tree.iter('node') if n.get('checkable') == 'true' and abs(bounds(n)[1] - row[1]) < 100]
    assert controls
    n = min(controls, key=lambda n: abs(bounds(n)[1] - row[1]))
    x1,y1,x2,y2 = bounds(n)
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)); time.sleep(1)

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        (OUT / 'optimization-logcat.txt').write_text(adb('shell', 'logcat', '-d', '-t', '5000'), encoding='utf-8')
        screenshot('opt-failure.png')
        report.append({'check': 'failure', 'passed': False, 'error': str(error), 'state': diagnostic()})
        raise
    finally:
        (OUT / 'optimization-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
