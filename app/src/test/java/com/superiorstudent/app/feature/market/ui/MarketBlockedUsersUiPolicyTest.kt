package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketBlockedUsersUiPolicyTest {
    @Test
    fun referenceHasExactTitleUsersAndNotice() {
        assertEquals("Blocked users", MarketBlockedUsersUiPolicy.TITLE)
        assertEquals(
            listOf("Bilal Raza", "Kamran Hassan"),
            MarketBlockedUsersUiPolicy.referenceUsers.map { it.name }
        )
        assertEquals(
            listOf("UNI-B9Z4C2L", "UNI-K2D8P4M"),
            MarketBlockedUsersUiPolicy.referenceUsers.map { it.universityId }
        )
        assertEquals(
            "You will not see their tasks, offers or proposals, and they cannot start a new Market chat with you.",
            MarketBlockedUsersUiPolicy.INFORMATION_NOTE
        )
        assertTrue(MarketBlockedUsersUiPolicy.hasReferenceContent())
    }

    @Test
    fun blockedCountReflectsCurrentPreviewListSize() {
        assertEquals("2 blocked", MarketBlockedUsersUiPolicy.countLabel(2))
        assertEquals("1 blocked", MarketBlockedUsersUiPolicy.countLabel(1))
        assertEquals("0 blocked", MarketBlockedUsersUiPolicy.countLabel(0))
        assertEquals("0 blocked", MarketBlockedUsersUiPolicy.countLabel(-4))
    }

    @Test
    fun removingExampleOnlyRemovesMatchingId() {
        val users = MarketBlockedUsersUiPolicy.referenceUsers
        assertEquals(
            listOf("Kamran Hassan"),
            MarketBlockedUsersUiPolicy.withoutUser(users, "UNI-B9Z4C2L").map { it.name }
        )
        assertEquals(users, MarketBlockedUsersUiPolicy.withoutUser(users, "not-found"))
        assertEquals(emptyList<MarketBlockedUser>(),
            MarketBlockedUsersUiPolicy.withoutUser(users, "UNI-B9Z4C2L").let {
                MarketBlockedUsersUiPolicy.withoutUser(it, "UNI-K2D8P4M")
            })
    }
}
