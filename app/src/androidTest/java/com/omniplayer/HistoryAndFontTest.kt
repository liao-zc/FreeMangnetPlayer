package com.omniplayer

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryAndFontTest {
    private val c get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun historyLimitsDescendingAndProgress() {
        HistoryStore.clear(c,null)
        for((kind,limit) in listOf("搜索" to 50,"播放" to 100,"下载" to 50,"缓存" to 30)) {
            for(i in 0..limit) HistoryStore.record(c,kind,HistoryEntry("文件$i","source$i",1000L+i,status="下载中",position=1500,duration=9000,done=50,size=100))
            val rows=HistoryStore.read(c,kind);assertEquals(limit,rows.size)
            assertEquals("source$limit",rows.first().source)
            assertTrue(rows.zipWithNext().all { (a,b) -> a.time>=b.time })
            HistoryStore.update(c,kind,rows.first().copy(done=80));assertEquals(80L,HistoryStore.read(c,kind).first().done)
        }
        assertEquals(1500L,HistoryStore.read(c,"播放").first().position)
        HistoryStore.clear(c,"搜索");assertTrue(HistoryStore.read(c,"搜索").isEmpty());assertEquals(100,HistoryStore.read(c,"播放").size)
        HistoryStore.clear(c,null);assertTrue(HistoryStore.kinds.all { HistoryStore.read(c,it).isEmpty() })
    }
    @Test fun fontsLoadAndCustomScalePersists() {
        val prefs=SettingsStore.prefs(c)
        val widths=listOf("黑体","宋体","楷体").map { name ->
            prefs.edit().putString("font-face",name).commit()
            val p=android.graphics.Paint();p.typeface=DisplaySettings.typeface(c);p.textSize=32f;p.measureText("视频播放测试 ABC 123")
        }
        assertTrue(widths.distinct().size>=2)
        for(scale in listOf(.85f,1f,1.15f,1.3f,1.47f)) {prefs.edit().putFloat("font-scale",scale).commit();assertEquals(scale,DisplaySettings.scale(c),.001f)}
        prefs.edit().putString("font-face","黑体").putFloat("font-scale",1f).commit()
    }
    @Test fun playbackProgressAndFavoritesToggleStorage() {
        val source="magnet:?xt=urn:btih:2362347540B27D8A4142AF2BBE0A071F26A3B7A1&so=0"
        LibraryStore.addPlayback(c,"测试视频",source,42000,90000)
        val entry=LibraryStore.readPlaybacks(c).first { it.source==source };assertEquals(42000L,entry.position);assertEquals(90000L,entry.duration)
        LibraryStore.addFavorite(c,"测试视频",source);assertTrue(LibraryStore.readFavorites(c).any{it.source==source})
        LibraryStore.removeEntries(c,"收藏",setOf(source));assertFalse(LibraryStore.readFavorites(c).any{it.source==source})
    }
}
