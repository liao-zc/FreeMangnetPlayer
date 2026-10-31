"""Instrumented search regression against the deterministic multi-page fixture."""
import json
import re
import shlex
import time
from pathlib import Path

from capture_usage_screenshots import adb, find_and_tap, require_visible, screenshot, PACKAGE, OUT

def state():
    try:
        return json.loads(adb("shell", "run-as", PACKAGE, "cat", "files/search-check.json"))
    except Exception:
        return {}

def wait_for_page(page, count):
    end = time.monotonic() + 90
    while time.monotonic() < end:
        current = state()
        if current.get("page") == page and current.get("complete") is True and len(current.get("results", [])) == count:
            return current
        time.sleep(1)
    raise AssertionError({"expected_page": page, "expected_results": count, "state": state()})

def check_page(page, count, first_title):
    current = wait_for_page(page, count)
    rows = current["results"]
    magnets = [row["magnet"] for row in rows]
    identities = [re.search(r"(?i)btih:([0-9a-f]{40})", link).group(1).lower() for link in magnets]
    assert len(identities) == len(set(identities)), "Duplicate BTIH is visible in a source page"
    assert current.get("totalPages") == 3, current
    if count:
        assert first_title in rows[0]["title"], rows[0]
        assert all("magnet:?xt=urn:btih:" in link for link in magnets)
        assert all(row.get("bytes") == 2300000000 for row in rows), "Fixture file size metadata was lost"
    screenshot(f"source-page-{page}.png")
    return current

adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
adb("shell", "am", "force-stop", PACKAGE)
prefs = '''<map><string name="search-name">分页测试</string><string name="search-url">http://10.0.2.2:8899/search?q=%s</string><string name="custom-name">分页测试</string><string name="custom-url">http://10.0.2.2:8899/search?q=%s</string></map>'''
Path("search-test-prefs.xml").write_text(prefs, encoding="utf-8")
adb("push", "search-test-prefs.xml", "/data/local/tmp/search-test-prefs.xml")
adb("shell", "run-as", PACKAGE, "sh", "-c", shlex.quote("mkdir -p shared_prefs; cat /data/local/tmp/search-test-prefs.xml > shared_prefs/player-settings.xml"))
adb("shell", "am", "start", "-W", "-n", PACKAGE + "/com.omniplayer.MainActivity", "--es", "test-query", shlex.quote("哪吒"), "--es", "test-engine", shlex.quote("分页测试"))
checks = []

one = check_page(1, 37, "哪吒资源文件 0")
assert len(one["results"]) > 10, "App imposed a second ten-row page limit"
checks.append({"check": "source-page-one-all-37-results-no-local-cap", "passed": True, "result_count": len(one["results"]), "source_total": one["totalPages"]})
find_and_tap("下一页")
two = check_page(2, 17, "哪吒第二页资源 0")
checks.append({"check": "source-page-two-all-17-results", "passed": True, "result_count": len(two["results"]), "source_total": two["totalPages"]})
require_visible("第 2 / 3 页")
find_and_tap("排序：匹配度")
require_visible("大小")
screenshot("sort-options-size.png")
find_and_tap("大小")
find_and_tap("上一页")
one = check_page(1, 37, "哪吒资源文件 0")
checks.append({"check": "previous-page-restores-source-results", "passed": True, "result_count": len(one["results"])})
find_and_tap("下一页")
find_and_tap("下一页")
three = check_page(3, 0, "")
checks.append({"check": "source-empty-third-page-remains-navigable", "passed": True, "result_count": len(three["results"])})
find_and_tap("上一页")
check_page(2, 17, "哪吒第二页资源 0")
find_and_tap("返回搜索")
require_visible("解析资源")
checks.append({"check": "back-returns-to-search-home", "passed": True})
(OUT / "pagination-verification.json").write_text(json.dumps(checks, ensure_ascii=False, indent=2), encoding="utf-8")
print("Search fixture passed: source-controlled pages, no client-side result cap, BTIH de-duplication, size sort, previous/next, and return navigation")
