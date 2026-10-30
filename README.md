# OmniPlayer

OmniPlayer is an Android app for IPTV live TV and magnet-link search, playback, downloads, cache, and favorites. Current release: **v0.2.7**; **v0.2.8** is under verification and is not yet released.

- Android display name: **OmniPlayer**
- GitHub repository: [liao-zc/OmniPlayer](https://github.com/liao-zc/OmniPlayer)
- Android namespace and app code use `com.omniplayer`. The install ID remains `com.freemangnetplayer` so existing installations can upgrade in place and retain private app data; it is not the visible app or file name.
- Chinese usage guide: [docs/usage-zh.md](docs/usage-zh.md)
- Verification report: [docs/report.md](docs/report.md)
- [Download the OmniPlayer v0.2.7 Markdown point-check report](https://github.com/liao-zc/OmniPlayer/releases/download/v0.2.7/OmniPlayer-v0.2.7-report.md)
- Third-party notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- [OmniPlayer v0.2.7 ARM64 APK](https://github.com/liao-zc/OmniPlayer/releases/download/v0.2.7/OmniPlayer-v0.2.7-arm64-v8a.apk) · [ARMv7 APK](https://github.com/liao-zc/OmniPlayer/releases/download/v0.2.7/OmniPlayer-v0.2.7-armeabi-v7a.apk) · [Universal APK](https://github.com/liao-zc/OmniPlayer/releases/download/v0.2.7/OmniPlayer-v0.2.7.apk)
- [Release files and OmniPlayer-named source archive](https://github.com/liao-zc/OmniPlayer/releases/tag/v0.2.7)

The default IPTV playlist is `https://testingcf.jsdelivr.net/gh/liao-zc/iptv-live@main/IPTV_ALL.m3u`. `https://iptv-org.github.io/iptv/index.m3u` is included as an alternate source. Playback availability depends on each source, network, Android version, and device codecs.

The v0.2.8 work in progress adds one-row previous/next page controls, a single sort menu for match/date/heat, IPTV watch history with multi-delete, and a grouped channel list below the live player. A fallback decoder is attempted when an MKV fails to render its first frame on the device decoder. Three cold starts of the exact Spider-Man magnet received about 55 MB from two seeds but rendered no frame: piece 0 at the file header was still unverified after two minutes despite the local HTTP range request and highest piece priority. The active-file sequential download range is now being tested. v0.2.8 is not released. v0.2.8 is not released. Magnet creation/seed time is not encoded in the URI; results can show only the search source publication date. Details and item-by-item verification status are in the [current report](docs/report.md).
