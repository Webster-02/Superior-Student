package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/** A config value paired with the time at which it was cached. */
data class MarketConfigCacheEntry(
    val config: MarketConfig,
    val savedAtMillis: Long
)

/**
 * Versioned, bounded JSON codec for the Market config cache.
 *
 * This codec deliberately has no Android storage or Firestore dependency. It
 * preserves supported Firestore scalar/container types in tagged envelopes so
 * extra limit fields cannot accidentally collide with timestamp markers.
 */
object MarketConfigCacheCodec {
    const val FORMAT_VERSION: Int = 1
    const val MAX_PAYLOAD_BYTES: Int = 64 * 1024

    private const val MAX_NESTING_DEPTH = 16
    private const val MAX_VALUE_NODES = 5_000

    fun encode(entry: MarketConfigCacheEntry): String? {
        if (entry.savedAtMillis <= 0L) return null

        return try {
            val firestoreMap = MarketConverters.toFirestoreMap(entry.config)
            if (MarketConverters.config(firestoreMap) == null) return null

            val budget = WriteBudget()
            val root = JSONObject()
                .put("formatVersion", FORMAT_VERSION)
                .put("savedAtMillis", entry.savedAtMillis)
                .put("config", encodeValue(firestoreMap, 0, budget))
            val payload = root.toString()
            if (payload.toByteArray(StandardCharsets.UTF_8).size > MAX_PAYLOAD_BYTES) null else payload
        } catch (_: Exception) {
            null
        }
    }

