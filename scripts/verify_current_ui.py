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
    capture.require_visible('默认搜索引擎：BD电影首发站')
    capture.find_and_tap('编辑 / 删除搜索引擎')
    capture.require_visible('搜索引擎管理', 'clg.im', 'BT搜索（dobt）', '保存搜索引擎')
    shot('11-search-engine-management.png', ('搜索引擎管理', 'clg.im', 'BT搜索（dobt）', '保存搜索引擎'))
    capture.find_and_tap('BD电影首发站')
    capture.require_visible('BD电影首发站', '默认')
    shot('12-search-engine-default.png', ('BD电影首发站', '保存搜索引擎'))
    capture.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(2)
    capture.find_and_tap('保存磁播设置')
    capture.require_visible('磁播设置已保存')
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
    capture.find_and_tap('磁播模块设置')
    time.sleep(2)
    shot('09-magnet-entry-settings.png', ('下载目录', '缓存目录', '默认搜索引擎：BD电影首发站'))
    check_absent('显示与字体', '背景颜色', '电视直播设置', '直播源名称', '软件说明')
    capture.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(2)
    capture.find_and_tap('返回主页')
    time.sleep(2)
    capture.find_and_tap('电视直播')
    time.sleep(4)
    capture.find_and_tap('直播源设置')
    time.sleep(2)
    shot('10-live-module-settings.png', ('电视直播设置', 'IPTV_ALL', 'IPTV Org'))
    if 'https://testingcf.jsdelivr.net/gh/liao-zc/iptv-live@main/IPTV_ALL.m3u' not in capture.visible_text():
        raise RuntimeError('The IPTV source editor opened from live TV does not show the shared default URL')
    capture.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    capture.adb('shell', 'am', 'start', '-W', '-n', f'{capture.PACKAGE}/.MainActivity', '--es', 'test-query', 'OmniNavigationCheck')
    time.sleep(3)
    capture.require_visible('搜索结果', '返回搜索')
    capture.find_and_tap('返回搜索')
    capture.require_visible('搜索关键词', '输入影片或资源名称')
    shot('13-search-toolbar-returns-to-keyword.png', ('搜索关键词', '输入影片或资源名称'))
    capture.find_and_tap('执行关键词搜索')
    time.sleep(3)
    capture.require_visible('搜索结果')
    capture.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(1)
    capture.require_visible('搜索关键词', '输入影片或资源名称')
    shot('14-search-system-back-returns-to-keyword.png', ('搜索关键词', '输入影片或资源名称'))
    print('Current UI verification passed: home navigation, isolated settings pages, persisted magnet preferences across entry points, independent software info, shared live source editor, first-run IPTV source URLs, searchable engine manager, and toolbar/system back from results to keyword search.')

if __name__ == '__main__':
    main()