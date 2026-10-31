package com.masterwok.simpletorrentandroid.extensions

import java.net.URI

/** libtorrent4j exposes endpoints as tcp://host:port, including bracketed IPv6. */
internal fun parsePeerAddress(raw: String): Pair<String, Int>? = runCatching {
    val uri = URI(if (raw.contains("://")) raw else "tcp://$raw")
    require(uri.scheme in setOf("tcp", "udp", "utp") && uri.userInfo == null)
    require(uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null)
    val host = requireNotNull(uri.host).removePrefix("[").removeSuffix("]")
    require(host.isNotBlank() && uri.port in 1..65535)
    host to uri.port
}.getOrNull()
