package com.omniplayer.magnet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Secondary timeline layer showing verified torrent pieces in a distinct color. */
class CacheCoverageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55D3C2F2 }
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A8F82.toInt() }
    private var ranges: List<CacheRange> = emptyList()
    private var fileSize: Long = 0

    fun submit(verifiedRanges: List<CacheRange>, totalBytes: Long, description: String) {
        ranges = verifiedRanges
        fileSize = totalBytes
        contentDescription = description
        visibility = if (ranges.isEmpty()) INVISIBLE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val height = height.toFloat()
        val width = width.toFloat()
        canvas.drawRoundRect(0f, 0f, width, height, height, height, backgroundPaint)
        if (fileSize <= 0) return
        ranges.forEach { range ->
            val left = width * range.start.toFloat() / fileSize
            val right = width * range.endExclusive.toFloat() / fileSize
            canvas.drawRoundRect(left, 0f, right.coerceAtLeast(left + height), height, height, height, rangePaint)
        }
    }
}
