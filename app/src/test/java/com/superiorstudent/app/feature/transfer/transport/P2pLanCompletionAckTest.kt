package com.superiorstudent.app.feature.transfer.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pLanCompletionAckTest {
    private val transferId = "transfer_0123456789ab"
    private val sha256 = "0123456789abcdef".repeat(4)
    private val token = "a".repeat(64)

    @Test
    fun validReceiptMatchesTransferDigestAndSecret() {
        val ack = P2pLanCompletionAck.create(transferId, sha256, token)
        assertTrue(P2pLanCompletionAck.verify(ack, transferId, sha256, token))
        ack.fill(0)
    }

    @Test
    fun receiptCannotBeReusedForAnotherTransferOrDigest() {
        val ack = P2pLanCompletionAck.create(transferId, sha256, token)
        assertFalse(P2pLanCompletionAck.verify(ack, transferId + "_other", sha256, token))
        assertFalse(P2pLanCompletionAck.verify(ack, transferId, "f".repeat(64), token))
        ack.fill(0)
    }

    @Test
    fun receiptRejectsIncorrectSecretAndModifiedBytes() {
        val ack = P2pLanCompletionAck.create(transferId, sha256, token)
        assertFalse(P2pLanCompletionAck.verify(ack, transferId, sha256, "b".repeat(64)))

        ack[ack.lastIndex] = (ack.last().toInt() xor 0x01).toByte()
        assertFalse(P2pLanCompletionAck.verify(ack, transferId, sha256, token))
        ack.fill(0)
    }

    @Test
    fun receiptRejectsInvalidInputs() {
        assertFalse(P2pLanCompletionAck.verify(byteArrayOf(1, 2, 3), transferId, sha256, token))
        assertFalse(P2pLanCompletionAck.verify(byteArrayOf(), transferId, "not-a-digest", token))
        assertFalse(P2pLanCompletionAck.verify(byteArrayOf(), "short", sha256, token))
    }
}
