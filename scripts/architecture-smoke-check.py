from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
source = root / "app/src/main/java/com/omniplayer"
checks = {
    ".magnet.DownloadService": "magnet/DownloadService.kt",
    ".core.UpdateDownloadReceiver": "core/UpdateReceivers.kt",
    ".core.PackageReplacedReceiver": "core/UpdateReceivers.kt",
    ".settings.SettingsActivity": "settings/SettingsActivity.kt",
    ".magnet.SearchEngineSettingsActivity": "magnet/SearchEngineSettings.kt",
    ".live.IptvActivity": "live/IptvActivity.kt",
    ".live.IptvSettingsActivity": "live/IptvSettingsActivity.kt",
    ".live.IptvPlayerActivity": "live/IptvPlayerActivity.kt",
    ".magnet.PlayerActivity": "magnet/PlayerActivity.kt",
    ".MainActivity": "MainActivity.kt",
}
for component, relative in checks.items():
    if f'android:name="{component}"' not in manifest:
        raise SystemExit(f"Manifest component missing: {component}")
    file = (source / relative).resolve()
    if not file.is_file():
        raise SystemExit(f"Component source missing: {file}")
print(f"Checked {len(checks)} Android component paths")

# Domain sources may use the shared core, but feature implementations must not
# silently remain in the app shell's former flat source location.
for old_name in ["PlayerActivity.kt", "TorrentTasks.kt", "IptvActivity.kt", "SettingsActivity.kt", "SearchEngineSettings.kt"]:
    if (source / old_name).exists():
        raise SystemExit(f"Legacy flat source remains: {old_name}")
print("Checked feature sources are grouped by package")