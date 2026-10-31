package com.omniplayer.magnet
import com.omniplayer.core.*
/** Downloads and cache/playback tasks never consume each other's slots. */
internal object TaskLanePolicy {
    const val NO_DATA_WARNING_AFTER_MS = 180_000L

    fun shouldWarnUnplayable(hasMetadata: Boolean, requestedVideo: Boolean, hasVerifiedBytes: Boolean, payloadBytes: Long, peerCount: Int, metadataAgeMs: Long) =
        hasMetadata && requestedVideo && !hasVerifiedBytes && payloadBytes <= 0L && peerCount == 0 && metadataAgeMs >= NO_DATA_WARNING_AFTER_MS

    fun active(task: TorrentTask) = (task.engine != null || task.job?.isActive == true) &&
        !task.queued && !task.paused && !task.complete && !task.cancelled && task.unplayableReason.isBlank()
    fun activeCount(tasks: Collection<TorrentTask>, download: Boolean) =
        tasks.count { it.download == download && active(it) }
}
