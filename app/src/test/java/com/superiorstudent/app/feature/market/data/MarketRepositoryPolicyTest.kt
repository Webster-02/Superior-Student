package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class MarketRepositoryPolicyTest {
    @Test
    fun clientTimestampHasThirtySecondSafetyMargin() {
        val now = 1_800_000_000_000L
        assertEquals(now - 30_000L, MarketRepositoryPolicy.safeClientTimestamp(now).toDate().time)
        assertEquals(0L, MarketRepositoryPolicy.safeClientTimestamp(10_000L).toDate().time)
    }

    @Test
    fun taskAndOfferExpiryAreDerivedFromTheirExactClientTimestamp() {
        val createdAt = Timestamp(Date(1_800_000_000_000L))
        assertEquals(
            createdAt.toDate().time + 14L * MarketRepositoryPolicy.DAY_MS,
            MarketRepositoryPolicy.taskExpiry(createdAt).toDate().time
        )
        assertEquals(
            createdAt.toDate().time + 30L * MarketRepositoryPolicy.DAY_MS,
            MarketRepositoryPolicy.offerExpiry(createdAt).toDate().time
        )
    }

    @Test
    fun deterministicIdentifiersSortConversationParticipants() {
        val first = MarketRepositoryPolicy.conversationId(MarketListingKind.TASK, "task123", "zulu", "alpha")
        val second = MarketRepositoryPolicy.conversationId(MarketListingKind.TASK, "task123", "alpha", "zulu")
        assertEquals("task_task123_alpha_zulu", first)
        assertEquals(first, second)
        assertEquals("offer_offerABC_alpha_zulu",
            MarketRepositoryPolicy.conversationId(MarketListingKind.OFFER, "offerABC", "alpha", "zulu"))
    }

    @Test
    fun initialsAreBoundedAndIdsMatchRuleShapes() {
        assertEquals("AK", MarketRepositoryPolicy.initials("Awais Khan"))
        assertTrue(MarketRepositoryPolicy.initials("One Two Three Four Five").length <= 4)
        assertEquals("task_task123_reporter", MarketRepositoryPolicy.reportId(
            MarketReportTargetType.TASK, "task123", "reporter"))
        assertEquals("task123_rater", MarketRepositoryPolicy.ratingId("task123", "rater"))
        assertEquals("appeal_notice123_user1", MarketRepositoryPolicy.appealTicketId("notice123", "user1"))
    }
}
