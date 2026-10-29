package com.freemangnetplayer

/** Container recognition is separate from whether an individual codec can decode. */
internal object MediaFormats {
    val extensions = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "qt", "ts", "mts", "m2ts", "m2t", "mpeg", "mpg", "mpe", "vob", "flv", "f4v", "wmv", "asf", "rm", "rmvb", "ogv", "ogg", "3gp", "3g2", "divx", "mxf", "mpv", "h264", "264", "h265", "265", "hevc")
    private val softwareContainers = setOf("avi", "divx", "wmv", "asf", "rm", "rmvb", "flv", "f4v", "mpeg", "mpg", "mpe", "vob", "ogv", "ogg", "mxf", "h265", "265", "hevc", "qt")
    fun extension(path: String) = path.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    fun isVideo(path: String) = extension(path) in extensions
    fun useSoftware(path: String) = extension(path) in softwareContainers
}
