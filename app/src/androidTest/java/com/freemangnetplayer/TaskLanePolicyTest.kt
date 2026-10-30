package com.freemangnetplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TaskLanePolicyTest {
    private fun task(download: Boolean, index: Int) =
        TorrentTask("lane-$download-$index", "magnet:?xt=urn:btih:2362347540B27D8A4142AF2BBE0A071F26A3B7A1",
            "Task $index", File("/data/local/tmp/lane-$index"), download).apply { queued = false; job = Job() }
    @Test fun cacheAndPlaybackNeverConsumeDownloadSlots() {
        val values = (0 until 5).map { task(true, it) } + (0 until 10).map { task(false, it) }
        assertEquals(5, TaskLanePolicy.activeCount(values, true))
        assertEquals(10, TaskLanePolicy.activeCount(values, false))
        values.last().queued = true
        assertEquals(5, TaskLanePolicy.activeCount(values, true))
        assertEquals(9, TaskLanePolicy.activeCount(values, false))
        values.forEach { it.job?.cancel() }
    }
    @Test fun pausedCompletedAndCancelledDoNotConsumeSlots() {
        val values = (0 until 5).map { task(true, it) }
        values[0].paused = true; values[1].complete = true; values[2].cancelled = true
        assertEquals(2, TaskLanePolicy.activeCount(values, true))
        values.forEach { it.job?.cancel() }
    }
}
