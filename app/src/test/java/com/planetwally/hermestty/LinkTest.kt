package com.planetwally.hermestty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkTest {
    @Test
    fun normalizesBaseUrls() {
        assertEquals("http://10.0.0.5:8642", normalizeBaseUrl("10.0.0.5:8642"))
        assertEquals("http://10.0.0.5:8642", normalizeBaseUrl(" http://10.0.0.5:8642/v1/ "))
        assertEquals("https://box.tail1234.ts.net", normalizeBaseUrl("https://box.tail1234.ts.net/"))
        assertEquals("", normalizeBaseUrl("  "))
    }

    @Test
    fun parsesPairingLinks() {
        val p = parsePairingLink("hermestty://connect?url=http%3A%2F%2F192.168.1.5%3A8642&key=s3cr%2Bt")
        assertEquals(PairRequest("http://192.168.1.5:8642", "s3cr+t"), p)
        assertEquals("http://192.168.1.5:8642", p!!.host)
    }

    @Test
    fun rejectsForeignOrIncompleteLinks() {
        assertNull(parsePairingLink(null))
        assertNull(parsePairingLink("not a link"))
        assertNull(parsePairingLink("otherapp://connect?url=http%3A%2F%2Fx&key=k"))
        assertNull(parsePairingLink("hermestty://other?url=http%3A%2F%2Fx&key=k"))
        assertNull(parsePairingLink("hermestty://connect?url=http%3A%2F%2Fx"))
        assertNull(parsePairingLink("hermestty://connect?url=&key=k"))
    }

    @Test
    fun classifiesTransport() {
        assertEquals(Transport.ENCRYPTED, transportOf("https://agent.example.com"))
        assertEquals(Transport.ENCRYPTED, transportOf("http://100.101.102.103:8642")) // tailnet
        assertEquals(Transport.ENCRYPTED, transportOf("http://box.tail1234.ts.net:8642"))
        assertEquals(Transport.ENCRYPTED, transportOf("127.0.0.1:8642"))
        assertEquals(Transport.ENCRYPTED, transportOf("http://[fd7a:115c:a1e0::1]:8642"))
        assertEquals(Transport.LOCAL_CLEARTEXT, transportOf("192.168.1.52:8642"))
        assertEquals(Transport.LOCAL_CLEARTEXT, transportOf("http://10.0.0.2:8642"))
        assertEquals(Transport.LOCAL_CLEARTEXT, transportOf("http://172.20.0.2:8642"))
        assertEquals(Transport.LOCAL_CLEARTEXT, transportOf("http://hermes-box.local:8642"))
        assertEquals(Transport.LOCAL_CLEARTEXT, transportOf("http://hermesbox:8642"))
        assertEquals(Transport.PUBLIC_CLEARTEXT, transportOf("http://203.0.113.7:8642"))
        assertEquals(Transport.PUBLIC_CLEARTEXT, transportOf("http://172.32.0.1:8642"))
        assertEquals(Transport.PUBLIC_CLEARTEXT, transportOf("http://agent.example.com:8642"))
        assertNull(transportWarning("https://agent.example.com"))
        assertEquals(Tone.ERROR, transportWarning("http://agent.example.com")!!.second)
    }
}
