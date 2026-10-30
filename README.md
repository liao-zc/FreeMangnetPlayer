# OmniPlayer

OmniPlayer is an Android app for IPTV live TV and magnet-link search, playback, downloads, cache, and favorites. Current release: **v0.2.7**.

- Android display name: **OmniPlayer**
- GitHub repository: [liao-zc/OmniPlayer](https://github.com/liao-zc/OmniPlayer)
- Android namespace and app code use `com.omniplayer`. The install ID remains `com.freemangnetplayer` so existing installations can upgrade in place and retain private app data; it is not the visible app or file name.
- Chinese usage guide: [docs/usage-zh.md](docs/usage-zh.md)
- Verification report: [docs/report.md](docs/report.md)
- Third-party notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- [OmniPlayer v0.2.7 ARM64 APK](https://github.com/liao-zc/OmniPlayer/releases/download/v0.2.7/OmniPlayer-v0.2.7-arm64-v8a.apk) · [ARMv7 APK](https://github.com/liao-zc/OmniPlayer/releases/download/v0.2.7/OmniPlayer-v0.2.7-armeabi-v7a.apk) · [Universal APK](https://github.com/liao-zc/OmniPlayer/releases/download/v0.2.7/OmniPlayer-v0.2.7.apk)
- [Release files and OmniPlayer-named source archive](https://github.com/liao-zc/OmniPlayer/releases/tag/v0.2.7)

The default IPTV playlist is `https://testingcf.jsdelivr.net/gh/liao-zc/iptv-live@main/IPTV_ALL.m3u`. `https://iptv-org.github.io/iptv/index.m3u` is included as an alternate source. Playback availability depends on each source, network, Android version, and device codecs.

The search engine settings support editing, deleting, and up to 10 sources, with BDFlix selected by default. If a torrent stays without usable peers or data, the app keeps a visible warning on its saved entries and excludes it from download/cache counts. Search results return to the previous keyword page and show the site's publication time when available plus the largest video file's format; other file types remain downloadable. Details and item-by-item verification status are in the [current report](docs/report.md).
