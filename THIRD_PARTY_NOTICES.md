# Third-party notices

- AndroidX, Jetpack Compose, and Android Media3: Apache License 2.0.
- simple-torrent-android: MIT License. The upstream project is marked deprecated by its maintainer.
- frostwire-jlibtorrent and bundled libtorrent JNI artifacts: MIT License.

See each linked upstream project for the full license text and transitive dependency notices:
https://github.com/masterwok/simple-torrent-android
https://github.com/frostwire/frostwire-jlibtorrent
https://github.com/androidx/androidx
https://github.com/androidx/media
## LibVLC

RealMedia (.rm/.rmvb) software decoding uses [VideoLAN LibVLC](https://code.videolan.org/videolan/vlc-android), LGPL-2.1-or-later. Maven artifact: org.videolan.android:libvlc-all:3.6.5. VLC and bundled codec sources/licensing are available from [VideoLAN](https://www.videolan.org/vlc/download-sources.html). The backend reads only verified BitTorrent pieces through an authenticated loopback range server; it does not use a third-party cloud playback service.
