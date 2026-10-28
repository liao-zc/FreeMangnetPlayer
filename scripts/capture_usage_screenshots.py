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


def find_and_tap(label: str) -> None:
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    tree = ET.fromstring(adb("shell", "cat", "/sdcard/window.xml"))
    candidates = []
    for node in tree.iter("node"):
        values = (node.get("text", ""), node.get("content-desc", ""), node.get("hintText", ""))
        if not any(label in value for value in values):
            continue
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not match:
            continue
        x1, y1, x2, y2 = map(int, match.groups())
        if x2 > x1 and y2 > y1:
            candidates.append((any(value != label for value in values), (x2 - x1) * (y2 - y1), x1, y1, x2, y2))
    if not candidates:
        raise RuntimeError(f"Could not find UI element: {label}")
    _, _, x1, y1, x2, y2 = min(candidates)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)


def main() -> None:
    adb("wait-for-device")
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    adb("shell", "wm", "size", "1080x2400")
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(3)
    screenshot("01-home-keyword.png")

    find_and_tap("输入关键词")
    adb("shell", "input", "text", "Big%ssBuck%ssBunny")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)
    screenshot("02-keyword-entered.png")

    find_and_tap("设置")
    time.sleep(1)
    screenshot("03-engine-settings.png")
    find_and_tap("完成")
    find_and_tap("搜索")
    time.sleep(10)
    screenshot("04-in-app-results.png")

    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(3)
    screenshot("05-torrent-progress.png")


if __name__ == "__main__":
    main()