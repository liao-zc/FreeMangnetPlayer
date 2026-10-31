package com.omniplayer.magnet

/** A verified byte interval relative to the selected media file. */
data class CacheRange(val start: Long, val endExclusive: Long)

internal object CacheCoverage {
    fun fileRanges(
        fileOffset: Long,
        fileSize: Long,
        pieceLength: Int,
        hasPiece: (Int) -> Boolean
    ): List<CacheRange> {
        if (fileSize <= 0 || pieceLength <= 0) return emptyList()
        val first = (fileOffset / pieceLength).toInt()
        val last = ((fileOffset + fileSize - 1) / pieceLength).toInt()
        val end = fileOffset + fileSize
        val result = mutableListOf<CacheRange>()
        for (piece in first..last) {
            if (!hasPiece(piece)) continue
            val start = maxOf(fileOffset, piece.toLong() * pieceLength) - fileOffset
            val pieceEnd = minOf(end, (piece + 1L) * pieceLength) - fileOffset
            val previous = result.lastOrNull()
            if (previous != null && start <= previous.endExclusive) {
                result[result.lastIndex] = previous.copy(endExclusive = maxOf(previous.endExclusive, pieceEnd))
            } else result += CacheRange(start, pieceEnd)
        }
        return result
    }

    fun describe(ranges: List<CacheRange>, fileSize: Long, durationMs: Long): String {
        if (ranges.isEmpty()) return "暂无已校验缓存区间"
        if (fileSize <= 0 || durationMs <= 0) return "已校验 ${ranges.size} 段缓存区间"
        val visible = ranges.take(4).joinToString("、") { range ->
            "${time(range.start.toDouble() / fileSize * durationMs)}–${time(range.endExclusive.toDouble() / fileSize * durationMs)}"
        }
        val remaining = ranges.size - minOf(ranges.size, 4)
        return "缓存时段（按文件位置估算）：$visible${if (remaining > 0) " 等 $remaining 段" else ""}"
    }

    private fun time(milliseconds: Double): String {
        val seconds = (milliseconds / 1000).toLong().coerceAtLeast(0)
        return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}
