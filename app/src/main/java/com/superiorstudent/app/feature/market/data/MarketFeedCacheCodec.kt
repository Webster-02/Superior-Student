package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

internal data class MarketFeedCacheDocument(
    val id: String,
    val data: Map<String, Any?>
)

internal data class MarketFeedCacheEntry(
    val kind: String,
    val ownerUid: String?,
    val savedAtMillis: Long,
    val documents: List<MarketFeedCacheDocument>
)

/**
 * Bounded, versioned cache codec for real Firestore records used by the Market feeds.
 * Every value is tagged so Firestore Timestamps and nested maps round-trip without
 * relying on JSONObject's implicit type conversion.
 */
internal object MarketFeedCacheCodec {
    const val FORMAT_VERSION = 1
    const val MAX_ITEMS = 50
    const val MAX_PAYLOAD_BYTES = 512 * 1024
    const val CLOCK_SKEW_BUFFER_MILLIS = 30_000L

    private const val MAX_DEPTH = 16
    private const val MAX_VALUE_NODES = 20_000
    private const val MAX_STRING_CHARS = 160_000

    fun encode(
        kind: String,
        ownerUid: String?,
        savedAtMillis: Long,
        documents: List<MarketFeedCacheDocument>
    ): String? {
        if (kind !in CACHE_KINDS || savedAtMillis <= 0L ||
            (kind == "profile" && ownerUid.isNullOrBlank()) ||
            (kind != "profile" && ownerUid != null) ||
            documents.size > MAX_ITEMS ||
            documents.any { it.id.isBlank() || it.id.length > 150 }
        ) return null

        return try {
            val budget = WriteBudget()
            val rows = JSONArray()
            documents.forEach { document ->
                rows.put(
                    JSONObject()
                        .put("id", document.id)
                        .put("data", encodeValue(document.data, 0, budget))
                )
            }
            val root = JSONObject()
                .put("formatVersion", FORMAT_VERSION)
                .put("kind", kind)
                .put("ownerUid", ownerUid ?: JSONObject.NULL)
                .put("savedAtMillis", savedAtMillis)
                .put("documents", rows)
            root.toString().takeIf {
                it.toByteArray(StandardCharsets.UTF_8).size <= MAX_PAYLOAD_BYTES
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decode(
        payload: String?,
        expectedKind: String,
        expectedOwnerUid: String? = null
    ): MarketFeedCacheEntry? {
        if (payload.isNullOrBlank() ||
            payload.toByteArray(StandardCharsets.UTF_8).size > MAX_PAYLOAD_BYTES ||
            expectedKind !in CACHE_KINDS
        ) return null

        return try {
            val root = JSONObject(payload)
            requireExactKeys(root, setOf("formatVersion", "kind", "ownerUid", "savedAtMillis", "documents"))
            if (asLong(root.get("formatVersion")) != FORMAT_VERSION.toLong()) return null
            if (root.getString("kind") != expectedKind) return null
            val owner = root.get("ownerUid").let { value ->
                if (value == JSONObject.NULL) null else value as? String ?: return null
            }
            if ((expectedKind == "profile" && owner.isNullOrBlank()) ||
                (expectedKind != "profile" && owner != null) ||
                (expectedOwnerUid != null && owner != expectedOwnerUid)
            ) return null
            val savedAt = asLong(root.get("savedAtMillis"))?.takeIf { it > 0L } ?: return null
            val array = root.get("documents") as? JSONArray ?: return null
            if (array.length() > MAX_ITEMS) return null
            val documents = ArrayList<MarketFeedCacheDocument>(array.length())
            for (index in 0 until array.length()) {
                val row = array.get(index) as? JSONObject ?: return null
                requireExactKeys(row, setOf("id", "data"))
                val id = row.get("id") as? String ?: return null
                if (id.isBlank() || id.length > 150) return null
                val dataValue = decodeValue(row.get("data"), 0) as? Map<*, *> ?: return null
                val data = linkedMapOf<String, Any?>()
                dataValue.forEach { (key, value) ->
                    data[key as? String ?: return null] = value
                }
                documents += MarketFeedCacheDocument(id, data)
            }
            MarketFeedCacheEntry(expectedKind, owner, savedAt, documents)
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeValue(value: Any?, depth: Int, budget: WriteBudget): JSONObject {
        require(depth <= MAX_DEPTH) { "Market feed cache nesting is too deep." }
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
                require(value.isFinite())
                JSONObject().put("type", "float").put("value", value.toDouble())
            }
            is Double -> {
                require(value.isFinite())
                JSONObject().put("type", "double").put("value", value)
            }
            is Timestamp -> JSONObject()
                .put("type", "timestamp")
                .put("seconds", value.seconds)
                .put("nanoseconds", value.nanoseconds)
            is Map<*, *> -> {
                require(value.size <= MAX_VALUE_NODES)
                val items = JSONObject()
                value.forEach { (key, item) ->
                    val field = key as? String ?: throw IllegalArgumentException("Cache map keys must be strings.")
                    budget.consumeString(field)
                    items.put(field, encodeValue(item, depth + 1, budget))
                }
                JSONObject().put("type", "map").put("value", items)
            }
            is List<*> -> {
                require(value.size <= MAX_VALUE_NODES)
                val items = JSONArray()
                value.forEach { items.put(encodeValue(it, depth + 1, budget)) }
                JSONObject().put("type", "list").put("value", items)
            }
            else -> throw IllegalArgumentException("Unsupported Market cache type: ${value::class.java.name}")
        }
    }

    private fun decodeValue(encoded: Any?, depth: Int): Any? {
        require(depth <= MAX_DEPTH) { "Market feed cache nesting is too deep." }
        val wrapper = encoded as? JSONObject
            ?: throw IllegalArgumentException("Market cache values must be tagged JSON objects.")
        return when (val type = wrapper.get("type") as? String) {
            "null" -> {
                requireExactKeys(wrapper, setOf("type"))
                null
            }
            "string" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                (wrapper.get("value") as? String)?.also {
                    require(it.length <= MAX_STRING_CHARS)
                } ?: throw IllegalArgumentException("Invalid cached string.")
            }
            "boolean" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                wrapper.get("value") as? Boolean ?: throw IllegalArgumentException("Invalid cached boolean.")
            }
            "byte" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                asLong(wrapper.get("value"))?.takeIf {
                    it in Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong()
                }?.toByte() ?: throw IllegalArgumentException("Invalid cached byte.")
            }
            "short" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                asLong(wrapper.get("value"))?.takeIf {
                    it in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
                }?.toShort() ?: throw IllegalArgumentException("Invalid cached short.")
            }
            "int" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                asLong(wrapper.get("value"))?.takeIf {
                    it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                }?.toInt() ?: throw IllegalArgumentException("Invalid cached int.")
            }
            "long" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                asLong(wrapper.get("value")) ?: throw IllegalArgumentException("Invalid cached long.")
            }
            "float" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                asDouble(wrapper.get("value"))?.takeIf {
                    it >= -Float.MAX_VALUE.toDouble() && it <= Float.MAX_VALUE.toDouble()
                }?.toFloat()?.takeIf { it.isFinite() }
                    ?: throw IllegalArgumentException("Invalid cached float.")
            }
            "double" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                asDouble(wrapper.get("value")) ?: throw IllegalArgumentException("Invalid cached double.")
            }
            "timestamp" -> {
                requireExactKeys(wrapper, setOf("type", "seconds", "nanoseconds"))
                val seconds = asLong(wrapper.get("seconds"))
                    ?: throw IllegalArgumentException("Invalid cached timestamp.")
                val nanos = asLong(wrapper.get("nanoseconds"))
                    ?.takeIf { it in 0L..999_999_999L }?.toInt()
                    ?: throw IllegalArgumentException("Invalid cached timestamp.")
                Timestamp(seconds, nanos)
            }
            "map" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                val objectValue = wrapper.get("value") as? JSONObject
                    ?: throw IllegalArgumentException("Invalid cached map.")
                val result = linkedMapOf<String, Any?>()
                val keys = objectValue.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = decodeValue(objectValue.get(key), depth + 1)
                }
                result
            }
            "list" -> {
                requireExactKeys(wrapper, setOf("type", "value"))
                val array = wrapper.get("value") as? JSONArray
                    ?: throw IllegalArgumentException("Invalid cached list.")
                if (array.length() > MAX_VALUE_NODES) throw IllegalArgumentException("Cached list is too large.")
                List(array.length()) { decodeValue(array.get(it), depth + 1) }
            }
            else -> throw IllegalArgumentException("Unknown Market feed cache type: $type")
        }
    }

    private fun requireExactKeys(value: JSONObject, expected: Set<String>) {
        val actual = linkedSetOf<String>()
        val keys = value.keys()
        while (keys.hasNext()) actual += keys.next()
        require(actual == expected) { "Unexpected Market feed cache fields." }
    }

    private fun asLong(value: Any?): Long? = when (value) {
        is Int -> value.toLong()
        is Long -> value
        is Number -> value.toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toLong()
        else -> null
    }

    private fun asDouble(value: Any?): Double? = (value as? Number)?.toDouble()?.takeIf { it.isFinite() }

    private class WriteBudget {
        private var nodes = 0
        private var stringChars = 0
        fun visit() {
            nodes++
            require(nodes <= MAX_VALUE_NODES) { "Market feed cache is too complex." }
        }
        fun consumeString(value: String) {
            stringChars += value.length
            require(value.length <= MAX_STRING_CHARS && stringChars <= MAX_STRING_CHARS * 4) {
                "Market feed cache contains too much text."
            }
        }
    }

    private val CACHE_KINDS = setOf("tasks", "offers", "profile", "stats")
}
