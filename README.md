# OmniPlayer

OmniPlayer is an Android media app for IPTV live channels and magnet-link search, playback, caching, and downloads.

## Current development state

- Current release: **v0.2.7**.
- The unreleased v0.2.9 updater and player changes pass the GitHub Actions Android APK build (run 41106664965). No device playback or update-install test has been completed, so these changes are not a published release.
- Android package ID remains `com.freemangnetplayer` so signed upgrades can retain the existing app's private settings and data.
- Supported playback depends on container/codec support in Android Media3, device decoders, or LibVLC. No Android player can guarantee every codec and profile on every device.

## Update behavior

The app can check the latest public GitHub Release at startup when automatic checks are enabled, and can check manually in Settings. An available APK downloads without leaving the current screen. Android then opens its package installer; the user must confirm installation because ordinary apps cannot silently replace themselves. A package-replaced receiver attempts to reopen the app. SharedPreferences and app-private cache/files survive an in-place install when the package ID and signing certificate match. Android may remove cache files under storage pressure; cache is not a backup.

## IPTV

Default playlist: `https://testingcf.jsdelivr.net/gh/liao-zc/iptv-live@main/IPTV_ALL.m3u`.
Additional M3U/M3U8 sources can be configured by URL or imported from a device file. Imported playlists are copied into app-private storage. Channel playback and PiP depend on the stream and device.

## Verification

The complete, itemized functional report, including unresolved playback cases and prior test evidence, is in [docs/report.md](docs/report.md). The latest update/player changes are marked as implementation-only until CI and device verification are available. Do not treat the historic v0.2.7 APK as containing these unreleased changes.

## Download

See [GitHub Releases](https://github.com/liao-zc/OmniPlayer/releases) for published APKs. The current development changes have no new APK download yet.