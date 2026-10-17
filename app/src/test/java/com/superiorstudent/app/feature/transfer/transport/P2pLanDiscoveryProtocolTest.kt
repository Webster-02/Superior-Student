package com.superiorstudent.app.feature.transfer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pLanDiscoveryProtocolTest {

    @Test
    fun acceptsExpectedOpaqueSessionIds() {
        assertTrue(P2pLanDiscoveryProtocol.isValidSessionId("transfer_0123456789ab"))
        assertTrue(P2pLanDiscoveryProtocol.isValidSessionId("ABCDEFGHIJKLMNOPQRSTUVWXYZ_123456"))
    }

    @Test
    fun rejectsShortOrUnsafeSessionIds() {
        assertFalse(P2pLanDiscoveryProtocol.isValidSessionId("too-short"))
        assertFalse(P2pLanDiscoveryProtocol.isValidSessionId("transfer/session/123456"))
        assertFalse(P2pLanDiscoveryProtocol.isValidSessionId("transfer session 123456"))
        assertFalse(P2pLanDiscoveryProtocol.isValidSessionId("x".repeat(81)))
    }

    @Test
    fun createsDnsSafeBoundedServiceNames() {
        val name = P2pLanDiscoveryProtocol.createServiceName(
            deviceLabel = "Awais phone / main Wi-Fi",
            sessionId = "transfer_0123456789ab"
        )

        assertTrue(name.length <= 63)
        assertTrue(name.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertTrue(name.endsWith("01234589ab".takeLast(8)))
    }

    @Test
    fun uniqueSessionSuffixAvoidsNameCollisions() {
        val first = P2pLanDiscoveryProtocol.createServiceName("Student", "transfer_0123456789ab")
        val second = P2pLanDiscoveryProtocol.createServiceName("Student", "transfer_0123456789cd")

        assertNotEquals(first, second)
    }

    @Test
    fun acceptsDnsSdServiceTypeWithOptionalTrailingDot() {
        assertTrue(P2pLanDiscoveryProtocol.isOurServiceType("_superiorstudentp2p._tcp"))
        assertTrue(P2pLanDiscoveryProtocol.isOurServiceType("_SUPERIORSTUDENTP2P._TCP."))
        assertFalse(P2pLanDiscoveryProtocol.isOurServiceType("_http._tcp."))
        assertFalse(P2pLanDiscoveryProtocol.isOurServiceType(null))
    }

    @Test
    fun protocolVersionAndServiceTypeRemainStable() {
        assertEquals(1, P2pLanDiscoveryProtocol.PROTOCOL_VERSION)
        assertEquals("_superiorstudentp2p._tcp.", P2pLanDiscoveryProtocol.SERVICE_TYPE)
    }
}
