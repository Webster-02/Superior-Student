package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketConvertersTest {

    private val now = Timestamp(1_800_000_000L, 0)

    @Test
    fun profileCodecUsesExactSeventeenFieldShape() {
        val data = linkedMapOf<String, Any?>(
            "uid" to "uid1",
            "skillTags" to listOf("math"),
            "blockedUids" to listOf("uid2"),
            "status" to "active",
            "suspendedUntil" to null,
            "warnings" to 0,
            "settings" to mapOf(
                "alertProposals" to true,
                "alertUpdates" to true,
                "alertNotices" to true,
                "alertMatchingTasks" to false
            ),
            "openTasks" to 0,
            "activeOffers" to 0,
            "proposalWindowCount" to 0,
            "proposalWindowStartedAt" to null,
            "reportWindowCount" to 0,
            "reportWindowStartedAt" to null,
            "openSupportTickets" to 0,
            "lastMutation" to null,
            "createdAt" to now,
            "updatedAt" to now
        )

        assertEquals(17, data.size)
        val profile = MarketConverters.profile("uid1", data)
        assertNotNull(profile)
        assertEquals(listOf("uid2"), profile!!.blockedUids)
        assertEquals(false, profile.settings.alertMatchingTasks)
        assertEquals(data.keys, MarketConverters.toFirestoreMap(profile).keys)

        val extraField = data.toMutableMap().apply { put("displayName", "Not a Market field") }
        assertNull(MarketConverters.profile("uid1", extraField))
    }

    @Test
    fun taskCodecAcceptsRuleAllowedOptionalFieldsAndRejectsMalformedOptionalValues() {
        val data = taskData().apply {
            put("courseTag", "Biology")
            put("budgetAmount", 0.0)
            put("lastAuditLogId", "audit-task-1")
            put("lastAdminNoticeId", "notice-task-1")
        }
        val task = MarketConverters.task("task1", data)
        assertNotNull(task)
        assertEquals("Biology", task!!.courseTag)
        assertEquals(0.0, task.budgetAmount!!, 0.0)

        val encoded = MarketConverters.toFirestoreMap(task)
        assertTrue(encoded.containsKey("courseTag"))
        assertTrue(encoded.containsKey("budgetAmount"))
        assertFalse(encoded.containsKey("id"))

        val malformedCourseTag = taskData().apply { put("courseTag", 42) }
        assertNull(MarketConverters.task("task1", malformedCourseTag))

        val malformedBudget = taskData().apply { put("budgetAmount", "free") }
        assertNull(MarketConverters.task("task1", malformedBudget))
        assertNull(MarketConverters.task("task1", taskData().apply { put("lastAuditLogId", "") }))
        assertNull(MarketConverters.task("task1", taskData().apply { put("lastAdminNoticeId", 17) }))

        assertNull(MarketConverters.task("task1", taskData().apply { put("unexpected", true) }))
    }

    @Test
    fun offerCodecAcceptsOptionalAdminAuditPointersButRejectsMalformedPointers() {
        val data = linkedMapOf<String, Any?>(
            "title" to "Biology study notes",
            "category" to "Study materials",
            "description" to "Carefully organised biology notes for the semester.",
            "priceType" to "free",
            "sellerId" to "alice",
            "sellerName" to "Alice",
            "sellerInitials" to "AL",
            "status" to "active",
            "moderation" to "ok",
            "pinned" to false,
            "reportCount" to 0,
            "createdAt" to now,
            "renewedAt" to now,
            "updatedAt" to now,
            "expiresAt" to Timestamp(now.seconds + 30L * 24 * 60 * 60, now.nanoseconds),
            "lastAuditLogId" to "audit-offer-1",
            "lastAdminNoticeId" to "notice-offer-1"
        )
        assertNotNull(MarketConverters.offer("offer1", data))
        assertNull(MarketConverters.offer("offer1", data + ("lastAuditLogId" to "")))
        assertNull(MarketConverters.offer("offer1", data + ("lastAdminNoticeId" to 17)))
    }

    @Test
    fun proposalDocumentIdMustMatchImmutableProposerId() {
        val data = mapOf<String, Any?>(
            "proposerId" to "helper1",
            "message" to "I can complete this work carefully.",
            "price" to 125.0,
            "deliveryDays" to 3,
            "status" to "sent",
            "proposerName" to "Helper One",
            "proposerInitials" to "HO",
            "createdAt" to now,
            "updatedAt" to now
        )
        val proposal = MarketConverters.proposal("helper1", "task1", data)
        assertNotNull(proposal)
        assertEquals("task1", proposal!!.taskId)
        assertNull(MarketConverters.proposal("anotherUid", "task1", data))
    }

    @Test
    fun conversationCodecUsesSortedDeterministicIdAndPathIdsStayOutOfDocument() {
        val conversation = MarketConversation(
            id = "task_task1_alpha_zulu",
            participants = listOf("alpha", "zulu"),
            kind = MarketListingKind.TASK,
            listingId = "task1",
            listingOwnerUid = "alpha",
            createdAt = now,
            updatedAt = now,
            taskId = "task1"
        )
        val encoded = MarketConverters.toFirestoreMap(conversation)
        assertTrue(encoded.keys.containsAll(setOf("participants", "kind", "listingId", "listingOwnerUid", "taskId", "createdAt", "updatedAt")))
        assertFalse(encoded.containsKey("id"))
        assertFalse(encoded.containsKey("conversationId"))
        assertEquals(conversation, MarketConverters.conversation(conversation.id, encoded))

        assertNull(
            MarketConverters.conversation(
                conversation.id,
                encoded + ("participants" to listOf("zulu", "alpha"))
            )
        )
    }

    @Test
    fun reportIdAndAllowedValuesAreChecked() {
        val data = mapOf<String, Any?>(
            "targetType" to "task",
            "targetId" to "task1",
            "reporterId" to "reporter1",
            "reason" to "scam",
            "createdAt" to now,
            "status" to "open"
        )
        assertNotNull(MarketConverters.report("task_task1_reporter1", data))
        assertNotNull(MarketConverters.report(
            "task_task1_reporter1",
            data + ("lastAuditLogId" to "report-log-1") + ("lastAdminNoticeId" to "report-notice-1")
        ))
        assertNull(MarketConverters.report(
            "task_task1_reporter1", data + ("lastAuditLogId" to "")
        ))
        assertNull(MarketConverters.report(
            "task_task1_reporter1", data + ("lastAdminNoticeId" to 42)
        ))
        assertNull(MarketConverters.report("wrong_id", data))
        assertNull(MarketConverters.report("task_task1_reporter1", data + ("reason" to "made_up")))
    }

    @Test
    fun configRetainsRuleAllowedExtraLimitEntriesAndRequiresWeightsTotalOneHundred() {
        val data = mapOf<String, Any?>(
            "announcement" to mapOf("text" to "Welcome", "active" to true, "until" to null),
            "blockedWords" to listOf("example"),
            "rankWeights" to mapOf("newness" to 50.0, "rating" to 30.0, "match" to 20.0),
            "limits" to mapOf(
                "openTasks" to 3,
                "proposalsPerDay" to 10,
                "proposalsPerTask" to 15,
                "activeOffers" to 5,
                "reportsToHide" to 3,
                "serverOwnedEntry" to "preserve"
            )
        )
        val config = MarketConverters.config(data)
        assertNotNull(config)
        assertEquals("preserve", config!!.limits.extraFields["serverOwnedEntry"])
        assertEquals(config, MarketConverters.config(MarketConverters.toFirestoreMap(config)))
        assertEquals(config, MarketConverters.config(data + ("lastAuditLogId" to "config-log-1")))
        assertNull(MarketConverters.config(data + ("lastAuditLogId" to "")))
        assertNull(MarketConverters.config(data + ("lastAuditLogId" to 42)))

        val badWeights = data.toMutableMap().apply {
            put("rankWeights", mapOf("newness" to 50.0, "rating" to 30.0, "match" to 19.0))
        }
        assertNull(MarketConverters.config(badWeights))
    }

    private fun taskData(): MutableMap<String, Any?> = linkedMapOf(
        "title" to "Need assignment help",
        "category" to "Academic",
        "description" to "Please help explain the topic and review my draft.",
        "budgetType" to "fixed",
        "budgetAmount" to 0.0,
        "deadline" to now,
        "skills" to listOf("Writing"),
        "posterId" to "poster1",
        "posterName" to "Poster One",
        "posterInitials" to "PO",
        "status" to "open",
        "moderation" to "ok",
        "pinned" to false,
        "proposalCount" to 0,
        "reportCount" to 0,
        "createdAt" to now,
        "updatedAt" to now,
        "expiresAt" to Timestamp(now.seconds + 14L * 24 * 60 * 60, now.nanoseconds)
    )
}
