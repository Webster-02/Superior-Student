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

    private fun descriptor(bytes: ByteArray, chunkSize: Int = 32 * 1024) =
        P2pEncryptedChunkStream.TransferDescriptor(
            transferId = transferId,
            fileName = "sample.bin",
            mimeType = "application/octet-stream",
            totalBytes = bytes.size.toLong(),
            sha256Hex = MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) },
            chunkSize = minOf(chunkSize, P2pEncryptedChunkStream.MAX_CHUNK_BYTES)
        )

    @Test fun encryptedStreamRoundTripsMultipleChunks() {
        val bytes = ByteArray(132000) { ((it * 31) xor (it shr 3)).toByte() }
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

    @Test fun interruptedTransferCanResumeFromVerifiedChunkBoundary() {
        val bytes = ByteArray(132000) { ((it * 13) xor (it shr 2)).toByte() }
        val description = descriptor(bytes, chunkSize = 32 * 1024)
        val senderKeys = P2pTransferCrypto.generateEphemeralKeyPair()
        val receiverKeys = P2pTransferCrypto.generateEphemeralKeyPair()
        val senderKey = P2pTransferCrypto.deriveTransferKey(
            senderKeys, receiverKeys.publicKeyBase64(), transferId
        )
        val receiverKey = P2pTransferCrypto.deriveTransferKey(
            receiverKeys, senderKeys.publicKeyBase64(), transferId
        )

        val firstFrames = mutableListOf<ByteArray>()
        P2pEncryptedChunkStream.Sender(
            ByteArrayInputStream(bytes), senderKey, description
        ).use { sender ->
            firstFrames += sender.nextFrame()!!
            firstFrames += sender.nextFrame()!!
        }
        val prefixOutput = ByteArrayOutputStream()
        P2pEncryptedChunkStream.Receiver(prefixOutput, receiverKey, description).use { receiver ->
            firstFrames.forEach(receiver::acceptFrame)
            assertEquals((2 * description.chunkSize).toLong(), receiver.bytesReceived)
        }
        val checkpoint = prefixOutput.toByteArray()
        assertTrue(P2pEncryptedChunkStream.isValidCheckpoint(
            checkpoint.size.toLong(), description.totalBytes, description.chunkSize
        ))

        val resumedFrames = mutableListOf<ByteArray>()
        P2pEncryptedChunkStream.Sender(
            ByteArrayInputStream(bytes), senderKey, description, checkpoint.size.toLong()
        ).use { sender ->
            while (true) resumedFrames += sender.nextFrame() ?: break
            resumedFrames += sender.finishFrame()
            sender.markEndFrameSent()
        }

        val finalOutput = ByteArrayOutputStream().apply { write(checkpoint) }
        P2pEncryptedChunkStream.Receiver(
            finalOutput, receiverKey, description,
            initialBytes = checkpoint.size.toLong(),
            prefixInput = ByteArrayInputStream(checkpoint)
        ).use { receiver ->
            resumedFrames.forEach(receiver::acceptFrame)
            receiver.finish()
            assertTrue(receiver.isComplete)
            assertEquals(bytes.size.toLong(), receiver.bytesReceived)
        }
        assertArrayEquals(bytes, finalOutput.toByteArray())
    }

    @Test fun resumeRejectsUnalignedOrOversizedCheckpoints() {
        assertTrue(P2pEncryptedChunkStream.isValidCheckpoint(0L, 100_000L, 32 * 1024))
        assertTrue(P2pEncryptedChunkStream.isValidCheckpoint(32_768L, 100_000L, 32 * 1024))
        assertTrue(P2pEncryptedChunkStream.isValidCheckpoint(100_000L, 100_000L, 32 * 1024))
        assertFalse(P2pEncryptedChunkStream.isValidCheckpoint(1L, 100_000L, 32 * 1024))
        assertFalse(P2pEncryptedChunkStream.isValidCheckpoint(131_072L, 100_000L, 32 * 1024))
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
