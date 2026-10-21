package com.superiorstudent.app.feature.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketQueryBuildersTest {

    @Test
    fun publicTaskFeedAlwaysConstrainsModerationAndStatus() {
        val plan = MarketQueryBuilders.publicTasksPlan(category = "Biology", pinnedFirst = true)

        assertEquals("tasks", plan.collection)
        assertEquals(
            listOf(
                MarketQueryFilter("category", MarketQueryOperator.EQUALS, "Biology"),
                MarketQueryFilter("moderation", MarketQueryOperator.EQUALS, "ok"),
                MarketQueryFilter("status", MarketQueryOperator.EQUALS, "open")
            ),
            plan.filters
        )
        assertEquals(
            listOf(
                MarketQueryOrder("pinned", descending = true),
                MarketQueryOrder("createdAt", descending = true)
            ),
            plan.ordering
        )
        assertEquals(MarketQueryBuilders.DEFAULT_PAGE_SIZE, plan.limit)
    }

    @Test
    fun publicTaskFeedCanUseNewestOnlyIndexAndIgnoresBlankCategory() {
        val plan = MarketQueryBuilders.publicTasksPlan(category = "  ", pinnedFirst = false, pageSize = 12)

        assertEquals(
            listOf(
                MarketQueryFilter("moderation", MarketQueryOperator.EQUALS, "ok"),
                MarketQueryFilter("status", MarketQueryOperator.EQUALS, "open")
            ),
            plan.filters
        )
        assertEquals(listOf(MarketQueryOrder("createdAt", descending = true)), plan.ordering)
        assertEquals(12, plan.limit)
    }

    @Test
    fun publicOfferFeedAlwaysConstrainsModerationAndStatus() {
        val plan = MarketQueryBuilders.publicOffersPlan(category = null, pinnedFirst = true)

        assertEquals("offers", plan.collection)
        assertEquals(
            listOf(
                MarketQueryFilter("moderation", MarketQueryOperator.EQUALS, "ok"),
                MarketQueryFilter("status", MarketQueryOperator.EQUALS, "active")
            ),
            plan.filters
        )
        assertEquals(
            listOf(
                MarketQueryOrder("pinned", descending = true),
                MarketQueryOrder("renewedAt", descending = true)
            ),
            plan.ordering
        )
    }

    @Test
    fun myTasksAlwaysScopeToPosterAndCanOptionallyFilterStatus() {
        val plan = MarketQueryBuilders.myTasksPlan("student-123", MarketTaskStatus.OPEN)

        assertEquals("tasks", plan.collection)
        assertEquals("student-123", plan.filters.first { it.field == "posterId" }.value)
        assertEquals("open", plan.filters.first { it.field == "status" }.value)
        assertEquals(
            listOf(
                MarketQueryFilter("posterId", MarketQueryOperator.EQUALS, "student-123"),
                MarketQueryFilter("status", MarketQueryOperator.EQUALS, "open")
            ),
            plan.filters
        )
        assertEquals(listOf(MarketQueryOrder("createdAt", descending = true)), plan.ordering)
    }

    @Test
    fun myOffersAlwaysScopeToSellerAndOrderByRenewalTime() {
        val plan = MarketQueryBuilders.myOffersPlan("seller-456", MarketOfferStatus.PAUSED)

        assertEquals("offers", plan.collection)
        assertEquals(
            listOf(
                MarketQueryFilter("sellerId", MarketQueryOperator.EQUALS, "seller-456"),
                MarketQueryFilter("status", MarketQueryOperator.EQUALS, "paused")
            ),
            plan.filters
        )
        assertEquals(listOf(MarketQueryOrder("renewedAt", descending = true)), plan.ordering)
    }

    @Test
    fun ownerQueriesRejectBlankUidAndAllQueriesRejectInvalidPageSizes() {
        assertThrows(IllegalArgumentException::class.java) {
            MarketQueryBuilders.myTasksPlan(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarketQueryBuilders.myOffersPlan("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarketQueryBuilders.publicTasksPlan(pageSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarketQueryBuilders.publicOffersPlan(pageSize = 51)
        }
    }

    @Test
    fun queryPlanMetadataDoesNotDisablePublicConstraints() {
        val taskPlan = MarketQueryBuilders.publicTasksPlan(pinnedFirst = false)
        val offerPlan = MarketQueryBuilders.publicOffersPlan(pinnedFirst = false)

        assertTrue(taskPlan.filters.any { it.field == "moderation" && it.value == "ok" })
        assertTrue(taskPlan.filters.any { it.field == "status" && it.value == "open" })
        assertTrue(offerPlan.filters.any { it.field == "moderation" && it.value == "ok" })
        assertTrue(offerPlan.filters.any { it.field == "status" && it.value == "active" })
        assertFalse(taskPlan.collectionGroup)
        assertFalse(offerPlan.collectionGroup)
    }

    @Test
    fun appliedProposalsUsesCollectionGroupAndMandatoryOwnUidFilter() {
        val uid = "student-789"
        val plan = MarketQueryBuilders.appliedProposalsPlan(uid)

        assertEquals("proposals", plan.collection)
        assertTrue(plan.collectionGroup)
        assertEquals(
            listOf(MarketQueryFilter("proposerId", MarketQueryOperator.EQUALS, uid)),
            plan.filters
        )
        assertEquals(
            listOf(MarketQueryOrder("updatedAt", descending = true)),
            plan.ordering
        )
        assertEquals(20, plan.limit)
    }

    @Test
    fun appliedProposalsRejectsBlankUid() {
        assertThrows(IllegalArgumentException::class.java) {
            MarketQueryBuilders.appliedProposalsPlan(" ")
        }
    }
}
