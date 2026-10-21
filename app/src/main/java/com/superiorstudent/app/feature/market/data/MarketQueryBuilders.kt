package com.superiorstudent.app.feature.market.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * Rule-aware read-query builders for the student Market feeds and owner listing pages.
 *
 * These filters make student queries provably compatible with Firestore list rules.
 * They are not an authorization boundary: firestore.rules remains authoritative.
 */
object MarketQueryBuilders {
    const val DEFAULT_PAGE_SIZE = 20
    private const val MAX_PAGE_SIZE = 50

    /**
     * Public task feed. Public queries must constrain BOTH moderation=ok and status=open.
     * The optional category and ordering variants match INDEX_QUERY_MAP.md.
     */
    fun publicTasks(
        firestore: FirebaseFirestore,
        category: String? = null,
        pinnedFirst: Boolean = true,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Query = execute(
        firestore,
        publicTasksPlan(category = category, pinnedFirst = pinnedFirst, pageSize = pageSize)
    )

    /**
     * Public offer feed. Public queries must constrain BOTH moderation=ok and status=active.
     * The optional category and ordering variants match INDEX_QUERY_MAP.md.
     */
    fun publicOffers(
        firestore: FirebaseFirestore,
        category: String? = null,
        pinnedFirst: Boolean = true,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Query = execute(
        firestore,
        publicOffersPlan(category = category, pinnedFirst = pinnedFirst, pageSize = pageSize)
    )

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
        var query: Query = firestore.collection(plan.collection)
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
    val collectionGroup: Boolean = false
)
