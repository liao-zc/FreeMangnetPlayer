package com.omniplayer.core

fun formatLibraryBytes(value: Long): String = when {
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "%.1f KB".format(value / 1024f)
    value < 1024L * 1024 * 1024 -> "%.1f MB".format(value / (1024f * 1024))
    else -> "%.1f GB".format(value / (1024f * 1024 * 1024))
}