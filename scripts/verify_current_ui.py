from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import capture_usage_screenshots as capture

OUT = Path('manual-shots')
OUT.mkdir(exist_ok=True)

def check_absent(*labels):
    text = capture.visible_text()
    found = [label for label in labels if label in text]
    if found:
        raise RuntimeError(f'Unexpected controls visible: {found}; UI: {text}')


def shot(name, required=()):
    if required:
        capture.require_visible(*required)
    capture.screenshot(name)


def main():
    capture.adb('wait-for-device')
    capture.adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
    capture.adb('shell', 'wm', 'size', '1080x2400')
    capture.adb('shell', 'pm', 'clear', capture.PACKAGE)
    capture.adb('shell', 'am', 'start', '-W', '-n', f'{capture.PACKAGE}/.MainActivity')
    import time
    time.sleep(7)
    shot('01-home.png', ('电视直播', '磁力播放', '统一设置'))
    capture.find_and_tap('统一设置')
    shot('03-settings.png', ('直播设置', '磁播设置', '通用设置', '软件说明'))
    capture.find_and_tap('磁播设置')
    shot('04-magnet-settings.png', ('下载目录', '缓存目录', '搜索引擎'))
    check_absent('字体大小', '背景颜色', '直播源名称', '软件说明')
    capture.find_and_tap('返回设置分类')
    capture.find_and_tap('通用设置')
    shot('05-general-settings.png', ('显示与字体', '背景颜色'))
    check_absent('下载目录', '缓存目录', '搜索引擎', '直播源名称', '软件信息', '版本：')
    capture.find_and_tap('返回设置分类')
    capture.find_and_tap('软件说明')
    shot('06-about.png', ('OmniPlayer', '版本：', '最近更新：'))
    capture.find_and_tap('返回设置分类')
    capture.find_and_tap('直播设置')
    shot('08-live-source-settings.png', ('电视直播设置', 'IPTV_ALL', 'IPTV Org'))
    text = capture.visible_text()
    if 'https://testingcf.jsdelivr.net/gh/liao-zc/iptv-live@main/IPTV_ALL.m3u' not in text:
        raise RuntimeError('The configured IPTV_ALL default URL is missing from the fresh-install source editor: ' + text)
    if 'https://iptv-org.github.io/iptv/index.m3u' not in text:
        raise RuntimeError('The IPTV Org alternate URL is missing from the source editor: ' + text)
    capture.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(2)
    capture.find_and_tap('首页')
    time.sleep(2)
    capture.find_and_tap('电视直播')
    time.sleep(5)
    shot('07-live-channels.png', ('电视直播', '直播源：IPTV_ALL'))
    capture.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(2)
    capture.find_and_tap('磁力播放')
    time.sleep(3)
    shot('02-magnet.png', ('搜索', '播放', '下载', '收藏'))
    capture.find_and_tap('设置')
    time.sleep(2)
    shot('09-magnet-entry-settings.png', ('下载目录', '缓存目录', '搜索引擎'))
    check_absent('显示与字体', '背景颜色', '电视直播设置', '直播源名称', '软件说明')
    capture.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(2)
    if 'RETURN_TO_MENU' in capture.visible_text():
        raise RuntimeError('Unreachable marker')
    print('Current UI verification passed: home navigation, isolated settings pages, independent software info, first-run IPTV source URLs, and module settings entry.')

if __name__ == '__main__':
    main()