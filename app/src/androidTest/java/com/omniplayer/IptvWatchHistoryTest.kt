package com.omniplayer
import com.omniplayer.core.*
import com.omniplayer.live.*
import com.omniplayer.magnet.*


import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IptvWatchHistoryTest {
    @Test fun historyRetainsChannelAfterSourceRemovalAndSupportsMultiDelete() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rows = listOf(
            IptvChannel("频道甲", "https://example.test/a.m3u8", "新闻", "https://example.test/a.png", "history-a"),
            IptvChannel("频道乙", "https://example.test/b.m3u8", "电影", "", "history-b")
        )
        try {
            rows.forEach { IptvStore.recordWatch(context, it) }
            assertEquals(setOf("history-a", "history-b"), IptvStore.watchHistory(context).map { it.channel.id }.toSet())
            IptvStore.removeWatchHistory(context, setOf("history-a"))
            assertEquals(listOf("history-b"), IptvStore.watchHistory(context).map { it.channel.id })
        } finally {
            IptvStore.removeWatchHistory(context, setOf("history-a", "history-b"))
        }
    }
}
