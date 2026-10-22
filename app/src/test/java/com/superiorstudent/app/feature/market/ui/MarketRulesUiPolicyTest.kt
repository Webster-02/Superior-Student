package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRulesUiPolicyTest {
    @Test
    fun headingsAndReferenceCopyMatchScreen28() {
        assertEquals("Rules and safety", MarketRulesUiPolicy.TITLE)
        assertEquals("Keep the market useful", MarketRulesUiPolicy.SUBTITLE)
        assertEquals("Allowed", MarketRulesUiPolicy.ALLOWED_HEADING)
        assertEquals("Not allowed", MarketRulesUiPolicy.NOT_ALLOWED_HEADING)
        assertEquals("Stay safe", MarketRulesUiPolicy.STAY_SAFE_HEADING)
        assertEquals("If rules are broken", MarketRulesUiPolicy.BROKEN_RULES_HEADING)
        assertEquals(
            "Ask for a sample first. Pay in parts. Meet on campus. U2U never handles money, so keep proof of agreement in chat.",
            MarketRulesUiPolicy.STAY_SAFE_COPY
        )
        assertTrue(MarketRulesUiPolicy.hasReferenceContent())
    }

    @Test
    fun allowedAndProhibitedListsMatchReference() {
        assertEquals(
            listOf(
                "Notes, tutoring, explaining a topic",
                "Project help, design, typing, formatting",
                "Proofreading and coding help (explain, review)"
            ),
            MarketRulesUiPolicy.allowedItems
        )
        assertEquals(
            listOf(
                "Doing someone else's graded assignment, quiz or exam",
                "Using someone's ERP login or sharing your password",
                "Asking for full payment before work is shown",
                "Spam, fake offers or abusive messages"
            ),
            MarketRulesUiPolicy.prohibitedItems
        )
    }

    @Test
    fun enforcementStepsKeepReferenceOrderAndTone() {
        assertEquals(
            listOf("1:Warning", "2:7 days", "3:30 days", "4:Ban"),
            MarketRulesUiPolicy.enforcementSteps.map { "${it.number}:${it.label}" }
        )
        assertEquals(
            listOf(
                MarketRulesPenaltyTone.INFO,
                MarketRulesPenaltyTone.WARNING,
                MarketRulesPenaltyTone.PEACH,
                MarketRulesPenaltyTone.DANGER
            ),
            MarketRulesUiPolicy.enforcementSteps.map { it.tone }
        )
    }
}
