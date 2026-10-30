package com.masterwok.simpletorrentandroid.extensions

import org.libtorrent4j.TorrentInfo


/**
 * Get the largest file index of the [TorrentInfo].
 */
internal fun TorrentInfo.getLargestFileIndex(): Int = files().getLargestFileIndex()
