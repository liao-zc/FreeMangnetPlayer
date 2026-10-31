# OmniPlayer

OmniPlayer is an Android media app for IPTV live channels and magnet-link search, playback, caching, and downloads.

## Current development state

- Current release: **v0.3.0** ([GitHub Release](https://github.com/liao-zc/OmniPlayer/releases/tag/v0.3.0)).
- The **v0.3.0** release includes LibVLC embedded audio/subtitle selection and same-folder SRT/VTT/ASS/SSA subtitle streams. Android APK CI passed (run [41149346604](https://github.com/liao-zc/OmniPlayer/actions/runs/41149346604)); device-level track switching is unverified. The full public magnet playback retest failed 0/10 first frames; details are in the dated report below.
- Android package ID remains `com.freemangnetplayer` so signed upgrades can retain the existing app's private settings and data.
- Supported playback depends on container/codec support in Android Media3, device decoders, or LibVLC. No Android player can guarantee every codec and profile on every device.

## Update behavior

The app can check the latest public GitHub Release at startup when automatic checks are enabled, and can check manually in Settings. An available APK downloads without leaving the current screen. Android then opens its package installer; the user must confirm installation because ordinary apps cannot silently replace themselves. A package-replaced receiver attempts to reopen the app. SharedPreferences and app-private cache/files survive an in-place install when the package ID and signing certificate match. Android may remove cache files under storage pressure; cache is not a backup.

## IPTV

Default playlist: `https://testingcf.jsdelivr.net/gh/liao-zc/iptv-live@main/IPTV_ALL.m3u`.
Additional M3U/M3U8 sources can be configured by URL or imported from a device file. Imported playlists are copied into app-private storage. Magnet download/cache directories can be selected with the Android folder picker. Android 11 and later may ask the user to grant all-files access so the native BT engine can write to that path. Channel playback and PiP depend on the stream and device.

## Verification

The complete, itemized functional report, including unresolved playback cases and prior test evidence, is in [docs/report.md](docs/report.md). The v0.3.0 APK is published. Full APK playback acceptance is complete but **failed: 0/10 supplied public magnets rendered a first frame within 120 seconds**. The fixed magnet received 0 payload bytes; the Spider-Man test received 4.8 MB but verified none of its startup pieces. See the [dated playback report](docs/report.md#v030-full-public-playback-retest-2026-10-31) and [run 41148360586](https://github.com/liao-zc/OmniPlayer/actions/runs/41148360586).

## Download

- [Universal APK](https://github.com/liao-zc/OmniPlayer/releases/download/v0.3.0/OmniPlayer-v0.3.0.apk)
- [ARM64 APK](https://github.com/liao-zc/OmniPlayer/releases/download/v0.3.0/OmniPlayer-v0.3.0-arm64-v8a.apk)
- [ARMv7 APK](https://github.com/liao-zc/OmniPlayer/releases/download/v0.3.0/OmniPlayer-v0.3.0-armeabi-v7a.apk)
- [OmniPlayer source archive](https://github.com/liao-zc/OmniPlayer/releases/download/v0.3.0/OmniPlayer-v0.3.0-source.zip)