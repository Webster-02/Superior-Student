package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketConfigCacheCodecTest {

    private val timestamp = Timestamp(1_800_000_000L, 123_456_789)

    @Test
    fun roundTripsConfigTimestampAndNestedAllowedExtraValues() {
        val config = sampleConfig()
        val entry = MarketConfigCacheEntry(config, 1_800_000_123_456L)

        val payload = MarketConfigCacheCodec.encode(entry)
        assertNotNull(payload)
        val decoded = MarketConfigCacheCodec.decode(payload!!)

        assertEquals(entry, decoded)
        assertEquals(timestamp, decoded!!.config.announcement.until)
        assertEquals(4_000_000_000L, decoded.config.limits.extraFields["largeNumber"])
        val nested = decoded.config.limits.extraFields["nested"] as Map<*, *>
        assertEquals(timestamp, nested["timestamp"])
        assertEquals(listOf("text", 7, true, null), nested["items"])
    }

    @Test
    fun roundTripsNullAnnouncementUntilAndPreservesStringFields() {
        val original = sampleConfig().copy(
            announcement = MarketAnnouncement("Welcome", false, null),
            blockedWords = listOf("scam", "spam")
        )
        val decoded = MarketConfigCacheCodec.decode(
            MarketConfigCacheCodec.encode(MarketConfigCacheEntry(original, 1_800_000_000_000L))!!
        )

        assertEquals(original, decoded!!.config)
    }

    @Test
    fun rejectsInvalidConfigAndInvalidSavedAtTimeOnEncode() {
        val badConfig = sampleConfig().copy(
            rankWeights = MarketRankWeights(50.0, 30.0, 19.0)
        )
        assertNull(MarketConfigCacheCodec.encode(MarketConfigCacheEntry(badConfig, 1_800_000_000_000L)))
        assertNull(MarketConfigCacheCodec.encode(MarketConfigCacheEntry(sampleConfig(), 0L)))
        assertNull(MarketConfigCacheCodec.encode(MarketConfigCacheEntry(sampleConfig(), -1L)))
    }

    @Test
    fun rejectsWrongFormatUnexpectedFieldsMalformedJsonAndOversizedPayload() {
        val valid = MarketConfigCacheCodec.encode(
            MarketConfigCacheEntry(sampleConfig(), 1_800_000_000_000L)
        )!!
        assertNull(MarketConfigCacheCodec.decode("{broken"))
        assertNull(MarketConfigCacheCodec.decode("x".repeat(MarketConfigCacheCodec.MAX_PAYLOAD_BYTES + 1)))

        val wrongVersion = JSONObject(valid).put("formatVersion", MarketConfigCacheCodec.FORMAT_VERSION + 1)
        assertNull(MarketConfigCacheCodec.decode(wrongVersion.toString()))

        val unexpectedKey = JSONObject(valid).put("unexpected", true)
        assertNull(MarketConfigCacheCodec.decode(unexpectedKey.toString()))
    }

    @Test
    fun rejectsUnsupportedNestedTypesInsteadOfSilentlyDroppingThem() {
        val config = sampleConfig().copy(
            limits = sampleConfig().limits.copy(
                extraFields = mapOf("unsupported" to Any())
            )
        )
        assertNull(MarketConfigCacheCodec.encode(MarketConfigCacheEntry(config, 1_800_000_000_000L)))
    }

    @Test
    fun payloadHasVersionAndStaysWithinTheCacheByteLimit() {
        val payload = MarketConfigCacheCodec.encode(
            MarketConfigCacheEntry(sampleConfig(), 1_800_000_000_000L)
        )!!
        assertEquals(MarketConfigCacheCodec.FORMAT_VERSION, JSONObject(payload).getInt("formatVersion"))
        assertTrue(payload.toByteArray(Charsets.UTF_8).size <= MarketConfigCacheCodec.MAX_PAYLOAD_BYTES)
    }

    private fun sampleConfig(): MarketConfig = MarketConfig(
        announcement = MarketAnnouncement("Maintenance tonight", true, timestamp),
        blockedWords = listOf("scam", "unapproved"),
        rankWeights = MarketRankWeights(newness = 50.0, rating = 30.0, match = 20.0),
        limits = MarketLimits(
            openTasks = 3,
            proposalsPerDay = 10,
            proposalsPerTask = 15,
            activeOffers = 5,
            reportsToHide = 3,
            extraFields = linkedMapOf(
                "serverOwnedEntry" to "preserve",
                "largeNumber" to 4_000_000_000L,
                "nested" to linkedMapOf(
                    "timestamp" to timestamp,
                    "items" to listOf("text", 7, true, null)
                )
            )
        )
    )
}
