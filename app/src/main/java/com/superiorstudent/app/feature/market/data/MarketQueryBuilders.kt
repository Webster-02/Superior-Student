package com.superiorstudent.app.feature.market.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp

/**
 * Rule-aware read-query builders for the student Market feeds and owner listing pages.
 *
 * These filters make student queries provably compatible with Firestore list rules.
 * They are not an authorization boundary: firestore.rules remains authoritative.
 */
object MarketQueryBuilders {
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_DELTA_PAGE_SIZE = 50
    private const val MAX_PAGE_SIZE = 50

    /**
     * Public task feed. Public queries must constrain BOTH moderation=ok and status=open.
     * The optional category and ordering variants match INDEX_QUERY_MAP.md.
     */
    fun publicTasks(
        firestore: FirebaseFirestore,
        category: String? = null,
        pinnedFirst: Boolean = true,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        startAfter: DocumentSnapshot? = null
    ): Query {
        val query = execute(
            firestore,
            publicTasksPlan(category = category, pinnedFirst = pinnedFirst, pageSize = pageSize)
        )
        return startAfter?.let { query.startAfter(it) } ?: query
    }

    /**
     * Public offer feed. Public queries must constrain BOTH moderation=ok and status=active.
     * The optional category and ordering variants match INDEX_QUERY_MAP.md.
     */
    fun publicOffers(
        firestore: FirebaseFirestore,
        category: String? = null,
        pinnedFirst: Boolean = true,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        startAfter: DocumentSnapshot? = null
    ): Query {
        val query = execute(
            firestore,
            publicOffersPlan(category = category, pinnedFirst = pinnedFirst, pageSize = pageSize)
        )
        return startAfter?.let { query.startAfter(it) } ?: query
    }

    /**
     * Incremental task-feed refresh. The public state constraints remain mandatory.
     * This uses the indexed (moderation, status, updatedAt DESC) query variant and is
     * intended only for an all-category cached feed; category-specific queries have
     * separate indexes and must use [publicTasks].
     */
    fun publicTasksUpdatedSince(
        firestore: FirebaseFirestore,
        since: Timestamp,
        pageSize: Int = MAX_DELTA_PAGE_SIZE,
        startAfter: DocumentSnapshot? = null
    ): Query {
        var query = firestore.collection("tasks")
            .whereEqualTo("moderation", MarketModerationStatus.OK.wireValue)
            .whereEqualTo("status", MarketTaskStatus.OPEN.wireValue)
            .whereGreaterThan("updatedAt", since)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
        startAfter?.let { query = query.startAfter(it) }
        return query.limit(validatedPageSize(pageSize).toLong())
    }

    /**
     * Incremental offer-feed refresh. The query follows the indexed public
     * (moderation, status, updatedAt DESC) variant and must not be used for
     * category-specific refreshes.
     */
    fun publicOffersUpdatedSince(
        firestore: FirebaseFirestore,
        since: Timestamp,
        pageSize: Int = MAX_DELTA_PAGE_SIZE,
        startAfter: DocumentSnapshot? = null
    ): Query {
        var query = firestore.collection("offers")
            .whereEqualTo("moderation", MarketModerationStatus.OK.wireValue)
            .whereEqualTo("status", MarketOfferStatus.ACTIVE.wireValue)
            .whereGreaterThan("updatedAt", since)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
        startAfter?.let { query = query.startAfter(it) }
        return query.limit(validatedPageSize(pageSize).toLong())
    }

