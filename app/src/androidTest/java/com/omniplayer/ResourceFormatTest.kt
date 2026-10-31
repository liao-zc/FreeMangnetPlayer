package com.omniplayer
import com.omniplayer.core.*
import com.omniplayer.live.*
import com.omniplayer.magnet.*


import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceFormatTest {
    private val magnet = "magnet:?xt=urn:btih:2362347540B27D8A4142AF2BBE0A071F26A3B7A1"

    @Test fun choosesLargestVideoExtensionBeforeNonVideoFiles() {
        val files = listOf(
            TaskFile(0, "release/archive.zip", 5_000_000_000),
            TaskFile(1, "release/episode-720p.mkv", 900_000_000),
            TaskFile(2, "release/episode-1080p.mp4", 2_000_000_000)
        )
        assertEquals(".MP4", torrentResourceFormat(files, magnet))
    }

    @Test fun labelsArchiveAndUsesMagnetDisplayNameWhenMetadataNameHasNoExtension() {
        assertEquals(".RAR（压缩包/镜像）", torrentResourceFormat(listOf(
            TaskFile(0, "bundle.zip", 10), TaskFile(1, "bundle.part2.rar", 20)
        ), magnet))
        assertEquals(".MP4", detectResourceFormat("资源", "$magnet&dn=episode%2Emp4"))
    }

    @Test fun retriesMkvWithCompatibleDecoderOnlyWhenHardwarePathCannotRender() {
        org.junit.Assert.assertTrue(MediaFormats.isVideo("Spider-Man.mkv"))
        org.junit.Assert.assertFalse(MediaFormats.useSoftware("Spider-Man.mkv"))
        org.junit.Assert.assertTrue(MediaFormats.shouldRetryWithSoftware("Spider-Man.mkv"))
        org.junit.Assert.assertFalse(MediaFormats.useSoftware("clip.mp4"))
        org.junit.Assert.assertFalse(MediaFormats.shouldRetryWithSoftware("clip.mp4"))
    }
}
