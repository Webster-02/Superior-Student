package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp

/** Wire enums mirror values currently accepted by firestore.rules. */
interface MarketWireValue {
    val wireValue: String
}

inline fun <reified T> marketWireEnum(value: String): T?
    where T : Enum<T>, T : MarketWireValue =
    enumValues<T>().firstOrNull { it.wireValue == value }

enum class MarketProfileStatus(override val wireValue: String) : MarketWireValue {
    ACTIVE("active"), SUSPENDED("suspended"), BANNED("banned")
}

enum class MarketModerationStatus(override val wireValue: String) : MarketWireValue {
    OK("ok"), UNDER_REVIEW("under_review"), HIDDEN("hidden"), REMOVED("removed")
}

enum class MarketTaskStatus(override val wireValue: String) : MarketWireValue {
    OPEN("open"), ASSIGNED("assigned"), COMPLETED("completed"), CANCELLED("cancelled"), EXPIRED("expired")
}

enum class MarketBudgetType(override val wireValue: String) : MarketWireValue {
    FREE("free"), FIXED("fixed"), NEGOTIABLE("negotiable")
}

enum class MarketProposalStatus(override val wireValue: String) : MarketWireValue {
    SENT("sent"), SHORTLISTED("shortlisted"), DECLINED("declined"), ACCEPTED("accepted"), WITHDRAWN("withdrawn")
}

enum class MarketOfferStatus(override val wireValue: String) : MarketWireValue {
    ACTIVE("active"), PAUSED("paused"), EXPIRED("expired"), REMOVED("removed")
}

enum class MarketPriceType(override val wireValue: String) : MarketWireValue {
    FREE("free"), SWAP("swap"), AMOUNT("amount")
}

enum class MarketListingKind(override val wireValue: String) : MarketWireValue {
    TASK("task"), OFFER("offer")
}

enum class MarketReportTargetType(override val wireValue: String) : MarketWireValue {
    TASK("task"), OFFER("offer"), USER("user")
}

enum class MarketReportReason(override val wireValue: String) : MarketWireValue {
    GRADED_WORK("graded_work"), SCAM("scam"), SPAM("spam"), ABUSIVE("abusive"),
    ERP_LOGIN("erp_login"), OTHER("other")
}

enum class MarketReportStatus(override val wireValue: String) : MarketWireValue {
    OPEN("open"), UNDER_REVIEW("under_review"), ACTION_TAKEN("action_taken"), DISMISSED("dismissed")
}

enum class MarketNoticeType(override val wireValue: String) : MarketWireValue {
    WARNING("warning"), REMOVAL("removal"), SUPPORT_REPLY("support_reply"),
    APPEAL_RESULT("appeal_result"), ANNOUNCEMENT("announcement"), ACTIVITY("activity")
}

enum class MarketSupportType(override val wireValue: String) : MarketWireValue {
    LISTING("listing"), ACCOUNT("account"), BUG("bug"), PERSON("person"), OTHER("other"), APPEAL("appeal")
}

enum class MarketSupportStatus(override val wireValue: String) : MarketWireValue {
    OPEN("open"), REPLIED("replied"), RESOLVED("resolved")
}

/** Nested profile mutation marker. Only targetType is optional in the rules contract. */
data class MarketLastMutation(
    val kind: String,
    val operation: String,
    val targetId: String,
    val at: Timestamp,
    val targetType: String? = null
)

data class MarketProfileSettings(
    val alertProposals: Boolean,
    val alertUpdates: Boolean,
    val alertNotices: Boolean,
    val alertMatchingTasks: Boolean
)

data class MarketProfile(
    val uid: String,
    val skillTags: List<String>,
    val blockedUids: List<String>,
    val status: MarketProfileStatus,
    val suspendedUntil: Timestamp?,
    val warnings: Int,
    val settings: MarketProfileSettings,
    val openTasks: Int,
    val activeOffers: Int,
    val proposalWindowCount: Int,
    val proposalWindowStartedAt: Timestamp?,
    val reportWindowCount: Int,
    val reportWindowStartedAt: Timestamp?,
    val openSupportTickets: Int,
    val lastMutation: MarketLastMutation?,
    val createdAt: Timestamp,
    val updatedAt: Timestamp
)

data class MarketTask(
    val id: String,
    val title: String,
    val category: String,
    val courseTag: String?,
    val description: String,
    val budgetType: MarketBudgetType,
    val budgetAmount: Double?,
    val deadline: Timestamp,
    val skills: List<String>,
    val posterId: String,
    val posterName: String,
    val posterInitials: String,
    val status: MarketTaskStatus,
    val moderation: MarketModerationStatus,
    val pinned: Boolean,
    val proposalCount: Int,
    val reportCount: Int,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val expiresAt: Timestamp,
    val assignedTo: String? = null
)

