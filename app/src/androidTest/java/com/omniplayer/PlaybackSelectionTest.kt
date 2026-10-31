package com.omniplayer
import com.omniplayer.core.*
import com.omniplayer.live.*
import com.omniplayer.magnet.*


import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlaybackSelectionTest {
    private fun task() = TorrentTask("selection-test", "magnet:?xt=urn:btih:2362347540B27D8A4142AF2BBE0A071F26A3B7A1",
        "Selection test", File("/data/local/tmp/selection-test"), false).apply {
        files = listOf(TaskFile(0, "Episode-01.mp4", 1000), TaskFile(1, "Episode-02.mp4", 1000))
    }
    @Test fun playbackPreparationAndRetryPreserveExplicitSelection() {
        TorrentTasks.initialize(ApplicationProvider.getApplicationContext())
        val task = task().apply { selected = setOf(0, 1) }
        repeat(3) { TorrentTasks.playFile(task, 0) }
        assertEquals(setOf(0, 1), task.selected)
        assertEquals(0, task.playingIndex)
    }
    @Test fun playbackChoosesAFileWhenSelectionIsEmpty() {
        TorrentTasks.initialize(ApplicationProvider.getApplicationContext())
        val task = task()
        TorrentTasks.playFile(task, 1)
        assertEquals(setOf(1), task.selected)
        assertEquals(1, task.playingIndex)
    }
}
