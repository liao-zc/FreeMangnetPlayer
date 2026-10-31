package com.omniplayer.magnet

/** Select disk playback only after the complete file was piece-verified and persisted. */
object CachePlaybackPolicy {
    fun useVerifiedLocalFile(expectedSize: Long, verifiedBytes: Long, fileExists: Boolean, physicalBytes: Long): Boolean =
        expectedSize > 0 && verifiedBytes >= expectedSize && fileExists && physicalBytes >= expectedSize
}
