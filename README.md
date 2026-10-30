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

The v0.2.8 work in progress includes one-row result pagination, a single sort menu (match/date/heat), IPTV watch history with multi-delete, and grouped live channel switching. The Android build passes and the latest controlled search-pagination regression passes ([run 41097603494](https://github.com/liao-zc/OmniPlayer/actions/runs/41097603494), with current emulator screenshots in docs/screenshots/current). The exact Spider-Man 4K MKV remains unverified for playback. A current cold run received 11.9 MB and verified piece 0 but rendered no frame; logcat revealed that a session-level CACHE_FLUSHED notification was being dropped by an invalid torrent-handle filter. The listener order is fixed in commit e3a886d. The post-fix build and same-magnet run (41100286464, 41100291676) have no runner step updates yet, so they are not counted as built or passed. The player now exposes file/cache/peer progress while waiting, prioritizes the header piece first, then permits random-access requests for the MKV index, and keeps hardware decoding active while data is pending. The full APK cold-start suite completed with 0/10 user magnets starting (the exact magnet also had no frame before the cache-flush fix); per-resource data is in the report. A same-magnet retest with the fix is running at [GitHub Actions](https://github.com/liao-zc/OmniPlayer/actions/runs/41100291676). No release is declared until video frames and sustained playback pass. Magnet URIs do not encode seed dates; the UI uses the search source publication date when available. Per-item results are in the [verification report](docs/report.md).
