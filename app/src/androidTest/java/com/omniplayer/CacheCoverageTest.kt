package com.omniplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omniplayer.magnet.CacheCoverage
import com.omniplayer.magnet.CacheRange
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheCoverageTest {
    @Test fun mapsVerifiedPiecesToFileRelativeRangesAndClipsBoundaries() {
        val ranges = CacheCoverage.fileRanges(1_000, 3_000, 1_024) { it == 1 || it == 3 }
        assertEquals(listOf(CacheRange(24, 1_048), CacheRange(2_072, 3_000)), ranges)
    }

    @Test fun adjacentVerifiedPiecesMergeAndGapsRemainVisible() {
        val ranges = CacheCoverage.fileRanges(0, 5_000, 1_000) { it == 0 || it == 1 || it == 4 }
        assertEquals(listOf(CacheRange(0, 2_000), CacheRange(4_000, 5_000)), ranges)
    }

    @Test fun timelineLabelsAreExplicitlyEstimatedFromFileOffsets() {
        assertEquals(
            "缓存时段（按文件位置估算）：01:00–02:00",
            CacheCoverage.describe(listOf(CacheRange(100, 200)), 1_000, 600_000)
        )
    }

    @Test fun emptyOrInvalidTimelineHasSafeDescription() {
        assertEquals("暂无已校验缓存区间", CacheCoverage.describe(emptyList(), 1_000, 600_000))
        assertEquals("已校验 1 段缓存区间", CacheCoverage.describe(listOf(CacheRange(0, 1)), 0, 600_000))
        assertEquals(emptyList<CacheRange>(), CacheCoverage.fileRanges(0, 0, 1_024) { true })
    }
}
