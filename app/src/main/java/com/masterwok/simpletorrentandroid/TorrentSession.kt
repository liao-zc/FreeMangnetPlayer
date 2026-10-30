package com.masterwok.simpletorrentandroid

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
import com.masterwok.simpletorrentandroid.contracts.TorrentSessionListener
import com.masterwok.simpletorrentandroid.extensions.*
import com.masterwok.simpletorrentandroid.models.TorrentSessionBuffer
import com.masterwok.simpletorrentandroid.models.TorrentSessionStatus
import java.io.File
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.security.InvalidParameterException


/**
 * This class is used to control a torrent download session.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
open class TorrentSession(
        private val torrentSessionOptions: TorrentSessionOptions
) {
    companion object {
        private const val Tag = "TorrentSession"
    }

    /**
     * The provided [listener] will receive status updates during the torrent download.
     */
    var listener: TorrentSessionListener? = null

    private val sessionParams = SessionParams(torrentSessionOptions.settingsPack)
    private val alertListener = TorrentSessionAlertListener(this)

    private val sessionManager = SessionManager(torrentSessionOptions.enableLogging)

    private lateinit var torrentSessionBuffer: TorrentSessionBuffer

    private var shouldDownloadMagnetOnResume: Boolean = false
    private var bencode: ByteArray = ByteArray(0)
    private var saveLocationUri: Uri = Uri.EMPTY
    private var largestFileUri: Uri = Uri.EMPTY
    private var magnetUri: Uri = Uri.EMPTY

    init {
        sessionManager.addListener(alertListener)
    }

    private fun isTorrentPaused(torrentHandle: TorrentHandle): Boolean =
            torrentHandle.status().flags().and_(TorrentFlags.PAUSED).nonZero()
                    || sessionManager.isPaused

    private fun isValidTorrentUri(torrentUri: Uri): Boolean {
        val path = torrentUri.toString()

        return path.startsWith("magnet")
                || URLUtil.isNetworkUrl(path)
                || URLUtil.isFileUrl(path)
                || URLUtil.isContentUrl(path)
    }

    private fun createSessionStatus(torrentHandle: TorrentHandle): TorrentSessionStatus =
            TorrentSessionStatus.createInstance(
                    magnetUri
                    , torrentHandle
                    , bencode
                    , torrentSessionBuffer
                    , saveLocationUri
                    , largestFileUri
            )

    private class TorrentSessionAlertListener(
            torrentSession: TorrentSession
    ) : AlertListener {

        private val torrentSession: WeakReference<TorrentSession> = WeakReference(torrentSession)

        @Synchronized
        override fun alert(alert: Alert<*>) {
            try {
                if (alert.isTorrentAlert() && !alert.hasValidTorrentHandle()) {
                    Log.w(Tag, "Ignoring alert with invalid torrent handle: ${alert.type()}")
                    return
                }

                when (alert.type()) {
                    AlertType.DHT_BOOTSTRAP -> torrentSession.get()?.onDhtBootstrap()
                    AlertType.DHT_STATS -> torrentSession.get()?.onDhtStats()
                    AlertType.METADATA_RECEIVED -> torrentSession.get()?.onMetadataReceived(alert as MetadataReceivedAlert)
                    AlertType.METADATA_FAILED -> torrentSession.get()?.onMetadataFailed(alert as MetadataFailedAlert)
                    AlertType.PIECE_FINISHED -> torrentSession.get()?.onPieceFinished(alert as PieceFinishedAlert)
                    AlertType.TORRENT_DELETE_FAILED -> torrentSession.get()?.onTorrentDeleteFailed(alert as TorrentDeleteFailedAlert)
                    AlertType.TORRENT_DELETED -> torrentSession.get()?.onTorrentDeleted(alert as TorrentDeletedAlert)
                    AlertType.TORRENT_REMOVED -> torrentSession.get()?.onTorrentRemoved(alert as TorrentRemovedAlert)
                    AlertType.TORRENT_RESUMED -> torrentSession.get()?.onTorrentResumed(alert as TorrentResumedAlert)
                    AlertType.TORRENT_PAUSED -> torrentSession.get()?.onTorrentPaused(alert as TorrentPausedAlert)
                    AlertType.TORRENT_FINISHED -> torrentSession.get()?.onTorrentFinished(alert as TorrentFinishedAlert)
                    AlertType.TORRENT_ERROR -> torrentSession.get()?.onTorrentError(alert as TorrentErrorAlert)
                    AlertType.CACHE_FLUSHED -> torrentSession.get()?.let { it.cacheFlushGeneration++ }
                    AlertType.TRACKER_ERROR, AlertType.LISTEN_FAILED -> torrentSession.get()?.networkMessage = alert.toString()
                    AlertType.ADD_TORRENT -> torrentSession.get()?.onAddTorrent(alert as AddTorrentAlert)
                    AlertType.BLOCK_UPLOADED -> torrentSession.get()?.onBlockUploaded(alert as BlockUploadedAlert)
                    else -> if (torrentSession.get()?.torrentSessionOptions?.enableLogging == true) Log.d(Tag, "Unhandled alert: $alert")
                }
            } catch (e: Exception) {
                Log.e(Tag, "An exception occurred within torrent session callback", e)
            }
        }

        override fun types(): IntArray? = null
    }

    private fun isDhtReady() = sessionManager.stats().dhtNodes() >= torrentSessionOptions.dhtNodeMinimum

    private val dhtLock = Object()

    private fun onDhtStats() {
        synchronized(dhtLock) {
            if (isDhtReady()) {
                dhtLock.notify()
            }
        }
    }

    private fun onDhtBootstrap() {
        synchronized(dhtLock) {
            dhtLock.notify()
        }
    }

    private fun onMetadataReceived(metadataReceivedAlert: MetadataReceivedAlert) {
        val torrentHandle = metadataReceivedAlert.handle()

        setInitialTorrentState(torrentHandle)

        listener?.onMetadataReceived(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }

    private fun setInitialTorrentState(torrentHandle: TorrentHandle) {
        if (torrentHandle.torrentFile() == null) {
            return
        }

        largestFileUri = torrentHandle.getLargestFileUri(torrentSessionOptions.downloadLocation)
        saveLocationUri = Uri.fromFile(torrentSessionOptions.downloadLocation)
        bencode = torrentHandle.getBencode()
        magnetUri = Uri.parse(URLDecoder.decode(torrentHandle.makeMagnetUri(), "utf-8"))

        if (torrentSessionOptions.onlyDownloadLargestFile) {
            // Set priorities atomically. A separate "ignore every file" call
            // briefly makes the torrent finished, disconnects its seed, and
            // can trigger playback before any video bytes have arrived.
            val selected = torrentHandle.getLargestFileIndex()
            torrentHandle.prioritizeFiles(Array(torrentHandle.torrentFile().numFiles()) {
                if (it == selected) Priority.NORMAL else Priority.IGNORE
            })
        }

        val largestFileIndex = torrentHandle.getLargestFileIndex()

        torrentSessionBuffer = TorrentSessionBuffer(
                torrentSessionBuffer.bufferSize
                , torrentHandle.getStartPieceIndex(largestFileIndex)
                , torrentHandle.getEndPieceIndex(largestFileIndex)
        )

        if (torrentSessionOptions.shouldStream) {
            torrentHandle.setBufferPriorities(torrentSessionBuffer)
        }
    }

    private fun onAddTorrent(addTorrentAlert: AddTorrentAlert) {
        val torrentHandle = addTorrentAlert.handle()

        setInitialTorrentState(torrentHandle)

        reconnectKnownPeers(torrentHandle)
        listener?.onAddTorrent(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )

        addTorrentAlert
                .handle()
                .resume()
    }

    private fun onPieceFinished(pieceFinishedAlert: PieceFinishedAlert) {
        // The modern player handles its own buffering; do not log every downloaded piece.
        if (!torrentSessionOptions.shouldStream) return
        val torrentHandle = pieceFinishedAlert.handle()

        val pieceIndex = pieceFinishedAlert.pieceIndex()

        if (pieceIndex < torrentSessionBuffer.startIndex || pieceIndex > torrentSessionBuffer.endIndex) {
            Log.w(Tag, "Out of range piece downloaded.")
            return
        }

        torrentSessionBuffer.setPieceDownloaded(pieceIndex)

        if (torrentSessionOptions.shouldStream) {
            torrentHandle.setBufferPriorities(torrentSessionBuffer)
        }

        listener?.onPieceFinished(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }

    private fun onMetadataFailed(metadataFailedAlert: MetadataFailedAlert) {
        val torrentHandle = metadataFailedAlert.handle()

        listener?.onMetadataFailed(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }

    private fun onTorrentDeleteFailed(torrentDeleteFailedAlert: TorrentDeleteFailedAlert) {
        val torrentHandle = torrentDeleteFailedAlert.handle()

        listener?.onTorrentDeleteFailed(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }

    private fun onTorrentPaused(torrentPausedAlert: TorrentPausedAlert) {
        val torrentHandle = torrentPausedAlert.handle()

        // When the user pauses the session before the magnet is added, and then
        // resumes the session, an extraneous TorrentPausedAlert occurs after the
        // TorrentResumedAlert. Guard against this case as the session is not
        // really paused at this point.
        if (!isTorrentPaused(torrentHandle)) {
            return
        }

        listener?.onTorrentPaused(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }

    private fun onTorrentResumed(torrentResumedAlert: TorrentResumedAlert) {
        val torrentHandle = torrentResumedAlert.handle()

        listener?.onTorrentResumed(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }

    private fun onTorrentRemoved(torrentRemovedAlert: TorrentRemovedAlert) {
        val torrentHandle = torrentRemovedAlert.handle()

        listener?.onTorrentRemoved(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }

    private fun onTorrentDeleted(torrentDeletedAlert: TorrentDeletedAlert) {
        val torrentHandle = torrentDeletedAlert.handle()

        listener?.onTorrentDeleted(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }

    private fun onTorrentError(torrentErrorAlert: TorrentErrorAlert) {
        val torrentHandle = torrentErrorAlert.handle()

        listener?.onTorrentError(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }

    private fun onTorrentFinished(torrentFinishedAlert: TorrentFinishedAlert) {
        val torrentHandle = torrentFinishedAlert.handle()

        listener?.onTorrentFinished(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }

    private fun onBlockUploaded(blockUploadedAlert: BlockUploadedAlert) {
        val torrentHandle = blockUploadedAlert.handle()

        listener?.onBlockUploaded(
                torrentHandle
                , createSessionStatus(torrentHandle)
        )
    }


    /**
     * Download a torrent from the [torrentUrl] to the [downloadLocation] destination.
     */
    private fun downloadUsingNetworkUri(
            downloadLocation: File
            , torrentUrl: URL
    ) {
        val connection = (torrentUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 10_000
        }

        connection.connect()

        if (connection.responseCode != 200) {
            Log.w(Tag, "Unexpected response code returned from server: ${connection.responseCode}")
        }

        val data = connection
                .inputStream
                .readBytes()

        sessionManager.download(
                TorrentInfo.bdecode(data)
                , downloadLocation
        )
    }


    /**
     * Download the torrent using a content URI. The provided [context] is used
     * to resolve the content resolver.
     */
    private fun downloadUsingContentUri(
            context: Context
            , downloadLocation: File
            , torrentUri: Uri
    ) {
        val bytes = context.contentResolver.openInputStream(torrentUri)?.use { it.readBytes() }
                ?: throw java.io.IOException("Cannot open torrent URI")

        sessionManager.download(
                TorrentInfo.bdecode(bytes)
                , downloadLocation
        )
    }

    private fun downloadUsingMagnetUri(
            magnetUrl: String
            , downloadLocation: File
    ) = synchronized(dhtLock) {
        shouldDownloadMagnetOnResume = false

        // Trackers and supplied peers must start even if UDP/DHT is unavailable.
        // Native DHT announces later when routing nodes become available.

        // Session was paused while waiting on DHT, defer until resume
        if (sessionManager.isPaused) {
            shouldDownloadMagnetOnResume = true
            return
        }

        sessionManager.download(
                magnetUrl // Native parser decodes parameters; decoding the full URL corrupts trackers.
                , downloadLocation
                , org.libtorrent4j.swig.torrent_flags_t()
        )
    }

    fun resume() {
        sessionManager.resume()

        // Session was paused before DHT could become active, retry magnet download.
        if (shouldDownloadMagnetOnResume) {
            downloadUsingMagnetUri(
                    magnetUri.toString()
                    , torrentSessionOptions.downloadLocation
            )
        }
    }

    private fun setInitialStartState() {
        bencode = ByteArray(0)
        saveLocationUri = Uri.EMPTY
        largestFileUri = Uri.EMPTY

        torrentSessionBuffer = TorrentSessionBuffer(
                bufferSize = if (torrentSessionOptions.shouldStream) torrentSessionOptions.streamBufferSize else 0
        )
    }

    /**
     * Start the session using BEncoded data contained within the [bencode] [ByteArray].
     */
    fun start(bencode: ByteArray) {
        setInitialStartState()

        if (!sessionManager.isRunning) {
            sessionManager.start(sessionParams)
        }

        sessionManager.download(
                TorrentInfo.bdecode(bencode)
                , torrentSessionOptions.downloadLocation
        )
    }

    /**
     * Attempt to start a torrent download. The provided [Context] is used to resolve
     * an input stream from the content resolver when the URI is a file or content scheme.
     */
    fun start(context: Context, torrentUri: Uri) {
        setInitialStartState()

        if (!isValidTorrentUri(torrentUri)) {
            throw InvalidParameterException("Unrecognized torrent URI: $torrentUri")
        }

        val path = torrentUri.toString()

        if (!sessionManager.isRunning) {
            sessionManager.start(sessionParams)
        }

        if (path.startsWith("magnet")) {
            downloadUsingMagnetUri(
                    path
                    , torrentSessionOptions.downloadLocation
            )
        } else if (URLUtil.isNetworkUrl(path)) {
            downloadUsingNetworkUri(
                    torrentSessionOptions.downloadLocation
                    , URL(path)
            )

        } else if (URLUtil.isFileUrl(path) || URLUtil.isContentUrl(path)) {
            downloadUsingContentUri(
                    context
                    , torrentSessionOptions.downloadLocation
                    , torrentUri
            )
        }
    }

    val uploadRate: Long get() = sessionManager.uploadRate()

    val downloadRate: Long get() = sessionManager.downloadRate()

    val isPaused: Boolean get() = sessionManager.isPaused

    val isRunning: Boolean get() = sessionManager.isRunning
    fun preferTcp(handle: TorrentHandle) {
        val settings = SettingsPack()
        settings.setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.enable_outgoing_utp.swigValue(), false)
        sessionManager.applySettings(settings)
        // Applying settings alone leaves an existing stalled uTP peer alive.
        handle.pause(); handle.resume()
        handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL)
        handle.forceDHTAnnounce(); reconnectKnownPeers(handle)
    }
    fun enableOutgoingUtp() {
        val settings = SettingsPack()
        settings.setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)
        sessionManager.applySettings(settings)
    }
    val dhtNodes: Long get() = sessionManager.dhtNodes()
    val actualDownloadLimit: Int get() = sessionManager.downloadRateLimit()
    val actualUploadLimit: Int get() = sessionManager.uploadRateLimit()
    @Volatile var networkMessage: String = ""
    @Volatile var cacheFlushGeneration: Long = 0

    @Volatile var metadataReplacements = 0
    @Volatile var restoredTrackers = 0
    private val knownPeers = java.util.concurrent.ConcurrentHashMap<String, TcpEndpoint>()
    fun reconnectKnownPeers(handle: TorrentHandle) {
        knownPeers.values.take(100).forEach { endpoint -> runCatching { handle.swig().connect_peer(endpoint.swig()) } }
    }
    fun discoverPeers(handle: TorrentHandle) {
        val hash = handle.infoHash()
        sessionManager.dhtGetPeers(hash, 5)?.forEach { endpoint -> knownPeers[endpoint.toString()] = endpoint }
        if (handle.isValid) reconnectKnownPeers(handle)
    }
    /** HTTP metadata is a parallel fallback, never a prerequisite for P2P startup. */
    fun supplyMetadata(handle: TorrentHandle, bytes: ByteArray) {
        if (!handle.isValid || handle.torrentFile() != null) return
        val info = TorrentInfo.bdecode(bytes)
        if (info.infoHash() != handle.infoHash()) return
        // 1.2 bindings cannot set metadata in place. Preserve tracker URLs/tiers
        // and connected endpoints BEFORE replacing the owning handle.
        val oldTrackers = handle.trackers()
        oldTrackers.forEach { info.addTracker(it.url(), it.tier()) }
        handle.peerInfo().forEach { peer -> runCatching {
            val endpoint = peer.ip()
            knownPeers[endpoint] = TcpEndpoint(endpoint.substringBeforeLast(':').removePrefix("[").removeSuffix("]"), endpoint.substringAfterLast(':').toInt())
        } }
        restoredTrackers = oldTrackers.size
        metadataReplacements++
        val hash = info.infoHash()
        sessionManager.remove(handle)
        val deadline = android.os.SystemClock.elapsedRealtime() + 3000
        while (sessionManager.find(hash)?.isValid == true && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
        check(sessionManager.find(hash)?.isValid != true) { "Previous metadata task is still being removed" }
        sessionManager.download(info, torrentSessionOptions.downloadLocation, null, null, knownPeers.values.toList(), org.libtorrent4j.swig.torrent_flags_t())
    }

    /**
     * Alert handles borrow storage from the native alert and must not be kept
     * after the callback. find() returns an owning copy suitable for polling
     * and the media data source. Keeping an alert handle causes a JNI crash.
     */
    fun retainHandle(handle: TorrentHandle): TorrentHandle? = sessionManager.find(handle.infoHash())

    fun pause() = sessionManager.pause()

    fun stop() {
        sessionManager.stop()

        sessionManager.removeListener(alertListener)
    }

}

