package com.freemangnetplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.media3.common.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/** Media3 control-compatible software decoder for RealMedia containers. */
internal class VlcTorrentPlayer(context: Context, task: TorrentTask, file: TaskFile) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val stream = TorrentHttpStream(task, file)
    private val library = LibVLC(context.applicationContext, arrayListOf("--network-caching=300", "--file-caching=300", "--no-video-title-show"))
    private val decoder = MediaPlayer(library)
    private val main = Handler(Looper.getMainLooper())
    private val item = MediaItem.fromUri(stream.uri)
    private var wanted = false
    private var ready = false
    private var ended = false
    private var released = false
    private var speed = 1f
    private var error: PlaybackException? = null
    private var videoSize = VideoSize.UNKNOWN
    private var rendered = false
    private var output: Any? = null
    private var pendingSeek: Long? = null
    private val refresh = object : Runnable {
        override fun run() { if (!released) { invalidateState(); main.postDelayed(this, 250) } }
    }
    init {
        val media = Media(library, stream.uri)
        media.setHWDecoderEnabled(false, false)
        decoder.media = media; media.release()
        decoder.setEventListener { event -> main.post {
            if (!released) {
                when (event.type) {
                    MediaPlayer.Event.Playing -> { ready = true; ended = false; decoder.setRate(speed); pendingSeek?.let { decoder.setTime(it); pendingSeek = null } }
                    MediaPlayer.Event.Buffering -> ready = event.buffering >= 100f
                    MediaPlayer.Event.EndReached -> ended = true
                    MediaPlayer.Event.EncounteredError -> error = PlaybackException("兼容解码器无法读取视频流", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
                }
                invalidateState()
            }
        } }
        main.post(refresh)
    }
    override fun getState(): State {
        if (released) return State.Builder().build()
        val duration = decoder.length
        val data = MediaItemData.Builder(item.mediaId.ifEmpty { stream.uri.toString() }).setMediaItem(item)
            .setDurationUs(if (duration > 0) duration * 1000 else C.TIME_UNSET).setIsSeekable(true).build()
        val commands = Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SET_SPEED_AND_PITCH, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA, Player.COMMAND_GET_TRACKS, Player.COMMAND_SET_VIDEO_SURFACE, Player.COMMAND_RELEASE).build()
        return State.Builder().setAvailableCommands(commands).setPlaylist(listOf(data)).setCurrentMediaItemIndex(0)
            .setPlayWhenReady(wanted, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (error != null) Player.STATE_IDLE else if (ended) Player.STATE_ENDED else if (ready) Player.STATE_READY else Player.STATE_BUFFERING)
            .setIsLoading(!ready && error == null).setPlayerError(error).setPlaybackParameters(PlaybackParameters(speed))
            .setSeekBackIncrementMs(15000).setSeekForwardIncrementMs(15000).setVideoSize(videoSize)
            .setNewlyRenderedFirstFrame(rendered).setContentPositionMs(decoder.time.coerceAtLeast(0))
            .setContentBufferedPositionMs { decoder.time.coerceAtLeast(0) }.build()
    }
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        wanted = playWhenReady
        if (wanted) decoder.play() else decoder.pause()
        return Futures.immediateVoidFuture()
    }
    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleStop(): ListenableFuture<*> { wanted = false; decoder.stop(); return Futures.immediateVoidFuture() }
    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        speed = playbackParameters.speed; decoder.setRate(speed); return Futures.immediateVoidFuture()
    }
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (decoder.length <= 0) pendingSeek = positionMs.coerceAtLeast(0) else decoder.setTime(positionMs.coerceAtLeast(0)); ended = false; return Futures.immediateVoidFuture()
    }
    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> {
        if (output === videoOutput) return Futures.immediateVoidFuture()
        decoder.vlcVout.detachViews(); output = videoOutput
        if (videoOutput is SurfaceView) {
            decoder.vlcVout.setVideoView(videoOutput)
            videoOutput.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                if (!released && output === videoOutput) decoder.vlcVout.setWindowSize((right-left).coerceAtLeast(1), (bottom-top).coerceAtLeast(1))
            }
            decoder.vlcVout.attachViews { _, width, height, _, _, sarNum, sarDen ->
                main.post { if (!released) { videoSize = VideoSize(width, height, 0, if (sarDen > 0) sarNum.toFloat()/sarDen else 1f); rendered = true; invalidateState() } }
            }
            decoder.vlcVout.setWindowSize(videoOutput.width.coerceAtLeast(1), videoOutput.height.coerceAtLeast(1))
        }
        return Futures.immediateVoidFuture()
    }
    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> {
        if (videoOutput == null || output === videoOutput) { decoder.vlcVout.detachViews(); output = null }
        return Futures.immediateVoidFuture()
    }
    override fun handleRelease(): ListenableFuture<*> {
        released = true; wanted = false; main.removeCallbacks(refresh)
        stream.close(); decoder.setEventListener(null); decoder.vlcVout.detachViews(); decoder.stop(); decoder.release(); library.release()
        return Futures.immediateVoidFuture()
    }
}
