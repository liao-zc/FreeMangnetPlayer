package com.omniplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omniplayer.magnet.CachePlaybackPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CachePlaybackPolicyTest {
    @Test fun completeVerifiedFileUsesDiskButPartialAndSparseFilesKeepTorrentReader() {
        assertTrue(CachePlaybackPolicy.useVerifiedLocalFile(100, 100, true, 100))
        assertTrue(CachePlaybackPolicy.useVerifiedLocalFile(100, 140, true, 100))
        assertFalse(CachePlaybackPolicy.useVerifiedLocalFile(100, 99, true, 100))
        assertFalse(CachePlaybackPolicy.useVerifiedLocalFile(100, 0, true, 100))
        assertFalse(CachePlaybackPolicy.useVerifiedLocalFile(100, 100, false, 100))
        assertFalse(CachePlaybackPolicy.useVerifiedLocalFile(100, 100, true, 99))
        assertFalse(CachePlaybackPolicy.useVerifiedLocalFile(0, 0, true, Long.MAX_VALUE))
    }
}
