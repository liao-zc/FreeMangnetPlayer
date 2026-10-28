import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path("manual-shots")
OUT.mkdir(exist_ok=True)
PACKAGE = "com.freemangnetplayer"


def adb(*args: str) -> str:
    return subprocess.check_output(["adb", *args], text=True, encoding="utf-8", errors="replace")


def screenshot(name: str) -> None:
    with (OUT / name).open("wb") as image:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=image, check=True)
    print(f"Captured {OUT / name}")


def visible_text() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    xml = adb("shell", "cat", "/sdcard/window.xml")
    tree = ET.fromstring(xml)
    return " ".join(node.get("text", "") + " " + node.get("content-desc", "") for node in tree.iter("node"))


def require_visible(*labels: str) -> None:
    visible = visible_text()
    missing = [label for label in labels if label not in visible]
    if missing:
        raise RuntimeError(f"Expected visible app controls {missing}; UI dump was: {visible}")


def find_and_tap(label: str) -> None:
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    xml = adb("shell", "cat", "/sdcard/window.xml")
    tree = ET.fromstring(xml)
    candidates = []
    for node in tree.iter("node"):
        values = (node.get("text", ""), node.get("content-desc", ""), node.get("hintText", ""))
        if not any(label in value for value in values):
            continue
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            if x2 > x1 and y2 > y1:
                candidates.append((not any(value == label for value in values), (x2 - x1) * (y2 - y1), x1, y1, x2, y2))
    if not candidates:
        raise RuntimeError(f"Could not find UI element: {label}; visible text: {visible_text()}")
    _, _, x1, y1, x2, y2 = min(candidates)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)


def main() -> None:
    adb("wait-for-device")
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    adb("shell", "wm", "size", "1080x2400")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(7)
    require_visible("输入影片或资源名称", "粘贴磁力链接", "解析资源", "搜索", "播放", "下载", "收藏")
    screenshot("01-home-keyword.png")

    find_and_tap("输入影片或资源名称")
    adb("shell", "input", "text", "Big%ssBuck%ssBunny")
    time.sleep(2)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
    screenshot("02-keyword-entered.png")

    find_and_tap("选择搜索引擎")
    time.sleep(1)
    require_visible("clg.im", "BT搜索（dobt）", "BD电影首发站", "Pomo", "美剧粉", "高清族", "自定义搜索引擎")
    screenshot("03-engine-settings.png")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    find_and_tap("执行关键词搜索")
    time.sleep(3)
    require_visible("搜索结果", "返回")
    screenshot("04-in-app-results.png")
    time.sleep(26)
    require_visible("搜索结果")
    screenshot("07-search-no-result.png")
    find_and_tap("返回首页")

    find_and_tap("播放")
    require_visible("播放列表")
    screenshot("08-playback-list.png")
    find_and_tap("下载")
    require_visible("下载任务")
    screenshot("09-download-list.png")
    find_and_tap("收藏")
    require_visible("我的收藏")
    screenshot("10-favorites-list.png")
    find_and_tap("搜索")

    find_and_tap("magnet 链接")
    adb("shell", "input", "text", "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567")
    time.sleep(2)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)
    screenshot("05-magnet-pasted.png")
    find_and_tap("解析资源")
    time.sleep(5)
    require_visible("正在解析磁力资源", "正在连接种子")
    screenshot("06-torrent-progress.png")


if __name__ == "__main__":
    main()