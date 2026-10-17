package com.superiorstudent.app.feature.transfer.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pEncryptedChunkStreamTest {
    private val transferId = "transfer_0123456789ab"

    private fun descriptor(bytes: ByteArray, chunkSize: Int = 8192) =
        P2pEncryptedChunkStream.TransferDescriptor(
            transferId = transferId,
            fileName = "sample.bin",
            mimeType = "application/octet-stream",
            totalBytes = bytes.size.toLong(),
            sha256Hex = MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) },
            chunkSize = chunkSize
        )

    @Test fun encryptedStreamRoundTripsMultipleChunks() {
        val bytes = ByteArray(42000) { ((it * 31) xor (it shr 3)).toByte() }
        val description = descriptor(bytes)
        val senderKeys = P2pTransferCrypto.generateEphemeralKeyPair()
        val receiverKeys = P2pTransferCrypto.generateEphemeralKeyPair()
        val senderKey = P2pTransferCrypto.deriveTransferKey(senderKeys, receiverKeys.publicKeyBase64(), transferId)
        val receiverKey = P2pTransferCrypto.deriveTransferKey(receiverKeys, senderKeys.publicKeyBase64(), transferId)
        val frames = mutableListOf<ByteArray>()
        P2pEncryptedChunkStream.Sender(ByteArrayInputStream(bytes), senderKey, description).use { sender ->
            while (true) frames += sender.nextFrame() ?: break
            frames += sender.finishFrame()
            sender.markEndFrameSent()
        }
        assertTrue(frames.size > 2)

        val output = ByteArrayOutputStream()
        P2pEncryptedChunkStream.Receiver(output, receiverKey, description).use { receiver ->
            frames.forEach(receiver::acceptFrame)
            receiver.finish()
            assertTrue(receiver.isComplete)
            assertEquals(bytes.size.toLong(), receiver.bytesReceived)
        }
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test fun receiverRefusesCorruptedChunkAndNeverCompletes() {
        val bytes = "do not accept tampered data".toByteArray()
        val description = descriptor(bytes)
        val senderKeys = P2pTransferCrypto.generateEphemeralKeyPair()
        val receiverKeys = P2pTransferCrypto.generateEphemeralKeyPair()
        val keyA = P2pTransferCrypto.deriveTransferKey(senderKeys, receiverKeys.publicKeyBase64(), transferId)
        val keyB = P2pTransferCrypto.deriveTransferKey(receiverKeys, senderKeys.publicKeyBase64(), transferId)
        val sender = P2pEncryptedChunkStream.Sender(ByteArrayInputStream(bytes), keyA, description)
        val frame = sender.nextFrame()!!.copyOf()
        frame[frame.lastIndex] = (frame.last().toInt() xor 1).toByte()

        val receiver = P2pEncryptedChunkStream.Receiver(ByteArrayOutputStream(), keyB, description)
        assertThrows(Exception::class.java) { receiver.acceptFrame(frame) }
        assertFalse(receiver.isComplete)
        assertThrows(IllegalStateException::class.java) { receiver.finish() }
        sender.close()
        receiver.close()
    }

    @Test fun receiverRejectsMissingCompletionFrame() {
        val bytes = "incomplete transfer".toByteArray()
        val description = descriptor(bytes)
        val senderKeys = P2pTransferCrypto.generateEphemeralKeyPair()
        val receiverKeys = P2pTransferCrypto.generateEphemeralKeyPair()
        val keyA = P2pTransferCrypto.deriveTransferKey(senderKeys, receiverKeys.publicKeyBase64(), transferId)
        val keyB = P2pTransferCrypto.deriveTransferKey(receiverKeys, senderKeys.publicKeyBase64(), transferId)
        val sender = P2pEncryptedChunkStream.Sender(ByteArrayInputStream(bytes), keyA, description)
        val output = ByteArrayOutputStream()
        val receiver = P2pEncryptedChunkStream.Receiver(output, keyB, description)
        receiver.acceptFrame(sender.nextFrame()!!)
        assertThrows(IllegalStateException::class.java) { receiver.finish() }
        sender.close()
        receiver.close()
    }
}
