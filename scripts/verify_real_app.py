import hashlib
import json
import shlex
import subprocess
import time
from pathlib import Path
from capture_usage_screenshots import adb, screenshot, require_visible, find_and_tap, PACKAGE, OUT

report = []


def launch(query=None, engine=None, magnet=None, mode="parse"):
    adb("shell", "am", "force-stop", PACKAGE)
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
    launch(magnet=fixture["magnet"], mode="download")
    require_visible("返回搜索")
    screenshot("14-metadata-progress.png")
    state = wait_for(lambda s: s.get("metadata") and s.get("files"))
    require_visible("真实文件列表", fixture["file"])
    screenshot("15-real-file-list.png")
    state = wait_for(lambda s: s.get("total", 0) > 0 and s.get("downloaded", 0) >= s["total"])
    target = f"/sdcard/Android/data/{PACKAGE}/files/Movies/FreeMangnetPlayer/{fixture['file']}"
    actual = adb("shell", "sha256sum", target).split()[0]
    if actual != fixture["sha256"]:
        raise RuntimeError("Downloaded video SHA256 differs from the real seed")
    screenshot("16-real-download.png")
    report.append({"check": "real-metadata-download", "passed": True, "state": state, "sha256": actual, "fixture": "Blender CC BY Big Buck Bunny clip"})
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
    require_visible("磁力资源")
    screenshot("19-persisted-favorite.png")
    launch()
    find_and_tap("收藏")
    require_visible("磁力资源")
    report.append({"check": "favorite-cold-restart", "passed": True})

    baseline = json.loads((OUT / "browser-baseline.json").read_text())
    for i, reference in enumerate(baseline, 1):
        launch("哪吒", reference["engine"])
        time.sleep(42)
        require_visible("搜索结果", "返回")
        state = diagnostic("search")
        screenshot(f"engine-{i}-app.png")
        hashes = sorted(set(r["magnet"].split("btih:", 1)[1][:40].lower() for r in state.get("results", [])))
        report.append({"check": "engine-comparison", "engine": reference["engine"], "browser": reference,
                       "app": state, "app_hashes": hashes, "equal": not reference["error"] and hashes == reference["hashes"]})
        find_and_tap("返回")
        require_visible("粘贴磁力链接")

    for name, magnet in [
        ("user-hash", "magnet:?xt=urn:btih:784036B1EA2AAE7E8A89E1A87E09D77589A90B10"),
        ("screenshot-hash", "magnet:?xt=urn:btih:b1b8a1f2edd3b93fbbaacde08635bd143ebf8308")]:
        launch(magnet=magnet)
        time.sleep(90)
        state = diagnostic("torrent")
        screenshot(f"{name}-actual.png")
        report.append({"check": name, "state": state, "metadata_received": state.get("metadata", False)})
    (OUT / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    try:
        main()
    finally:
        (OUT / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        (OUT / "android-logcat.txt").write_text(adb("logcat", "-d"), encoding="utf-8")
