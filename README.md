# OmniPlayer

OmniPlayer is an Android app for IPTV live channels and magnet-link search, playback, downloads, and caching. The current published release is [v0.3.0](https://github.com/liao-zc/OmniPlayer/releases/tag/v0.3.0). The Android package ID remains `com.freemangnetplayer` to support in-place upgrades and preserve app data.

## Features

- IPTV: configurable M3U/M3U8 sources by URL or imported file, channel groups, favorites, viewing history, fullscreen, and picture-in-picture.
- Magnet: configurable search engines, in-app results, BTIH de-duplication, result paging, metadata/file selection, download/cache tasks, and playback.
- Player: Media3 plus LibVLC fallback, seeking, speed, quality selection where available, audio/subtitle selection, fullscreen, and picture-in-picture.
- Settings: one home entry for live TV, magnet, general appearance, update checks, and software information. Magnet and live-TV settings remain separate.

## Limits and current verification

A magnet metadata list does not prove that a torrent has peers or playable bytes. The previous public-resource acceptance run rendered **0/10 first frames**; a controlled local seed passed playback and checksum checks for 16 formats. Public playback therefore remains an unresolved, resource-dependent limitation. See the [current functional checklist and audit](docs/report.md).

Search results show source-provided size, format, and publication date when available. A source that does not expose a field is shown as `—`; the app does not invent metadata. The magnet supplied in the latest request declares an `.srt` subtitle as its selected file, which is not itself a video stream.

## Build

Open the project in Android Studio with JDK 17, or run `gradle assembleDebug`. Current release metadata and APK downloads are listed on the [GitHub Releases page](https://github.com/liao-zc/OmniPlayer/releases).