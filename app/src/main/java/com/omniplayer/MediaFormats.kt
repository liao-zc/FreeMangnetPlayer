package com.omniplayer

/** Container recognition is separate from whether an individual codec can decode. */
internal object MediaFormats {
    val extensions = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "qt", "ts", "mts", "m2ts", "m2t", "mpeg", "mpg", "mpe", "vob", "flv", "f4v", "wmv", "asf", "rm", "rmvb", "ogv", "ogg", "3gp", "3g2", "divx", "mxf", "mpv", "h264", "264", "h265", "265", "hevc")
    // MKV commonly carries HEVC/Dolby Vision, E-AC-3, and other tracks that
    // are absent from the device's MediaCodec set. Route it through VLC first
    // so the player can inspect and decode the container instead of remaining
    // on Media3's indefinite track-discovery spinner.
    private val softwareContainers = setOf("mkv", "avi", "divx", "wmv", "asf", "rm", "rmvb", "flv", "f4v", "mpeg", "mpg", "mpe", "vob", "ogv", "ogg", "mxf", "h265", "265", "hevc", "qt")
    fun extension(path: String) = path.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    fun isVideo(path: String) = extension(path) in extensions
    fun useSoftware(path: String) = extension(path) in softwareContainers
}
