from pathlib import Path

root = Path(__file__).resolve().parents[1]
components = {
    "core": ["UpdateTransferService", "PackageReplacedReceiver"],
    "magnet": ["DownloadService", "SearchEngineSettingsActivity", "PlayerActivity"],
    "live": ["IptvActivity", "IptvSettingsActivity", "IptvPlayerActivity"],
}
for module, classes in components.items():
    manifest = (root / module / "src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    sources = list((root / module / "src/main/java").rglob("*.kt"))
    for simple_name in classes:
        if not any(simple_name in path.read_text(encoding="utf-8") for path in sources):
            raise SystemExit(f"{module} component source missing: {simple_name}")
        full_name = f"com.omniplayer.{module}.{simple_name}"
        if module == "core" and simple_name == "UpdateTransferService":
            full_name = f"com.omniplayer.core.{simple_name}"
        if module == "core" and simple_name == "PackageReplacedReceiver":
            full_name = f"com.omniplayer.core.{simple_name}"
        if f'android:name="{full_name}"' not in manifest:
            raise SystemExit(f"{module} manifest is missing {full_name}")
print("Checked feature-owned manifests and Android component sources")

settings = (root / "settings.gradle.kts").read_text(encoding="utf-8")
app_gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
for module in ("core", "magnet", "live"):
    if f'":{module}"' not in settings:
        raise SystemExit(f"Gradle settings do not include :{module}")
    if f'implementation(project(":{module}"))' not in app_gradle:
        raise SystemExit(f"app shell does not depend on :{module}")

legacy = root / "app/src/main/java/com/omniplayer"
for package in ("core", "magnet", "live"):
    path = legacy / package
    if path.exists() and any(path.rglob("*.kt")):
        raise SystemExit(f"Legacy {package} sources remain in the app shell")

feature_roots = {name: root / name / "src/main/java" / "com/omniplayer" / name for name in ("core", "magnet", "live")}
imports = {name: "\n".join(p.read_text(encoding="utf-8") for p in path.rglob("*.kt")) for name, path in feature_roots.items()}
for module, forbidden in (("core", ("com.omniplayer.magnet", "com.omniplayer.live")), ("magnet", ("com.omniplayer.live",)), ("live", ("com.omniplayer.magnet",))):
    for dependency in forbidden:
        if dependency in imports[module]:
            raise SystemExit(f"Forbidden feature dependency: :{module} imports {dependency}")

for module, sections in {"core": ("preferences", "media", "update", "navigation"), "magnet": ("search", "engine", "player", "library", "settings"), "live": ("source", "channels", "player", "settings")}.items():
    for section in sections:
        if not (root / module / "src/main/java/com/omniplayer" / module / section).is_dir():
            raise SystemExit(f"Missing {module}/{section} responsibility folder")
print("Checked app composition, Gradle library modules, source relocation, responsibility folders, and acyclic feature boundaries")
for module in ("core", "magnet", "live"):
    gradle_file = (root / module / "build.gradle.kts").read_text(encoding="utf-8")
    if 'id("com.android.library")' not in gradle_file:
        raise SystemExit(f":{module} is not configured as an Android library")
inspector_gradle = (root / "ui-inspector/build.gradle.kts").read_text(encoding="utf-8")
if 'implementation(project(":app"))' in inspector_gradle:
    raise SystemExit(":ui-inspector must remain an independent regression app")