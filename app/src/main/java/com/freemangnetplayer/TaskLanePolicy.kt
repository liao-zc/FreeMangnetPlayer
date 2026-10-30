package com.freemangnetplayer

/** Downloads and cache/playback tasks never consume each other's slots. */
internal object TaskLanePolicy {
    fun active(task: TorrentTask) = (task.engine != null || task.job?.isActive == true) &&
        !task.queued && !task.paused && !task.complete && !task.cancelled
    fun activeCount(tasks: Collection<TorrentTask>, download: Boolean) =
        tasks.count { it.download == download && active(it) }
}
