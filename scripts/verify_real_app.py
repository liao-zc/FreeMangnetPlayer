import hashlib
import json
import os
import shlex
import subprocess
import time
from pathlib import Path
from capture_usage_screenshots import adb, screenshot, require_visible, find_and_tap, PACKAGE, OUT

from magnet_hash import canonical_hash

report = []


def launch(query=None, engine=None, magnet=None, mode="parse"):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "run-as", PACKAGE, "rm", "-f", "files/torrent-check.json", "files/search-check.json")
    args = ["am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity"]
    if query:
        args += ["--es", "test-query", shlex.quote(query), "--es", "test-engine", shlex.quote(engine)]
    if magnet:
        args += ["-a", "android.intent.action.VIEW", "-d", shlex.quote(magnet), "--es", "mode", mode]
    adb("shell", *args)
    time.sleep(5)


def diagnostic(name):
    try:
        return json.loads(adb("shell", "run-as", PACKAGE, "cat", f"files/{name}-check.json"))
    except Exception:
        return {}


def wait_for(predicate, seconds=90):
    deadline = time.time() + seconds
    while time.time() < deadline:
        state = diagnostic("torrent")
        if predicate(state):
            return state
        time.sleep(2)
    raise RuntimeError("Real torrent check failed: " + json.dumps(diagnostic("torrent"), ensure_ascii=False))


