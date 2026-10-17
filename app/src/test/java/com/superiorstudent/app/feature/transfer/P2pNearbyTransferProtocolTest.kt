package com.superiorstudent.app.feature.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pNearbyTransferProtocolTest {
    private val validOffer = P2pNearbyOffer(
        transferId = "c4315069-3a87-4a7a-9f19-32fc1c8ad9ac",
        fileName = "notes.pdf",
        sizeBytes = 1024L,
        mimeType = "application/pdf",
        sha256 = "a".repeat(64),
        chunkSize = 64 * 1024,
        senderPublicKeyBase64 = "A".repeat(88)
    )

    @Test
    fun validOfferPassesValidation() {
        assertTrue(P2pNearbyTransferProtocol.isValidOffer(validOffer))
    }

    @Test
    fun rejectsUnsafeNamesAndInvalidMetadata() {
        assertFalse(P2pNearbyTransferProtocol.isValidOffer(validOffer.copy(fileName = "../notes.pdf")))
        assertFalse(P2pNearbyTransferProtocol.isValidOffer(validOffer.copy(sizeBytes = 0L)))
        assertFalse(P2pNearbyTransferProtocol.isValidOffer(validOffer.copy(sha256 = "not-a-digest")))
        assertFalse(P2pNearbyTransferProtocol.isValidOffer(validOffer.copy(chunkSize = 999)))
        assertFalse(P2pNearbyTransferProtocol.isValidOffer(validOffer.copy(transferId = "short")))
        assertFalse(P2pNearbyTransferProtocol.isValidOffer(validOffer.copy(senderPublicKeyBase64 = "invalid")))
    }

    @Test
    fun confirmationCodeDoesNotDependOnKeyOrder() {
        val senderKey = "sender-public-key"
        val receiverKey = "receiver-public-key"
        val first = P2pNearbyTransferProtocol.confirmationCode(
            validOffer.transferId, validOffer.sha256, senderKey, receiverKey
        )
        val second = P2pNearbyTransferProtocol.confirmationCode(
            validOffer.transferId, validOffer.sha256, receiverKey, senderKey
        )
        assertEquals(6, first.length)
        assertEquals(first, second)
    }

    @Test
    fun confirmationCodeChangesWhenTranscriptChanges() {
        val baseline = P2pNearbyTransferProtocol.confirmationCode(
            validOffer.transferId, validOffer.sha256, "sender-key", "receiver-key"
        )
        val differentKey = P2pNearbyTransferProtocol.confirmationCode(
            validOffer.transferId, validOffer.sha256, "sender-key-modified", "receiver-key"
        )
        val differentDigest = P2pNearbyTransferProtocol.confirmationCode(
            validOffer.transferId, "b".repeat(64), "sender-key", "receiver-key"
        )
        assertNotEquals(baseline, differentKey)
        assertNotEquals(baseline, differentDigest)
    }
}
