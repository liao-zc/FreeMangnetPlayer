# OmniPlayer

OmniPlayer is an Android IPTV and magnet media player. The latest published APK is [v0.3.0](https://github.com/liao-zc/OmniPlayer/releases/tag/v0.3.0). Existing installations keep the Android application ID `com.freemangnetplayer` for in-place upgrades and app-data retention.

## Published app

- IPTV channels, configurable playlists, favorites, history, fullscreen, and picture-in-picture.
- Magnet search, torrent metadata/file selection, playback, caching, downloads, and a Media3/LibVLC player.
- General appearance and update controls in Settings.

Playback depends on codec support and on the torrent having reachable peers. The latest public-resource acceptance report recorded **0/10 first frames**; local controlled-seed tests passed 16 format fixtures. Public magnet playback is still an open issue.

## Current development

The `improve/modern-bt-engine` branch has passed Android APK compilation, the current UI walkthrough, a controlled search-pagination test (37/17/0 results across three source pages; at most 10 per local page), and an end-to-end playback run for the latest user-supplied magnet. That magnet rendered a video frame and played for 20 seconds while only about 0.2% of the video had downloaded, but first-frame startup took about 107 seconds and speed varied around 175–236 KB/s. See [playback run 41158983146](https://github.com/liao-zc/OmniPlayer/actions/runs/41158983146) and the [full checklist and evidence](docs/report.md). These development changes are not part of a newly published APK; the latest release remains v0.3.0.

Search metadata (size, format, date) is shown when supplied by the source or torrent metadata; unavailable fields are marked `—` rather than fabricated. The latest test magnet declares `.srt` with `so=1`, while the torrent also contains an MKV; the app selected the MKV and achieved playback in the CI emulator. This does not establish fast startup or guarantee playback for other torrents.

## Build and download

Use Android Studio with JDK 17, or build with Gradle. Download the current published APK from [GitHub Releases](https://github.com/liao-zc/OmniPlayer/releases).