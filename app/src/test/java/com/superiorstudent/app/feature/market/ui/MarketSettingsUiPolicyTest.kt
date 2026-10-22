package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketSettingsUiPolicyTest {
    @Test
    fun referenceHasExpectedTitleSkillsAndRows() {
        assertEquals("Market settings", MarketSettingsUiPolicy.TITLE)
        assertEquals("I can help with", MarketSettingsUiPolicy.SKILLS_TITLE)
        assertEquals("Used to show you matching tasks first", MarketSettingsUiPolicy.SKILLS_SUBTITLE)
        assertEquals(listOf("Design", "Typing", "Canva"), MarketSettingsUiPolicy.defaultSkills)
        assertEquals(listOf("Blocked users", "Help and safety", "My reports"), listOf(
            MarketSettingsUiPolicy.BLOCKED_USERS,
            MarketSettingsUiPolicy.HELP_AND_SAFETY,
            MarketSettingsUiPolicy.MY_REPORTS
        ))
        assertTrue(MarketSettingsUiPolicy.hasReferenceContent())
    }

    @Test
    fun alertNamesAndDefaultStatesMatchReference() {
        assertEquals(
            listOf("New proposals on my tasks", "Proposal updates",
                "Messages from U2U team", "New tasks matching my skills"),
            MarketSettingsUiPolicy.alerts.map { it.label }
        )
        assertEquals(listOf(true, true, true, false),
            MarketSettingsUiPolicy.alerts.map { it.initialEnabled })
    }

    @Test
    fun skillPickerDoesNotOfferDuplicatesOrUnknownSkills() {
        assertEquals(
            listOf("Photoshop", "Coding", "Tutoring", "Notes", "Proofreading", "Research", "Presentation"),
            MarketSettingsUiPolicy.availableToAdd(listOf("Design", "Typing", "Canva"))
        )
        assertTrue(MarketSettingsUiPolicy.canAddSkill(listOf("Design"), "Coding"))
        assertFalse(MarketSettingsUiPolicy.canAddSkill(listOf("Design"), "Design"))
        assertFalse(MarketSettingsUiPolicy.canAddSkill(listOf("Design"), "Unknown"))
        assertTrue(MarketSettingsUiPolicy.availableToAdd(MarketSettingsUiPolicy.availableSkills).isEmpty())
    }
}