data class MarketProposal(
    /** Firestore document ID; rules require it to equal proposerId. */
    val id: String,
    /** Parent task path; not stored as a field inside the proposal document. */
    val taskId: String,
    val proposerId: String,
    val message: String,
    val price: Double,
    val deliveryDays: Int,
    val status: MarketProposalStatus,
    val proposerName: String,
    val proposerInitials: String,
    val createdAt: Timestamp,
    val updatedAt: Timestamp
)

data class MarketOffer(
    val id: String,
    val title: String,
    val category: String,
    val courseTag: String?,
    val description: String,
    val priceType: MarketPriceType,
    val priceAmount: Double?,
    val thumbnail: String?,
    val sellerId: String,
    val sellerName: String,
    val sellerInitials: String,
    val status: MarketOfferStatus,
    val moderation: MarketModerationStatus,
    val pinned: Boolean,
    val reportCount: Int,
    val createdAt: Timestamp,
    val renewedAt: Timestamp,
    val updatedAt: Timestamp,
    val expiresAt: Timestamp
)

data class MarketConversationLastMessage(
    val text: String,
    val senderUid: String,
    val at: Timestamp
)

data class MarketConversation(
    val id: String,
    val participants: List<String>,
    val kind: MarketListingKind,
    val listingId: String,
    val listingOwnerUid: String,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val taskId: String? = null,
    val offerId: String? = null,
    val lastMessage: MarketConversationLastMessage? = null,
    val lastReadAt: Map<String, Timestamp> = emptyMap()
)

data class MarketConversationMessage(
    val id: String,
    /** Parent conversation path; not stored inside the message document. */
    val conversationId: String,
    val senderUid: String,
    val recipientUid: String,
    val senderName: String,
    val text: String,
    val createdAt: Timestamp,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null
)

data class MarketReport(
    val id: String,
    val targetType: MarketReportTargetType,
    val targetId: String,
    val reporterId: String,
    val reason: MarketReportReason,
    val createdAt: Timestamp,
    val status: MarketReportStatus
)

data class MarketRating(
    val id: String,
    val taskId: String,
    val fromUid: String,
    val toUid: String,
    val stars: Int,
    val tags: List<String>,
    val comment: String,
    val createdAt: Timestamp
)

data class MarketUserStats(
    /** Firestore document ID; userStats documents do not store a uid field. */
    val uid: String,
    val ratingSum: Double,
    val ratingCount: Int,
    val completedCount: Int,
    val lastMutation: MarketLastMutation,
    val updatedAt: Timestamp
)

data class MarketNotice(
    val id: String,
    /** UID taken from users/{uid}/notices/{noticeId}; not a stored field. */
    val uid: String,
    val type: MarketNoticeType,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: Timestamp,
    val canAppeal: Boolean,
    val appealed: Boolean,
    val relatedType: String? = null,
    val relatedId: String? = null
)

/**
 * Extra fields are retained when present because the rules validate reply essentials
 * but do not impose a keys.hasOnly() allow-list on each reply map.
 */
data class MarketSupportReply(
    val from: String,
    val text: String,
    val at: Timestamp,
    val extraFields: Map<String, Any?> = emptyMap()
)

data class MarketSupportTicket(
    val id: String,
    val userId: String,
    val type: MarketSupportType,
    val subject: String,
    val body: String,
    val status: MarketSupportStatus,
    val replies: List<MarketSupportReply>,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val relatedType: String? = null,
    val relatedId: String? = null
)

data class MarketAdminLog(
    val id: String,
    val adminId: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val note: String,
    val createdAt: Timestamp
)

data class MarketAnnouncement(
    val text: String,
    val active: Boolean,
    val until: Timestamp?
)

data class MarketRankWeights(
    val newness: Double,
    val rating: Double,
    val match: Double
)

/**
 * The rules fix the five known limits but do not have a nested keys.hasOnly()
 * check for the limits map, so unknown existing entries are preserved on read.
 */
data class MarketLimits(
    val openTasks: Int,
    val proposalsPerDay: Int,
    val proposalsPerTask: Int,
    val activeOffers: Int,
    val reportsToHide: Int,
    val extraFields: Map<String, Any?> = emptyMap()
)

data class MarketConfig(
    val announcement: MarketAnnouncement,
    val blockedWords: List<String>,
    val rankWeights: MarketRankWeights,
    val limits: MarketLimits
)
