from pathlib import Path
p=Path('app/src/main/java/com/freemangnetplayer/VlcTorrentPlayer.kt');s=p.read_text(encoding='utf-8')
s=s.replace('    private var pendingSeek: Long? = null','    private var pendingSeek: Long? = null\n    private var playingEventAt = 0L')
s=s.replace('if (!released) { invalidateState(); main.postDelayed(this, 250) }','''if (!released) {
            if (ready && wanted && playingEventAt > 0 && android.os.SystemClock.elapsedRealtime() - playingEventAt > 10000 &&
                decoder.videoTracksCount <= 0 && decoder.audioTracksCount <= 0) {
                error = PlaybackException("文件没有可解码的音视频轨道", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
            }
            invalidateState(); main.postDelayed(this, 250)
        }''',1)
s=s.replace('MediaPlayer.Event.Playing -> { ready = true;', 'MediaPlayer.Event.Playing -> { playingEventAt = android.os.SystemClock.elapsedRealtime(); ready = true;',1)
s=s.replace('MediaPlayer.Event.EndReached -> ended = true','''MediaPlayer.Event.EndReached -> {
                        if (decoder.videoTracksCount <= 0 && decoder.audioTracksCount <= 0) error = PlaybackException("文件没有可解码的音视频轨道", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
                        else ended = true
                    }''',1)
p.write_text(s,encoding='utf-8')
