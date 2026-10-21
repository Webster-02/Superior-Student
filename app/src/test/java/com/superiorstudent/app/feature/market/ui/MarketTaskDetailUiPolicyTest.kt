package com.superiorstudent.app.feature.market.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTaskDetailUiPolicyTest {
    @Test
    fun designReferenceUsesExactBudgetDeadlineProposalAndTags() {
        val model = MarketTaskDetailUiPolicy.create(
            MarketTaskDetailSeed(
                id = "reference-design",
                category = "Design",
                courseTag = "BSOPM",
                title = "Poster for Optometry Society health camp",
                description = "Short list preview",
                budgetLabel = "Rs. 1,500",
                dueLabel = "Due in 3 days",
                proposalCount = 4,
                author = "Hamza Ali",
                initials = "HA",
                ageLabel = "· 2h ago"
            )
        )

        assertEquals("Rs. 1,500", model.budgetValue)
        assertEquals("fixed", model.budgetFootnote)
        assertEquals("24 Oct", model.deadlineDate)
        assertEquals("3 days left", model.deadlineFootnote)
        assertEquals("4 of 15", model.proposalsValue)
        assertEquals(listOf("Canva", "Photoshop", "Layout"), model.skillTags)
        assertTrue(model.details.contains("health camp on 28 Oct"))
    }

    @Test
    fun mapsCategoryAndCourseAndRemovesAgePrefix() {
        val model = MarketTaskDetailUiPolicy.create(
            MarketTaskDetailSeed(
                id = "other-task",
                category = "Coding",
                courseTag = "CS101",
                title = "Debug the loop",
                description = "Explain the loop error",
                budgetLabel = "Free help",
                dueLabel = "Due tomorrow",
                proposalCount = 7,
                author = "Bilal Raza",
                initials = "BR",
                ageLabel = "· 1d ago"
            )
        )

        assertEquals("Coding", model.category)
        assertEquals("CS101", model.courseTag)
        assertEquals("1d ago", model.ageLabel)
        assertEquals("Tomorrow", model.deadlineDate)
        assertEquals("1 day left", model.deadlineFootnote)
        assertTrue(model.skillTags.contains("Python"))
    }

    @Test
    fun lockNoticeIsConsistentAndProposalCountIsNonNegative() {
        val model = MarketTaskDetailUiPolicy.create(
            MarketTaskDetailSeed(
                id = "invalid-proposal-count",
                category = "Notes",
                courseTag = null,
                title = "Anatomy notes",
                description = "Clear notes",
                budgetLabel = "Negotiable",
                dueLabel = "",
                proposalCount = -4,
                author = "Sara Ahmed",
                initials = "SA",
                ageLabel = ""
            )
        )

        assertEquals("0 of 15", model.proposalsValue)
        assertEquals("Recently", model.ageLabel)
        assertTrue(model.contactLockMessage.contains("accepts your proposal"))
        assertFalse(model.courseTag != null)
    }
}
