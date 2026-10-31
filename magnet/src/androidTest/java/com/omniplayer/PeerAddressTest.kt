package com.omniplayer


import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masterwok.simpletorrentandroid.extensions.parsePeerAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeerAddressTest {
    @Test fun modernAndLegacyIpv4PreserveAddressAndPort() {
        assertEquals("192.0.2.1" to 51413, parsePeerAddress("tcp://192.0.2.1:51413"))
        assertEquals("192.0.2.1" to 51413, parsePeerAddress("192.0.2.1:51413"))
        assertEquals("192.0.2.1" to 51413, parsePeerAddress("utp://192.0.2.1:51413"))
    }
    @Test fun ipv6PreservesAddressAndPort() {
        assertEquals("2001:db8::1" to 6881, parsePeerAddress("tcp://[2001:db8::1]:6881"))
        assertEquals("2001:db8::1" to 6881, parsePeerAddress("[2001:db8::1]:6881"))
    }
    @Test fun malformedEndpointsAreRejected() {
        for (raw in listOf("tcp://192.0.2.1", "tcp://192.0.2.1:0", "tcp://192.0.2.1:70000", "https://192.0.2.1:6881", "tcp://user@192.0.2.1:6881", "tcp://192.0.2.1:6881/file"))
            assertNull(raw, parsePeerAddress(raw))
    }
}
