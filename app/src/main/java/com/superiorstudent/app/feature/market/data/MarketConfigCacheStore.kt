package com.superiorstudent.app.feature.market.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Minimal preference contract so cache persistence behaviour can be tested on
 * the JVM without constructing Android framework objects.
 */
internal interface MarketConfigCachePreferences {
    fun readString(key: String): String?
    fun writeString(key: String, value: String): Boolean
    fun remove(key: String): Boolean
}

/**
 * Persistent local storage for the public Market config document.
 *
 * The value remains available when stale so the caller can decide whether the
 * six-hour policy permits using it as connectivity-failure fallback. Corrupt
 * or incompatible values are removed rather than repeatedly parsed.
 */
class MarketConfigCacheStore internal constructor(
    private val preferences: MarketConfigCachePreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context) : this(
        SharedPreferencesMarketConfigCachePreferences(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        ),
        System::currentTimeMillis
    )

    /** Persist a validated config and the current cache timestamp. */
    fun write(config: MarketConfig): Boolean {
        val savedAt = try {
            nowMillis()
        } catch (_: RuntimeException) {
            return false
        }

        val payload = MarketConfigCacheCodec.encode(
            MarketConfigCacheEntry(config = config, savedAtMillis = savedAt)
        ) ?: return false

        return try {
            preferences.writeString(CACHE_KEY, payload)
        } catch (_: RuntimeException) {
            false
        }
    }

    /**
     * Read the cached value without enforcing TTL. The caller must use
     * MarketConfigCachePolicy to distinguish fresh data from fallback-only data.
     */
    fun read(): MarketConfigCacheEntry? {
        val payload = try {
            preferences.readString(CACHE_KEY)
        } catch (_: RuntimeException) {
            return null
        } ?: return null

        val entry = MarketConfigCacheCodec.decode(payload)
        if (entry == null) {
            try {
                preferences.remove(CACHE_KEY)
            } catch (_: RuntimeException) {
                // A future read will retry parsing and cleanup.
            }
        }
        return entry
    }

    /** Remove only the Market config entry, leaving other preferences untouched. */
    fun clear(): Boolean = try {
        preferences.remove(CACHE_KEY)
    } catch (_: RuntimeException) {
        false
    }

    private class SharedPreferencesMarketConfigCachePreferences(
        private val sharedPreferences: SharedPreferences
    ) : MarketConfigCachePreferences {
        override fun readString(key: String): String? = sharedPreferences.getString(key, null)

        override fun writeString(key: String, value: String): Boolean =
            sharedPreferences.edit().putString(key, value).commit()

        override fun remove(key: String): Boolean =
            sharedPreferences.edit().remove(key).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "market_config_cache"
        const val CACHE_KEY = "config_cache_v1"
    }
}