    /**
     * Owner-scoped task list. The posterId filter is mandatory even when status is omitted.
     */
    fun myTasks(
        firestore: FirebaseFirestore,
        uid: String,
        status: MarketTaskStatus? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Query = execute(
        firestore,
        myTasksPlan(uid = uid, status = status, pageSize = pageSize)
    )

    /**
     * Owner-scoped offer list. The sellerId filter is mandatory even when status is omitted.
     */
    fun myOffers(
        firestore: FirebaseFirestore,
        uid: String,
        status: MarketOfferStatus? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Query = execute(
        firestore,
        myOffersPlan(uid = uid, status = status, pageSize = pageSize)
    )

    /**
     * Publicly readable aggregate rating stats for one seller. The Rules permit single-user
     * gets for signed-in users; list queries remain separately governed by Rules.
     */
    fun publicUserStats(firestore: FirebaseFirestore, uid: String): DocumentReference {
        requireValidUid(uid)
        return firestore.collection("userStats").document(uid)
    }

    /**
     * Applied proposals across task subcollections. The proposerId filter is mandatory
     * for Firestore Rules access to the collection-group query. Call with the authenticated UID.
     * The required composite index orders these results by updatedAt descending.
     */
    fun appliedProposals(
        firestore: FirebaseFirestore,
        uid: String
    ): Query = execute(firestore, appliedProposalsPlan(uid))

    /**
     * Market Chats for the current verified user.
     *
     * The participants array-contains filter is mandatory for participant-only
     * list access; callers must pass the authenticated user's UID.
     */
    fun marketChats(
        firestore: FirebaseFirestore,
        uid: String
    ): Query = execute(firestore, marketChatsPlan(uid))

    internal fun marketChatsPlan(uid: String): MarketQueryPlan {
        requireValidUid(uid)
        return MarketQueryPlan(
            collection = "marketConversations",
            filters = listOf(contains("participants", uid)),
            ordering = listOf(descending("updatedAt")),
            limit = DEFAULT_PAGE_SIZE
        )
    }

    /**
     * Proposals under one task. Firestore Rules scope this nested collection to the task poster.
     */
    fun taskProposals(
        firestore: FirebaseFirestore,
        taskId: String,
        status: MarketProposalStatus? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Query = execute(
        firestore,
        taskProposalsPlan(taskId = taskId, status = status, pageSize = pageSize)
    )

    internal fun taskProposalsPlan(
        taskId: String,
        status: MarketProposalStatus? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): MarketQueryPlan {
        requireValidDocumentId(taskId, "task")
        val filters = buildList {
            status?.let { add(equals("status", it.wireValue)) }
        }
        return MarketQueryPlan(
            collection = "proposals",
            filters = filters,
            ordering = listOf(descending("createdAt")),
            limit = validatedPageSize(pageSize),
            collectionPath = "tasks/$taskId/proposals"
        )
    }

    /** Reports filed by this user only; reporterId is mandatory under Firestore Rules. */
    fun myReports(
        firestore: FirebaseFirestore,
        uid: String,
        targetType: MarketReportTargetType? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Query = execute(
        firestore,
        myReportsPlan(uid = uid, targetType = targetType, pageSize = pageSize)
    )

    internal fun myReportsPlan(
        uid: String,
        targetType: MarketReportTargetType? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): MarketQueryPlan {
        requireValidUid(uid)
        val filters = buildList {
            add(equals("reporterId", uid))
            targetType?.let { add(equals("targetType", it.wireValue)) }
        }
        return MarketQueryPlan(
            collection = "reports",
            filters = filters,
            ordering = listOf(descending("createdAt")),
            limit = validatedPageSize(pageSize)
        )
    }

    /** Notices are read only from this user's own /users/{uid}/notices subcollection. */
    fun myNotices(
        firestore: FirebaseFirestore,
        uid: String,
        read: Boolean? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Query = execute(
        firestore,
        myNoticesPlan(uid = uid, read = read, pageSize = pageSize)
    )

    internal fun myNoticesPlan(
        uid: String,
        read: Boolean? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): MarketQueryPlan {
        requireValidUid(uid)
        val filters = buildList {
            read?.let { add(MarketQueryFilter("read", MarketQueryOperator.EQUALS, it)) }
        }
        return MarketQueryPlan(
            collection = "notices",
            filters = filters,
            ordering = listOf(descending("createdAt")),
            limit = validatedPageSize(pageSize),
            collectionPath = "users/$uid/notices"
        )
    }

    /**
     * Support inbox scoped to one user. Status is required to match the
     * indexed userId/status/updatedAt-descending query shape.
     */
    fun mySupportTickets(
        firestore: FirebaseFirestore,
        uid: String,
        status: MarketSupportStatus,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Query = execute(
        firestore,
        mySupportTicketsPlan(uid = uid, status = status, pageSize = pageSize)
    )

    internal fun mySupportTicketsPlan(
        uid: String,
        status: MarketSupportStatus,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): MarketQueryPlan {
        requireValidUid(uid)
        return MarketQueryPlan(
            collection = "support",
            filters = listOf(
                equals("userId", uid),
                equals("status", status.wireValue)
            ),
            ordering = listOf(descending("updatedAt")),
            limit = validatedPageSize(pageSize)
        )
    }

    private fun requireValidDocumentId(value: String, label: String) {
        require(value.isNotBlank() && value.length <= 128 && !value.contains('/')) {
            "A valid $label document ID is required for this Market query."
        }
    }

    internal fun appliedProposalsPlan(uid: String): MarketQueryPlan {
        requireValidUid(uid)
        return MarketQueryPlan(
            collection = "proposals",
            filters = listOf(equals("proposerId", uid)),
            ordering = listOf(descending("updatedAt")),
            limit = DEFAULT_PAGE_SIZE,
            collectionGroup = true
        )
    }

    internal fun publicTasksPlan(
        category: String? = null,
        pinnedFirst: Boolean = true,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): MarketQueryPlan {
        val filters = buildList {
            normalizedCategory(category)?.let { add(equals("category", it)) }
            add(equals("moderation", MarketModerationStatus.OK.wireValue))
            add(equals("status", MarketTaskStatus.OPEN.wireValue))
        }
        val ordering = if (pinnedFirst) {
            listOf(descending("pinned"), descending("createdAt"))
        } else {
            listOf(descending("createdAt"))
        }
        return MarketQueryPlan(
            collection = "tasks",
            filters = filters,
            ordering = ordering,
            limit = validatedPageSize(pageSize)
        )
    }

    internal fun publicOffersPlan(
        category: String? = null,
        pinnedFirst: Boolean = true,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): MarketQueryPlan {
        val filters = buildList {
            normalizedCategory(category)?.let { add(equals("category", it)) }
            add(equals("moderation", MarketModerationStatus.OK.wireValue))
            add(equals("status", MarketOfferStatus.ACTIVE.wireValue))
        }
        val ordering = if (pinnedFirst) {
            listOf(descending("pinned"), descending("renewedAt"))
        } else {
            listOf(descending("renewedAt"))
        }
        return MarketQueryPlan(
            collection = "offers",
            filters = filters,
            ordering = ordering,
            limit = validatedPageSize(pageSize)
        )
    }

    internal fun myTasksPlan(
        uid: String,
        status: MarketTaskStatus? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): MarketQueryPlan {
        requireValidUid(uid)
        val filters = buildList {
            add(equals("posterId", uid))
            status?.let { add(equals("status", it.wireValue)) }
        }
        return MarketQueryPlan(
            collection = "tasks",
            filters = filters,
            ordering = listOf(descending("createdAt")),
            limit = validatedPageSize(pageSize)
        )
    }

    internal fun myOffersPlan(
        uid: String,
        status: MarketOfferStatus? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): MarketQueryPlan {
        requireValidUid(uid)
        val filters = buildList {
            add(equals("sellerId", uid))
            status?.let { add(equals("status", it.wireValue)) }
        }
        return MarketQueryPlan(
            collection = "offers",
            filters = filters,
            ordering = listOf(descending("renewedAt")),
            limit = validatedPageSize(pageSize)
        )
    }

    private fun execute(firestore: FirebaseFirestore, plan: MarketQueryPlan): Query {
        require(!(plan.collectionGroup && plan.collectionPath != null)) {
            "A Market query cannot target a collection group and a nested collection path simultaneously."
        }
        var query: Query = when {
            plan.collectionGroup -> firestore.collectionGroup(plan.collection)
            plan.collectionPath != null -> firestore.collection(plan.collectionPath)
            else -> firestore.collection(plan.collection)
        }
        plan.filters.forEach { filter ->
            query = when (filter.operator) {
                MarketQueryOperator.EQUALS -> query.whereEqualTo(filter.field, filter.value)
                MarketQueryOperator.ARRAY_CONTAINS -> query.whereArrayContains(filter.field, filter.value)
            }
        }
        plan.ordering.forEach { order ->
            query = query.orderBy(
                order.field,
                if (order.descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING
            )
        }
        return query.limit(plan.limit.toLong())
    }

    private fun normalizedCategory(category: String?): String? =
        category?.trim()?.takeIf { it.isNotEmpty() }

    private fun requireValidUid(uid: String) {
        require(uid.isNotBlank()) { "A signed-in user ID is required for this Market query." }
    }

    private fun validatedPageSize(pageSize: Int): Int {
        require(pageSize in 1..MAX_PAGE_SIZE) { "Market query page size must be between 1 and 50." }
        return pageSize
    }

    private fun equals(field: String, value: String) =
        MarketQueryFilter(field, MarketQueryOperator.EQUALS, value)

    private fun contains(field: String, value: String) =
        MarketQueryFilter(field, MarketQueryOperator.ARRAY_CONTAINS, value)

    private fun descending(field: String) = MarketQueryOrder(field, descending = true)
}

internal enum class MarketQueryOperator {
    EQUALS,
    ARRAY_CONTAINS
}

internal data class MarketQueryFilter(
    val field: String,
    val operator: MarketQueryOperator,
    val value: Any
)

internal data class MarketQueryOrder(
    val field: String,
    val descending: Boolean
)

internal data class MarketQueryPlan(
    /** Collection name, or collection-group name when collectionGroup is true. */
    val collection: String,
    val filters: List<MarketQueryFilter>,
    val ordering: List<MarketQueryOrder>,
    val limit: Int,
    val collectionGroup: Boolean = false,
    /** Explicit path for nested collections such as user notices and task proposals. */
    val collectionPath: String? = null
)
