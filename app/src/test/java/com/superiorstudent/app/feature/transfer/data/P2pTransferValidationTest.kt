package com.superiorstudent.app.feature.transfer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pTransferValidationTest {
    @Test fun filenameValidationRejectsPathTraversal() {
        assertTrue(P2pTransferValidation.isValidFileName("notes.pdf"))
        assertFalse(P2pTransferValidation.isValidFileName("../notes.pdf"))
        assertFalse(P2pTransferValidation.isValidFileName("folder/notes.pdf"))
        assertFalse(P2pTransferValidation.isValidFileName(".."))
    }

    @Test fun sha256MustBeExactly64HexCharacters() {
        assertTrue(P2pTransferValidation.isValidSha256("a".repeat(64)))
        assertFalse(P2pTransferValidation.isValidSha256("xyz"))
        assertFalse(P2pTransferValidation.isValidSha256("g".repeat(64)))
    }

    @Test fun chunkSizeIsBoundedByEncryptedStreamFrameLimit() {
        assertTrue(P2pTransferValidation.isValidChunkSize(1024))
        assertTrue(P2pTransferValidation.isValidChunkSize(64 * 1024))
        assertFalse(P2pTransferValidation.isValidChunkSize(1023))
        assertFalse(P2pTransferValidation.isValidChunkSize(64 * 1024 + 1))
    }

    @Test fun lanCandidatesRequirePrivateAddress() {
        assertTrue(P2pTransferValidation.isPrivateLanAddress("192.168.1.22"))
        assertTrue(P2pTransferValidation.isPrivateLanAddress("10.0.0.4"))
        assertFalse(P2pTransferValidation.isPrivateLanAddress("8.8.8.8"))
        assertFalse(P2pTransferValidation.isPrivateLanAddress("127.0.0.1"))
    }

    @Test fun signalPayloadKeysAndValuesAreBounded() {
        assertTrue(P2pTransferValidation.isValidSignalPayload(
            P2pSignalType.WEBRTC_OFFER, mapOf("sdp" to "v=0\\r\\n")
        ))
        assertFalse(P2pTransferValidation.isValidSignalPayload(
            P2pSignalType.WEBRTC_OFFER, mapOf("sdp" to "v=0", "fileBytes" to "not allowed")
        ))
        assertTrue(P2pTransferValidation.isValidSignalPayload(
            P2pSignalType.LAN_CANDIDATES,
            mapOf("ip" to "192.168.1.2", "port" to 45678, "token" to "t".repeat(48))
        ))
        assertFalse(P2pTransferValidation.isValidSignalPayload(
            P2pSignalType.LAN_CANDIDATES,
            mapOf("ip" to "8.8.8.8", "port" to 45678, "token" to "t".repeat(48))
        ))
    }

    @Test fun conversationIdMustBeCanonicalSortedPair() {
        assertTrue(P2pTransferValidation.isConversationIdForPair("alice_bob", "bob", "alice"))
        assertFalse(P2pTransferValidation.isConversationIdForPair("bob_alice", "bob", "alice"))
    }
}
