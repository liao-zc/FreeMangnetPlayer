package com.masterwok.simpletorrentandroid.extensions

import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.libtorrent

/** libtorrent 2.1 stores trackers in add parameters, not mutable torrent_info. */
internal fun TorrentInfo.bencode(trackers: List<String> = emptyList()): ByteArray {
    val parameters = AddTorrentParams()
    parameters.setTorrentInfo(this)
    parameters.setTrackers(trackers.distinct())
    val bytes = libtorrent.write_torrent_file_buf_ex(parameters.swig())
    return ByteArray(bytes.size()) { bytes.get(it) }
}
