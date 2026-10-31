# OmniPlayer

OmniPlayer is an Android media app for IPTV live channels and magnet-link search, playback, caching, and downloads.

## Current development state

- Current release: **v0.2.7**.
- The current development version is **v0.3.0**. LibVLC-backed playback now exposes embedded audio and subtitle track selection alongside the Media3 chooser. This change is pending Android CI and device verification; it is not a published release.
- Android package ID remains `com.freemangnetplayer` so signed upgrades can retain the existing app's private settings and data.
- Supported playback depends on container/codec support in Android Media3, device decoders, or LibVLC. No Android player can guarantee every codec and profile on every device.

## Update behavior

The app can check the latest public GitHub Release at startup when automatic checks are enabled, and can check manually in Settings. An available APK downloads without leaving the current screen. Android then opens its package installer; the user must confirm installation because ordinary apps cannot silently replace themselves. A package-replaced receiver attempts to reopen the app. SharedPreferences and app-private cache/files survive an in-place install when the package ID and signing certificate match. Android may remove cache files under storage pressure; cache is not a backup.

## IPTV

Default playlist: `https://testingcf.jsdelivr.net/gh/liao-zc/iptv-live@main/IPTV_ALL.m3u`.
Additional M3U/M3U8 sources can be configured by URL or imported from a device file. Imported playlists are copied into app-private storage. Magnet download/cache directories can be selected with the Android folder picker. Android 11 and later may ask the user to grant all-files access so the native BT engine can write to that path. Channel playback and PiP depend on the stream and device.

## Verification

The complete, itemized functional report, including unresolved playback cases and prior test evidence, is in [docs/report.md](docs/report.md). The v0.3.0 changes remain implementation-only until CI and device verification are available. Do not treat the historic v0.2.7 APK as containing these unreleased changes.

## Download

See [GitHub Releases](https://github.com/liao-zc/OmniPlayer/releases) for published APKs. A v0.3.0 APK will be linked here only after the build succeeds and the release is created.