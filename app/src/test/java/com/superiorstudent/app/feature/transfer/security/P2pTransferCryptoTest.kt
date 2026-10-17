package com.superiorstudent.app.feature.transfer.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class P2pTransferCryptoTest {
    private val transferId = "transfer_0123456789ab"
    private val metadata = "application/octet-stream|notes.bin|12".toByteArray()

    @Test fun peersDeriveTheSameTransferKeyAndRoundTripChunks() {
        val sender = P2pTransferCrypto.generateEphemeralKeyPair()
        val receiver = P2pTransferCrypto.generateEphemeralKeyPair()

        val senderKey = P2pTransferCrypto.deriveTransferKey(sender, receiver.publicKeyBase64(), transferId)
        val receiverKey = P2pTransferCrypto.deriveTransferKey(receiver, sender.publicKeyBase64(), transferId)
        assertArrayEquals(senderKey.encoded, receiverKey.encoded)

        val original = "hello secure transfer".toByteArray()
        val chunk = P2pTransferCrypto.encryptChunk(senderKey, transferId, 0L, original, metadata)
        val restored = P2pTransferCrypto.decryptChunk(
            receiverKey, transferId, chunk.sequence, chunk.nonce, chunk.ciphertext, metadata
        )
        assertArrayEquals(original, restored)
    }

    @Test fun ciphertextModificationIsRejected() {
        val sender = P2pTransferCrypto.generateEphemeralKeyPair()
        val receiver = P2pTransferCrypto.generateEphemeralKeyPair()
        val key = P2pTransferCrypto.deriveTransferKey(sender, receiver.publicKeyBase64(), transferId)
        val chunk = P2pTransferCrypto.encryptChunk(key, transferId, 2L, byteArrayOf(1, 2, 3), metadata)
        val changed = chunk.ciphertext.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }

        assertThrows(Exception::class.java) {
            P2pTransferCrypto.decryptChunk(key, transferId, chunk.sequence, chunk.nonce, changed, metadata)
        }
    }

    @Test fun metadataTransferIdAndSequenceAreAuthenticated() {
        val sender = P2pTransferCrypto.generateEphemeralKeyPair()
        val receiver = P2pTransferCrypto.generateEphemeralKeyPair()
        val key = P2pTransferCrypto.deriveTransferKey(sender, receiver.publicKeyBase64(), transferId)
        val chunk = P2pTransferCrypto.encryptChunk(key, transferId, 0L, byteArrayOf(4, 5), metadata)

        assertThrows(Exception::class.java) {
            P2pTransferCrypto.decryptChunk(key, transferId, 1L, chunk.nonce, chunk.ciphertext, metadata)
        }
        assertThrows(Exception::class.java) {
            P2pTransferCrypto.decryptChunk(key, transferId + "_other", 0L, chunk.nonce, chunk.ciphertext, metadata)
        }
        assertThrows(Exception::class.java) {
            P2pTransferCrypto.decryptChunk(key, transferId, 0L, chunk.nonce, chunk.ciphertext, "changed".toByteArray())
        }
    }

    @Test fun differentTransferIdsDeriveDifferentKeys() {
        val sender = P2pTransferCrypto.generateEphemeralKeyPair()
        val receiver = P2pTransferCrypto.generateEphemeralKeyPair()
        val first = P2pTransferCrypto.deriveTransferKey(sender, receiver.publicKeyBase64(), transferId)
        val second = P2pTransferCrypto.deriveTransferKey(sender, receiver.publicKeyBase64(), transferId + "_other")
        assertNotEquals(first.encoded.toList(), second.encoded.toList())
    }

    @Test fun rejectsMalformedPeerPublicKeyAndInvalidTransferId() {
        val keyPair = P2pTransferCrypto.generateEphemeralKeyPair()
        assertThrows(IllegalArgumentException::class.java) {
            P2pTransferCrypto.deriveTransferKey(keyPair, "not-base64", transferId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            P2pTransferCrypto.deriveTransferKey(keyPair, keyPair.publicKeyBase64(), "bad id")
        }
    }
}
