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
    fun confirmationCodeDoesNotDependOnKeyOrderOrDirection() {
        val offer = validOffer.copy(senderPublicKeyBase64 = "B".repeat(88))
        val receiverKey = "C".repeat(88)
        val forward = P2pNearbyTransferProtocol.confirmationCode(offer, receiverKey)
        val reversedOffer = offer.copy(senderPublicKeyBase64 = receiverKey)
        val backward = P2pNearbyTransferProtocol.confirmationCode(
            reversedOffer, offer.senderPublicKeyBase64
        )
        assertEquals(6, forward.length)
        assertEquals(forward, backward)
    }

    @Test
    fun pauseAndResumeAreRecognizedAsTransferControls() {
        assertTrue(P2pNearbyTransferProtocol.isPauseResumeControl(P2pNearbyTransferProtocol.CONTROL_PAUSE))
        assertTrue(P2pNearbyTransferProtocol.isPauseResumeControl(P2pNearbyTransferProtocol.CONTROL_RESUME))
        assertFalse(P2pNearbyTransferProtocol.isPauseResumeControl("complete"))
        assertFalse(P2pNearbyTransferProtocol.isPauseResumeControl("unknown"))
    }

    @Test
    fun confirmationCodeChangesWhenTranscriptChanges() {
        val offer = validOffer.copy(senderPublicKeyBase64 = "B".repeat(88))
        val receiverKey = "C".repeat(88)
        val baseline = P2pNearbyTransferProtocol.confirmationCode(offer, receiverKey)
        val differentKey = P2pNearbyTransferProtocol.confirmationCode(
            offer.copy(senderPublicKeyBase64 = "D".repeat(88)), receiverKey
        )
        val differentDigest = P2pNearbyTransferProtocol.confirmationCode(
            offer.copy(sha256 = "b".repeat(64)), receiverKey
        )
        val differentName = P2pNearbyTransferProtocol.confirmationCode(
            offer.copy(fileName = "different.pdf"), receiverKey
        )
        assertNotEquals(baseline, differentKey)
        assertNotEquals(baseline, differentDigest)
        assertNotEquals(baseline, differentName)
    }
}
