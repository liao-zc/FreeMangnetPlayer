package com.omniplayer
import com.omniplayer.core.*
import com.omniplayer.live.*
import com.omniplayer.magnet.*


import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry

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

    @Test fun activePlaybackGetsPriorityUntilPlaybackFails() {
        val playing = task(false, 99).apply { viewers = 1 }
        assertTrue(TaskLanePolicy.playbackPriority(playing))
        playing.playbackFailed = true
        assertEquals(false, TaskLanePolicy.playbackPriority(playing))
        playing.playbackFailed = false
        playing.paused = true
        assertEquals(false, TaskLanePolicy.playbackPriority(playing))
        playing.job?.cancel()
    }

    @Test fun playbackPositionIsStoredAndRestoredPerTorrentFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hash = "fedcba9876543210fedcba9876543210fedcba98"
        val source = "magnet:?xt=urn:btih:$hash&dn=episode.mkv&so=2"
        try {
            LibraryStore.addPlayback(context, "episode.mkv", source, 481_000, 1_200_000)
            assertEquals(481_000L, LibraryStore.playbackPosition(context, hash, 2))
            assertEquals(0L, LibraryStore.playbackPosition(context, hash, 1))
        } finally {
            LibraryStore.removeEntries(context, "播放", setOf(source))
            HistoryStore.remove(context, "播放", source)
        }
    }

    @Test fun taskFoldersUseOmniPlayerNameAndMigrateLegacyData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "task-folder-migration-check").apply { deleteRecursively(); mkdirs() }
        val prefs = SettingsStore.prefs(context)
        val previous = prefs.getString("download-path", null)
        prefs.edit().putString("download-path", root.absolutePath).commit()
        try {
            val id = "migration-check"
            val legacy = File(root, "FreeMangnetPlayerTasks/$id").apply { mkdirs() }
            File(legacy, "resume.dat").writeText("keep")
            val migrated = SettingsStore.taskFolder(context, true, id)
            assertEquals("OmniPlayerTasks", migrated.parentFile?.name)
            assertTrue(File(migrated, "resume.dat").readText() == "keep")
            assertTrue(!legacy.exists())
        } finally {
            prefs.edit().putString("download-path", previous).commit()
            root.deleteRecursively()
        }
    }

    @Test fun confirmedNoDataWarningPersistsAndDoesNotConsumeDownloadOrCacheSlots() {
        assertEquals(false, TaskLanePolicy.shouldWarnUnplayable(false, true, false, 0, 0, 600_000))
        assertEquals(false, TaskLanePolicy.shouldWarnUnplayable(true, true, true, 0, 0, 600_000))
        assertEquals(false, TaskLanePolicy.shouldWarnUnplayable(true, true, false, 1, 0, 600_000))
        assertEquals(false, TaskLanePolicy.shouldWarnUnplayable(true, true, false, 0, 1, 600_000))
        assertEquals(false, TaskLanePolicy.shouldWarnUnplayable(true, true, false, 0, 0, TaskLanePolicy.NO_DATA_WARNING_AFTER_MS - 1))
        assertEquals(true, TaskLanePolicy.shouldWarnUnplayable(true, true, false, 0, 0, TaskLanePolicy.NO_DATA_WARNING_AFTER_MS))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "abcdef0123456789abcdef0123456789abcdef01"
        val source = "magnet:?xt=urn:btih:$id"
        val failed = TorrentTask(id, source, "No seed", File(context.cacheDir, id), true).apply {
            files = listOf(TaskFile(0, "episode.mkv", 1024))
            selected = setOf(0)
            job = Job()
        }
        val reason = "⚠ 无法播放：没有收到有效视频数据"
        TorrentTasks.tasks[id] = failed
        try {
            LibraryStore.addFavorite(context, "No seed", source)
            LibraryStore.addPlayback(context, "No seed", source)
            LibraryStore.updateDownload(context, "No seed", source, reason)
            LibraryStore.saveCache(context, LibraryEntry("No seed", source, reason, unplayableReason = reason))
            LibraryStore.markUnplayable(context, failed, reason)

            assertEquals(reason, LibraryStore.readFavorites(context).first { it.source == source }.unplayableReason)
            assertEquals(reason, LibraryStore.readPlaybacks(context).first { it.source == source }.unplayableReason)
            assertEquals(reason, LibraryStore.readDownloads(context).first { it.source.contains(id) }.unplayableReason)
            assertEquals(reason, LibraryStore.readCaches(context).first { it.source == source }.unplayableReason)
            assertEquals(0, TaskLanePolicy.activeCount(listOf(failed), true))
            failed.download = false; failed.retainedCache = true; failed.paused = false
            assertEquals(0, TaskLanePolicy.activeCount(listOf(failed), false))
        } finally {
            failed.job?.cancel()
            TorrentTasks.tasks.remove(id)
            LibraryStore.removeEntries(context, "收藏", setOf(source))
            LibraryStore.removeEntries(context, "播放", setOf(source))
            LibraryStore.removeEntries(context, "下载", setOf(source, StoredVideos.fileSource(failed, failed.files.first())))
            LibraryStore.removeEntries(context, "缓存", setOf(source))
        }
    }
}
