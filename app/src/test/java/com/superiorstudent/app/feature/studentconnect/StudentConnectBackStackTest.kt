package com.superiorstudent.app.feature.studentconnect

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentConnectBackStackTest {
    private enum class Screen { HOME, FIND, CHATS, REQUESTS, PROFILE, CONNECTIONS, NOTIFICATIONS }

    @Test
    fun profileBackReturnsToHomeAndHomeIsTheExitBoundary() {
        val stack = StudentConnectBackStack(Screen.HOME)
        stack.navigate(Screen.HOME, Screen.PROFILE)

        assertEquals(Screen.HOME, stack.back(Screen.PROFILE))
        assertEquals(Screen.HOME, stack.back(Screen.HOME))
        assertEquals(0, stack.sizeForTest())
    }

    @Test
    fun requestsAndFindUnwindOneDestinationAtATime() {
        val stack = StudentConnectBackStack(Screen.HOME)
        stack.navigate(Screen.HOME, Screen.FIND)
        stack.navigate(Screen.FIND, Screen.REQUESTS)

        assertEquals(Screen.FIND, stack.back(Screen.REQUESTS))
        assertEquals(Screen.HOME, stack.back(Screen.FIND))
        assertEquals(Screen.HOME, stack.back(Screen.HOME))
    }

    @Test
    fun chatReturnsToChatListThenHomeEvenWhenOpenedFromConnections() {
        val stack = StudentConnectBackStack(Screen.HOME)
        stack.navigate(Screen.HOME, Screen.CONNECTIONS)
        stack.reset(Screen.HOME, Screen.CHATS)

        assertEquals(Screen.HOME, stack.back(Screen.CHATS))
        assertEquals(Screen.HOME, stack.back(Screen.HOME))
    }

    @Test
    fun navigatingHomeClearsOlderDestinations() {
        val stack = StudentConnectBackStack(Screen.HOME)
        stack.navigate(Screen.HOME, Screen.PROFILE)
        stack.navigate(Screen.PROFILE, Screen.REQUESTS)
        stack.navigate(Screen.REQUESTS, Screen.HOME)

        assertEquals(0, stack.sizeForTest())
        assertEquals(Screen.HOME, stack.back(Screen.HOME))
    }

    @Test
    fun nearbyShareFinishesBackToTheU2UHomeThatLaunchedIt() {
        // Nearby Share is a separate Activity launched from U2U Home.
        val stack = StudentConnectBackStack(Screen.HOME)
        assertEquals(Screen.HOME, stack.back(Screen.HOME))
        assertEquals(0, stack.sizeForTest())
    }
}