    fun decode(payload: String): MarketConfigCacheEntry? {
        if (payload.isBlank() ||
            payload.toByteArray(StandardCharsets.UTF_8).size > MAX_PAYLOAD_BYTES
        ) return null

        return try {
            val root = JSONObject(payload)
            requireExactKeys(root, setOf("formatVersion", "savedAtMillis", "config"))
            if (asLong(root.get("formatVersion")) != FORMAT_VERSION.toLong()) return null
            val savedAtMillis = asLong(root.get("savedAtMillis"))?.takeIf { it > 0L } ?: return null
            val decoded = decodeValue(root.get("config"), 0) as? Map<*, *> ?: return null
            val data = linkedMapOf<String, Any?>()
            for ((key, value) in decoded) {
                data[key as? String ?: return null] = value
            }
            val config = MarketConverters.config(data) ?: return null
            MarketConfigCacheEntry(config, savedAtMillis)
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeValue(value: Any?, depth: Int, budget: WriteBudget): JSONObject {
        require(depth <= MAX_NESTING_DEPTH) { "Market config cache nesting is too deep." }
        budget.visit()

        return when (value) {
            null -> JSONObject().put("type", "null")
            is String -> {
                budget.consumeString(value)
                JSONObject().put("type", "string").put("value", value)
            }
            is Boolean -> JSONObject().put("type", "boolean").put("value", value)
            is Byte -> JSONObject().put("type", "byte").put("value", value.toInt())
            is Short -> JSONObject().put("type", "short").put("value", value.toInt())
            is Int -> JSONObject().put("type", "int").put("value", value)
            is Long -> JSONObject().put("type", "long").put("value", value)
            is Float -> {
                require(value.isFinite()) { "Non-finite cache numbers are not supported." }
                JSONObject().put("type", "float").put("value", value.toDouble())
            }
            is Double -> {
                require(value.isFinite()) { "Non-finite cache numbers are not supported." }
                JSONObject().put("type", "double").put("value", value)
            }
            is Timestamp -> JSONObject()
                .put("type", "timestamp")
                .put("seconds", value.seconds)
                .put("nanoseconds", value.nanoseconds)
            is Map<*, *> -> {
                require(value.size <= MAX_VALUE_NODES) { "Market config cache map is too large." }
                val items = JSONObject()
                for ((key, item) in value) {
                    val stringKey = key as? String
                        ?: throw IllegalArgumentException("Cache maps must have string keys.")
                    budget.consumeString(stringKey)
                    items.put(stringKey, encodeValue(item, depth + 1, budget))
                }
                JSONObject().put("type", "map").put("value", items)
            }
            is List<*> -> {
                require(value.size <= MAX_VALUE_NODES) { "Market config cache list is too large." }
                val items = JSONArray()
                for (item in value) items.put(encodeValue(item, depth + 1, budget))
                JSONObject().put("type", "list").put("value", items)
            }
            else -> throw IllegalArgumentException(
                "Unsupported Market config cache value: ${value::class.java.name}"
            )
        }
    }

    private fun decodeValue(encoded: Any?, depth: Int): Any? {
        require(depth <= MAX_NESTING_DEPTH) { "Market config cache nesting is too deep." }
        val wrapper = encoded as? JSONObject
            ?: throw IllegalArgumentException("Cache values must be tagged JSON objects.")
        val type = wrapper.getString("type")

        return when (type) {
            "null" -> {
                requireExactKeys(wrapper, setOf("type"))
                null
            }
            "string" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                wrapper.get("value") as? String
                    ?: throw IllegalArgumentException("Invalid cached string.")
            }
            "boolean" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                wrapper.get("value") as? Boolean
                    ?: throw IllegalArgumentException("Invalid cached boolean.")
            }
            "byte" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                val number = asLong(wrapper.get("value"))
                    ?.takeIf { it in Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong() }
                    ?: throw IllegalArgumentException("Invalid cached byte.")
                number.toByte()
            }
            "short" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                val number = asLong(wrapper.get("value"))
                    ?.takeIf { it in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong() }
                    ?: throw IllegalArgumentException("Invalid cached short.")
                number.toShort()
            }
            "int" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                val number = asLong(wrapper.get("value"))
                    ?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }
                    ?: throw IllegalArgumentException("Invalid cached int.")
                number.toInt()
            }
            "long" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                asLong(wrapper.get("value"))
                    ?: throw IllegalArgumentException("Invalid cached long.")
            }
            "float" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                val number = asDouble(wrapper.get("value"))
                    ?.takeIf { it >= -Float.MAX_VALUE.toDouble() && it <= Float.MAX_VALUE.toDouble() }
                    ?: throw IllegalArgumentException("Invalid cached float.")
                number.toFloat().takeIf { it.isFinite() }
                    ?: throw IllegalArgumentException("Invalid cached float.")
            }
            "double" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                asDouble(wrapper.get("value"))
                    ?: throw IllegalArgumentException("Invalid cached double.")
            }
            "timestamp" -> {
                requireExactKeys(wrapper, setOf("type", "seconds", "nanoseconds"))
                val seconds = asLong(wrapper.get("seconds"))
                    ?: throw IllegalArgumentException("Invalid cached timestamp seconds.")
                val nanoseconds = asLong(wrapper.get("nanoseconds"))
                    ?.takeIf { it in 0L..999_999_999L }
                    ?: throw IllegalArgumentException("Invalid cached timestamp nanoseconds.")
                Timestamp(seconds, nanoseconds.toInt())
            }
            "map" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                val items = wrapper.get("value") as? JSONObject
                    ?: throw IllegalArgumentException("Invalid cached map.")
                val result = linkedMapOf<String, Any?>()
                val keys = items.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = decodeValue(items.get(key), depth + 1)
                }
                result
            }
            "list" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                val items = wrapper.get("value") as? JSONArray
                    ?: throw IllegalArgumentException("Invalid cached list.")
                if (items.length() > MAX_VALUE_NODES) {
                    throw IllegalArgumentException("Market config cache list is too large.")
                }
                List(items.length()) { index -> decodeValue(items.get(index), depth + 1) }
            }
            else -> throw IllegalArgumentException("Unknown Market config cache value type.")
        }
    }

    private fun requireExactKeys(value: JSONObject, expected: Set<String>) {
        val actual = linkedSetOf<String>()
        val keys = value.keys()
        while (keys.hasNext()) actual.add(keys.next())
        require(actual == expected) { "Unexpected Market config cache payload shape." }
    }

    private fun asLong(value: Any?): Long? = when (value) {
        is Byte -> value.toLong()
        is Short -> value.toLong()
        is Int -> value.toLong()
        is Long -> value
        is Number -> {
            val number = value.toDouble()
            if (number.isFinite() &&
                number >= Long.MIN_VALUE.toDouble() &&
                number < Long.MAX_VALUE.toDouble() &&
                number == number.toLong().toDouble()
            ) number.toLong() else null
        }
        else -> null
    }

    private fun asDouble(value: Any?): Double? =
        (value as? Number)?.toDouble()?.takeIf { it.isFinite() }

    private class WriteBudget {
        private var visited = 0
        private var consumedBytes = 0

        fun visit() {
            visited += 1
            require(visited <= MAX_VALUE_NODES) { "Market config cache has too many values." }
        }

        fun consumeString(value: String) {
            consumedBytes += value.toByteArray(StandardCharsets.UTF_8).size
            require(consumedBytes <= MAX_PAYLOAD_BYTES) { "Market config cache values are too large." }
        }
    }
}