def main():
    adb("wait-for-device")
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    adb("shell", "wm", "size", "1080x2400")
    if os.environ.get("FMP_TEST_MODE") == "user-resources":
        verify_user_resources()
        return
    if os.environ.get("FMP_TEST_MODE") == "titles":
        launch("哪吒", "clg.im")
        time.sleep(42)
        state = diagnostic("search")
        rows = state.get("results", [])
        if not rows or any(r.get("title", "").lower().startswith("magnet:") for r in rows):
            raise RuntimeError("Search must show resource titles rather than magnet URIs")
        screenshot("24-search-resource-titles.png")
        report.append({"check": "search-resource-titles", "passed": True, "state": state})
        return
    if os.environ.get("FMP_TEST_MODE") == "engines":
        verify_engines()
        return
    launch()
    require_visible("粘贴磁力链接", "解析资源", "播放", "下载", "收藏")
    screenshot("11-fixed-home.png")

    # Exercise an actual response containing no resources through custom engine.
    prefs = '<map><string name="custom-name">Empty fixture</string><string name="custom-url">http://10.0.2.2:8888/empty?q=%s</string><string name="search-name">Empty fixture</string></map>'
    subprocess.run(["adb", "shell", "run-as", PACKAGE, "mkdir", "-p", "shared_prefs"], check=True)
    subprocess.run(["adb", "shell", "run-as", PACKAGE, "sh", "-c", shlex.quote("cat > shared_prefs/player-settings.xml")], input=prefs.encode(), check=True)
    launch("no-such-resource", "Empty fixture")
    require_visible("搜索结果", "返回")
    time.sleep(41)
    require_visible("搜索结果", "返回首页", "重试搜索")
    screenshot("12-empty-search-return.png")
    find_and_tap("返回首页")
    require_visible("粘贴磁力链接", "解析资源")
    screenshot("13-returned-home.png")
    report.append({"check": "empty-search-return", "passed": True})

    fixture = json.loads(Path("fixture.json").read_text())
    prefs = '<map><string name="custom-name">Bunny fixture</string><string name="custom-url">http://10.0.2.2:8888/search?q=%s</string><string name="search-name">Bunny fixture</string></map>'
    subprocess.run(["adb", "shell", "run-as", PACKAGE, "sh", "-c", shlex.quote("cat > shared_prefs/player-settings.xml")], input=prefs.encode(), check=True)
    launch("Bunny", "Bunny fixture")
    require_visible("Big Buck Bunny CC BY", "解析 / 播放")
    screenshot("21-real-search-result.png")
    find_and_tap("解析 / 播放")
    time.sleep(4)
    require_visible("返回搜索")
    screenshot("14-metadata-progress.png")
    state = wait_for(lambda s: s.get("metadata") and s.get("files"))
    require_visible("真实文件列表", fixture["file"])
    screenshot("15-real-file-list.png")
    find_and_tap("▶ 播放")
    streaming = wait_for(lambda s: s.get("playing") and 0 < s.get("downloaded", 0) < s.get("total", 0), 45)
    screenshot("20-real-stream-before-complete.png")
    report.append({"check": "real-stream-before-download-complete", "passed": True, "state": streaming})
    find_and_tap("下载")
    state = wait_for(lambda s: s.get("total", 0) > 0 and s.get("downloaded", 0) >= s["total"])
    target = f"/sdcard/Android/data/{PACKAGE}/files/Movies/FreeMangnetPlayer/{fixture['file']}"
    actual = adb("shell", "sha256sum", target).split()[0]
    if actual != fixture["sha256"]:
        raise RuntimeError("Downloaded video SHA256 differs from the real seed")
    screenshot("16-real-download.png")
    report.append({"check": "real-metadata-download", "passed": True, "state": state, "sha256": actual, "fixture": "Blender CC BY Big Buck Bunny clip"})
    for _ in range(3):
        find_and_tap("后退 15 秒")
    find_and_tap("收藏")
    find_and_tap("▶ 播放")
    state = wait_for(lambda s: s.get("playing"), 45)
    time.sleep(3)
    screenshot("17-real-video-playing.png")
    find_and_tap("1.0×")
    wait_for(lambda s: s.get("speed") == 1.25)
    before = diagnostic("torrent").get("position", 0)
    find_and_tap("前进 15 秒")
    state = wait_for(lambda s: s.get("position", 0) > before + 10000, 15)
    before = state["position"]
    find_and_tap("后退 15 秒")
    wait_for(lambda s: s.get("position", 0) < before - 8000, 15)
    screenshot("18-speed-seek.png")
    report.append({"check": "real-play-speed-seek", "passed": True})
    find_and_tap("返回搜索")
    find_and_tap("收藏")
    require_visible("Big Buck Bunny CC BY")
    screenshot("19-persisted-favorite.png")
    launch()
    find_and_tap("收藏")
    require_visible("Big Buck Bunny CC BY")
    report.append({"check": "favorite-cold-restart", "passed": True})

    verify_engines()

    for name, magnet in [
        ("user-hash", "magnet:?xt=urn:btih:784036B1EA2AAE7E8A89E1A87E09D77589A90B10"),
        ("screenshot-hash", "magnet:?xt=urn:btih:b1b8a1f2edd3b93fbbaacde08635bd143ebf8308")]:
        launch(magnet=magnet)
        time.sleep(90)
        state = diagnostic("torrent")
        screenshot(f"{name}-actual.png")
        report.append({"check": name, "state": state, "metadata_received": state.get("metadata", False)})
    (OUT / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


def verify_engines():
    baseline = json.loads((OUT / "browser-baseline.json").read_text())
    for i, reference in enumerate(baseline, 1):
        launch("哪吒", reference["engine"])
        time.sleep(42)
        require_visible("搜索结果", "返回")
        state = diagnostic("search")
        screenshot(f"engine-{i}-app.png")
        hashes = sorted(set(h for h in (canonical_hash(r["magnet"]) for r in state.get("results", [])) if h))
        report.append({"check": "engine-comparison", "engine": reference["engine"], "browser": reference,
                       "app": state, "app_hashes": hashes, "equal": not reference["error"] and hashes == reference["hashes"]})
        find_and_tap("返回")
        require_visible("粘贴磁力链接")


def verify_user_resources():
    for name, value in [
        ("user-hash", "magnet:?xt=urn:btih:784036B1EA2AAE7E8A89E1A87E09D77589A90B1&#x30;**"),
        ("screenshot-hash", "magnet:?xt=urn:btih:b1b8a1f2edd3b93fbbaacde08635bd143ebf8308")]:
        launch()
        find_and_tap("磁力链接输入框")
        adb("shell", "input", "text", shlex.quote(value))
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        screenshot(f"{name}-pasted.png")
        find_and_tap("解析资源")
        time.sleep(5)
        require_visible("返回搜索")
        deadline = time.time() + 180
        requested = False
        row = {"check": name, "input_path": "actual text field and parse button", "input": value}
        while time.time() < deadline:
            state = diagnostic("torrent")
            if state.get("metadata") and not requested:
                screenshot(f"{name}-files.png")
                find_and_tap("收藏")
                find_and_tap("下载")
                find_and_tap("▶ 播放")
                requested = True
            if state.get("playing"):
                find_and_tap("前进 15 秒")
                try:
                    state = wait_for(lambda s: s.get("playing") and s.get("position", 0) >= 14000, 45)
                except RuntimeError:
                    state = diagnostic("torrent")
                screenshot(f"{name}-film.png")
                break
            time.sleep(2)
        state = diagnostic("torrent")
        screenshot(f"{name}-final.png")
        row.update({"state": state, "metadata_received": state.get("metadata", False),
                    "actual_playing": state.get("playing", False), "downloaded_bytes": state.get("downloaded", 0)})
        report.append(row)
        (OUT / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        find_and_tap("返回搜索")
        if requested:
            find_and_tap("收藏")
            require_visible("磁力资源")
            screenshot(f"{name}-favorite.png")


if __name__ == "__main__":
    try:
        main()
    finally:
        (OUT / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        (OUT / "android-logcat.txt").write_text(adb("logcat", "-d"), encoding="utf-8")
