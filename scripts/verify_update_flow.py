#!/usr/bin/env python3
"""Static regression checks for the durable Android update/install flow."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
updater = (ROOT / "app/src/main/java/com/omniplayer/core/AppUpdater.kt").read_text(encoding="utf-8")
service = (ROOT / "app/src/main/java/com/omniplayer/core/UpdateTransferService.kt").read_text(encoding="utf-8")
settings = (ROOT / "app/src/main/java/com/omniplayer/settings/SettingsActivity.kt").read_text(encoding="utf-8")
main = (ROOT / "app/src/main/java/com/omniplayer/MainActivity.kt").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/library.yml").read_text(encoding="utf-8")

checks = {
    "range resume": 'setRequestProperty("Range", "bytes=$offset-")' in service and 'target.name + ".part"' in service,
    "partial retained on pause/failure": 'update-state", "paused"' in updater and 'update-state", "failed"' in service and "part.delete()" in service,
    "pause and resume controls": 'Text("暂停更新")' in settings and 'Text("继续更新")' in settings,
    "install confirmation path": 'pending-update-apk' in updater and 'installerIntent' in updater and '点击打开系统安装确认' in service,
    "unknown-source permission handoff": 'ACTION_MANAGE_UNKNOWN_APP_SOURCES' in updater and 'PendingUpdateInstaller.resume(this)' in settings,
    "completed package reused": 'pending-update-apk' in updater and 'if (apk.isFile && apk.length() > 0L' in updater,
    "update notes shown": 'release.notes' in settings and 'update.notes' in main,
    "no version code/source text in update info": 'versionCode $version' not in settings and '版本号来自' not in settings and 'updateStatus = "正在检查 GitHub' not in settings,
    "foreground service declared": '.core.UpdateTransferService' in manifest and 'FOREGROUND_SERVICE_DATA_SYNC' in manifest,
    "release notes supplied": '--notes "OmniPlayer' in workflow,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'} {name}")
if failed:
    raise SystemExit(f"Update flow checks failed: {', '.join(failed)}")
print(f"Update flow checks passed: {len(checks)}/{len(checks)}")
