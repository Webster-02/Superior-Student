package com.superiorstudent.app.feature.transfer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class P2pNearbyCheckpointRecordTest {
    private val record = P2pNearbyCheckpointRecord(
        transferId = "checkpoint_0123456789",
        fileName = "lecture-notes.pdf",
        sizeBytes = 256_000L,
        mimeType = "application/pdf",
        sha256 = "a".repeat(64),
        chunkSize = 32 * 1024,
        senderPublicKeyBase64 = "A".repeat(88),
        tempFileName = "p2p-nearby-test_123.part",
        savedAtEpochMillis = 1_797_000_000_000L
    )

    @Test
    fun checkpointRecordRoundTripsWithoutLosingTransferMetadata() {
        val restored = P2pNearbyCheckpointRecord.fromJson(
            record.transferId,
            JSONObject(record.toJson().toString())
        )

        assertNotNull(restored)
        assertEquals(record, restored)
        assertEquals(record.toOffer(), restored?.toOffer())
    }

    @Test
    fun checkpointRecordRejectsMismatchedIndexKey() {
        assertNull(
            P2pNearbyCheckpointRecord.fromJson(
                "different_transfer_id",
                JSONObject(record.toJson().toString())
            )
        )
    }

    @Test
    fun checkpointRecordRejectsUnsafeTemporaryFileName() {
        val tampered = JSONObject(record.toJson().toString())
            .put("tempFileName", "../outside.part")

        assertNull(P2pNearbyCheckpointRecord.fromJson(record.transferId, tampered))
    }

    @Test
    fun checkpointRecordRejectsInvalidOfferMetadata() {
        val tampered = JSONObject(record.toJson().toString())
            .put("sha256", "not-a-sha256")

        assertNull(P2pNearbyCheckpointRecord.fromJson(record.transferId, tampered))
    }
}
