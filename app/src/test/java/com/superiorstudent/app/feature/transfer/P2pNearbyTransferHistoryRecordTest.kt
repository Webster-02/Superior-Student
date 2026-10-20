package com.superiorstudent.app.feature.transfer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pNearbyTransferHistoryRecordTest {
    private val record = P2pNearbyTransferHistoryRecord(
        transferId = "history_12345",
        fileName = "lecture-notes.pdf",
        mimeType = "application/pdf",
        sizeBytes = 256_000L,
        completedAtEpochMillis = 1_797_000_000_000L,
        direction = P2pNearbyTransferHistoryRecord.RECEIVED,
        contentUri = "content://provider/document/123"
    )

    @Test
    fun historyRecordRoundTrips() {
        val restored = P2pNearbyTransferHistoryRecord.fromJson(JSONObject(record.toJson().toString()))
        assertNotNull(restored)
        assertEquals(record, restored)
    }

    @Test
    fun historyRecordRejectsInvalidMetadata() {
        assertFalse(record.copy(sizeBytes = 0L).isValid())
        assertFalse(record.copy(fileName = "../private.txt").isValid())
        assertFalse(record.copy(direction = "UNKNOWN").isValid())
        assertFalse(record.copy(contentUri = "https://example.com/file.pdf").isValid())
        assertFalse(record.copy(transferId = "bad id").isValid())
    }

    @Test
    fun historyJsonRejectsUnsafeUriAndFileName() {
        assertNull(P2pNearbyTransferHistoryRecord.fromJson(
            JSONObject(record.toJson().toString()).put("contentUri", "file:///data/private")
        ))
        assertNull(P2pNearbyTransferHistoryRecord.fromJson(
            JSONObject(record.toJson().toString()).put("fileName", "../secrets.txt")
        ))
    }

    @Test
    fun sentAndReceivedDirectionsAreExplicit() {
        assertTrue(record.copy(direction = P2pNearbyTransferHistoryRecord.SENT).isValid())
        assertTrue(record.copy(direction = P2pNearbyTransferHistoryRecord.RECEIVED).isValid())
    }
}
