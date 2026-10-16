package com.superiorstudent.app.feature.studentconnect

import com.google.firebase.Timestamp
import com.superiorstudent.app.feature.studentconnect.data.StudentPresence
import com.superiorstudent.app.feature.studentconnect.data.StudentPresenceFormatter
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class StudentPresenceFormatterTest {
    @Test
    fun freshHeartbeatDisplaysOnline() {
        val now = 1_000_000L
        val state = StudentPresence("studentA", true, Timestamp(Date(now)), Timestamp(Date(now - 10_000L)))
        assertEquals("Online", StudentPresenceFormatter.label(state, now))
    }

    @Test
    fun expiredOnlineHeartbeatFallsBackToLastSeen() {
        val now = 1_000_000L
        val state = StudentPresence("studentA", true, Timestamp(Date(now - 120_000L)), Timestamp(Date(now - 120_000L)))
        assertEquals("Last seen 2m ago", StudentPresenceFormatter.label(state, now))
    }

    @Test
    fun offlinePresenceDisplaysItsLastSeenTime() {
        val now = 1_000_000L
        val state = StudentPresence("studentA", false, Timestamp(Date(now - 35_000L)), Timestamp(Date(now - 35_000L)))
        assertEquals("Last seen just now", StudentPresenceFormatter.label(state, now))
    }

    @Test
    fun missingPresenceDocumentDisplaysOffline() {
        assertEquals("Offline", StudentPresenceFormatter.label(null, 1_000_000L))
    }
}
