package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTaskPosterUiPolicyTest {
    @Test
    fun posterReferenceHasExpectedHeaderAndOwnerStatus() {
        val model = MarketTaskPosterUiPolicy.reference
        assertEquals("Your task", "Your task")
        assertEquals("Open", model.status)
        assertEquals("Hamza Ali", model.author)
        assertEquals("4.8 · Verified student", model.rating)
    }

    @Test
    fun posterReferenceMatchesThreeMetricCardsAndProposalSummary() {
        val model = MarketTaskPosterUiPolicy.reference
        assertEquals("4 of 15", MarketTaskPosterUiPolicy.proposalCapacityLabel(model))
        assertEquals("1 shortlisted", MarketTaskPosterUiPolicy.shortlistedLabel(model))
        assertEquals("Rs. 1,500", model.budget)
        assertEquals("24 Oct", model.deadline)
    }

    @Test
    fun posterReferenceShowsReferenceDetailsAndSkills() {
        val model = MarketTaskPosterUiPolicy.reference
        assertTrue(model.details.contains("health camp on 28 Oct"))
        assertEquals(listOf("Canva", "Photoshop", "Layout"), model.skillTags)
    }
}
