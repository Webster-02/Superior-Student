package com.superiorstudent.app.feature.market.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Local persistence for the small public-feed pages and the signed-in user's own
 * Market profile. Cached records are always decoded through the strict Firestore
 * converters before the UI can use them.
 */
class MarketFeedCacheStore internal constructor(
    private val preferences: MarketFeedCachePreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context) : this(
        SharedPreferencesMarketFeedCachePreferences(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        ),
        System::currentTimeMillis
    )

    fun saveTasks(items: List<MarketTask>): Boolean = writeDocuments(
        key = TASKS_KEY,
        kind = "tasks",
        ownerUid = null,
        documents = items.take(MarketFeedCacheCodec.MAX_ITEMS).map { task ->
            MarketFeedCacheDocument(task.id, MarketConverters.toFirestoreMap(task))
        }
    )

    fun readTasks(): List<MarketTask> = readDocuments(TASKS_KEY, "tasks").mapNotNull { document ->
        MarketConverters.task(document.id, document.data)
    }

    fun saveOffers(items: List<MarketOffer>): Boolean = writeDocuments(
        key = OFFERS_KEY,
        kind = "offers",
        ownerUid = null,
        documents = items.take(MarketFeedCacheCodec.MAX_ITEMS).map { offer ->
            val data = MarketConverters.toFirestoreMap(offer).toMutableMap()
            // Feed cards do not render thumbnails. Avoid persisting large base64
            // images in the offline list cache; full offer details are read separately.
            data.remove("thumbnail")
            MarketFeedCacheDocument(offer.id, data)
        }
    )

    fun readOffers(): List<MarketOffer> = readDocuments(OFFERS_KEY, "offers").mapNotNull { document ->
        MarketConverters.offer(document.id, document.data)
    }

    fun saveProfile(profile: MarketProfile): Boolean = writeDocuments(
        key = PROFILE_KEY,
        kind = "profile",
        ownerUid = profile.uid,
        documents = listOf(MarketFeedCacheDocument(profile.uid, MarketConverters.toFirestoreMap(profile)))
    )

    /** The owner ID is checked twice: once in the cache envelope and once in the decoded record. */
    fun readProfile(uid: String): MarketProfile? {
        if (uid.isBlank()) return null
        val entry = readEntry(PROFILE_KEY, "profile", uid) ?: return null
        val document = entry.documents.singleOrNull() ?: return null
        if (document.id != uid) return null
        return MarketConverters.profile(document.id, document.data)
            ?.takeIf { it.uid == uid }
    }

    fun saveStats(stats: Collection<MarketUserStats>): Boolean {
        val merged = readStats().toMutableMap()
        stats.forEach { if (it.uid.isNotBlank()) merged[it.uid] = it }
        val documents = merged.values.toList().takeLast(MarketFeedCacheCodec.MAX_ITEMS).map { item ->
            MarketFeedCacheDocument(item.uid, MarketConverters.toFirestoreMap(item))
        }
        return writeDocuments(
            key = STATS_KEY,
            kind = "stats",
            ownerUid = null,
            documents = documents
        )
    }

    fun readStats(): Map<String, MarketUserStats> =
        readDocuments(STATS_KEY, "stats").mapNotNull { document ->
            MarketConverters.userStats(document.id, document.data)?.let { it.uid to it }
        }.toMap()

    /** Removes only this user's cached profile; public listing/stat caches remain intact. */
    fun clearProfile(uid: String): Boolean {
        if (uid.isBlank()) return false
        val entry = readEntry(PROFILE_KEY, "profile", uid) ?: return true
        return remove(PROFILE_KEY)
    }

    private fun writeDocuments(
        key: String,
        kind: String,
        ownerUid: String?,
        documents: List<MarketFeedCacheDocument>
    ): Boolean {
        val savedAtMillis = try {
            nowMillis()
        } catch (_: RuntimeException) {
            return false
        }
        val payload = MarketFeedCacheCodec.encode(kind, ownerUid, savedAtMillis, documents)
            ?: return false
        return try {
            preferences.writeString(key, payload)
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun readDocuments(key: String, kind: String): List<MarketFeedCacheDocument> =
        readEntry(key, kind, null)?.documents.orEmpty()

    private fun readEntry(
        key: String,
        kind: String,
        ownerUid: String?
    ): MarketFeedCacheEntry? {
        val payload = try {
            preferences.readString(key)
        } catch (_: RuntimeException) {
            return null
        } ?: return null
        val decoded = MarketFeedCacheCodec.decode(payload, kind, ownerUid)
        if (decoded == null) {
            try {
                preferences.remove(key)
            } catch (_: RuntimeException) {
                // A subsequent read may retry cleanup if storage is temporarily unavailable.
            }
        }
        return decoded
    }

    private fun remove(key: String): Boolean = try {
        preferences.remove(key)
    } catch (_: RuntimeException) {
        false
    }

    private class SharedPreferencesMarketFeedCachePreferences(
        private val sharedPreferences: SharedPreferences
    ) : MarketFeedCachePreferences {
        override fun readString(key: String): String? = sharedPreferences.getString(key, null)
        override fun writeString(key: String, value: String): Boolean =
            sharedPreferences.edit().putString(key, value).commit()
        override fun remove(key: String): Boolean =
            sharedPreferences.edit().remove(key).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "market_feed_cache_v1"
        const val TASKS_KEY = "tasks_v1"
        const val OFFERS_KEY = "offers_v1"
        const val PROFILE_KEY = "profile_v1"
        const val STATS_KEY = "stats_v1"
    }
}

internal interface MarketFeedCachePreferences {
    fun readString(key: String): String?
    fun writeString(key: String, value: String): Boolean
    fun remove(key: String): Boolean
}
