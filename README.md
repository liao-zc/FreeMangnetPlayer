# OmniPlayer

OmniPlayer is an Android IPTV and magnet media player. The latest published APK is [v0.3.0](https://github.com/liao-zc/OmniPlayer/releases/tag/v0.3.0). Existing installations keep the Android application ID `com.freemangnetplayer` for in-place upgrades and app-data retention.

## Published app

- IPTV channels, configurable playlists, favorites, history, fullscreen, and picture-in-picture.
- Magnet search, torrent metadata/file selection, playback, caching, downloads, and a Media3/LibVLC player.
- General appearance and update controls in Settings.

Playback depends on codec support and on the torrent having reachable peers. The latest public-resource acceptance report recorded **0/10 first frames**; local controlled-seed tests passed 16 format fixtures. Public magnet playback is still an open issue.

## Current development

The `improve/modern-bt-engine` branch contains the latest settings, search paging/deduplication, and IPTV control changes. This work is not part of a new published APK yet. Its full checklist, historical requirement summary, and known test limits are in [docs/report.md](docs/report.md).

Search metadata (size, format, date) is shown when supplied by the source or torrent metadata; unavailable fields are marked `—` rather than fabricated. The latest magnet supplied for playback names an `.srt` subtitle file, which cannot itself produce a video frame.

## Build and download

Use Android Studio with JDK 17, or build with Gradle. Download the current published APK from [GitHub Releases](https://github.com/liao-zc/OmniPlayer/releases).