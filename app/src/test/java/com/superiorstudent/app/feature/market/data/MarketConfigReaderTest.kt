package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class MarketConfigReaderTest {

    @Test
    fun readerUsesOnlyTheFixedConfigDocumentIdentity() {
        assertEquals("config", MarketConfigReaderPolicy.COLLECTION_NAME)
        assertEquals("market", MarketConfigReaderPolicy.DOCUMENT_ID)

        val validData = validConfigMap()
        assertNotNull(MarketConfigReaderPolicy.decode("market", validData))
        assertNull(MarketConfigReaderPolicy.decode("other", validData))
        assertNull(MarketConfigReaderPolicy.decode("market", null))
    }

    @Test
    fun decodesOnlyACompleteStrictlyValidConfig() {
        val decoded = MarketConfigReaderPolicy.decode("market", validConfigMap())

        assertNotNull(decoded)
        assertEquals("Read-only notice", decoded!!.announcement.text)
        assertEquals(listOf("spam", "scam"), decoded.blockedWords)
        assertEquals(MarketRankWeights(50.0, 30.0, 20.0), decoded.rankWeights)
        assertEquals(3, decoded.limits.openTasks)
    }

    @Test
    fun rejectsExtraTopLevelKeysMissingFieldsAndInvalidWeights() {
        val valid = validConfigMap()
        assertNull(MarketConfigReaderPolicy.decode("market", valid + ("unexpected" to true)))
        assertNull(MarketConfigReaderPolicy.decode("market", valid - "blockedWords"))
        assertNull(
            MarketConfigReaderPolicy.decode(
                "market",
                valid + ("rankWeights" to mapOf(
                    "newness" to 50.0,
                    "rating" to 30.0,
                    "match" to 19.0
                ))
            )
        )
    }

    @Test
    fun rejectsWrongNestedShapeOrRuleFixedLimits() {
        val valid = validConfigMap()
        assertNull(
            MarketConfigReaderPolicy.decode(
                "market",
                valid + ("announcement" to mapOf("text" to "missing active", "until" to null))
            )
        )

        val wrongLimits = (valid["limits"] as Map<String, Any?>).toMutableMap()
        wrongLimits["openTasks"] = 4
        assertNull(MarketConfigReaderPolicy.decode("market", valid + ("limits" to wrongLimits)))
    }

    private fun validConfigMap(): Map<String, Any?> = linkedMapOf(
        "announcement" to mapOf(
            "text" to "Read-only notice",
            "active" to true,
            "until" to Timestamp(1_800_000_000L, 0)
        ),
        "blockedWords" to listOf("spam", "scam"),
        "rankWeights" to mapOf(
            "newness" to 50.0,
            "rating" to 30.0,
            "match" to 20.0
        ),
        "limits" to mapOf(
            "openTasks" to 3,
            "proposalsPerDay" to 10,
            "proposalsPerTask" to 15,
            "activeOffers" to 5,
            "reportsToHide" to 3
        )
    )
}
