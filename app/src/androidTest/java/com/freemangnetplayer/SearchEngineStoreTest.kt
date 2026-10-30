package com.freemangnetplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchEngineStoreTest {
    @Test fun keepsSixEnginesAndSupportsEditDeleteAndTenEngineLimit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SettingsStore.prefs(context)
        val original = SearchEngineStore.load(context)
        val originalDefault = prefs.getString("search-name", SearchEngineStore.defaultName()) ?: SearchEngineStore.defaultName()
        try {
            org.junit.Assert.assertTrue(original.size >= 6)
            org.junit.Assert.assertTrue(original.map { it.name }.containsAll(listOf("clg.im", "BT搜索（dobt）", "BD电影首发站", "Pomo", "美剧粉", "高清族")))
            assertEquals("BD电影首发站", SearchEngineStore.defaultName())
            val edited = original.map { if (it.name == "clg.im") it.copy(name = "clg edited") else it }
            val withoutOne = edited.filterNot { it.name == "高清族" }
            SearchEngineStore.save(context, withoutOne, "BD电影首发站")
            assertEquals(original.size - 1, SearchEngineStore.load(context).size)
            assertEquals("clg edited", SearchEngineStore.load(context).first().name)
            var rejectedTooMany = false
            try {
                SearchEngineStore.save(context, (0..10).map { SearchProvider("E$it", "https://e$it.example/") }, "E0")
            } catch (_: IllegalArgumentException) { rejectedTooMany = true }
            org.junit.Assert.assertTrue(rejectedTooMany)
        } finally {
            SearchEngineStore.save(context, original, originalDefault)
        }
    }
}
