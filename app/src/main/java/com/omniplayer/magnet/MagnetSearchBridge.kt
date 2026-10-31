package com.omniplayer.magnet

import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
internal class MagnetSearchBridge(
    private val onResource: (JSONObject) -> Unit = {},
    private val onPagination: (JSONObject) -> Unit = {},
    private val onPage: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },
    private val onMagnet: (String, String) -> Unit
) {
    @JavascriptInterface
    fun onPageInfo(json: String) {
        runCatching { val value = JSONObject(json); onPagination(value); onPage(value.optString("url"), value.optString("next"), value.optInt("pending"), value.optInt("failures")) }
    }
    @JavascriptInterface
    fun onResults(json: String) {
        runCatching {
            val rows = JSONArray(json)
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val link = normalizeMagnetUri(row.optString("source"))
                if (link != null) {
                    onMagnet(row.optString("title"), link); onResource(row)
                }
            }
        }
    }
}
