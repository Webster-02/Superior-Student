package com.superiorstudent.app.feature.transfer.security

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Per-transfer end-to-end cryptographic primitives.
 *
 * The peers must authenticate/exchange ephemeral public keys through an already
 * authenticated conversation before using this API. A public key received over an
 * unauthenticated channel does not identify an authenticated peer.
 *
 * Never reuse a transfer key for another transfer. File bytes are not sent to Firebase.
 */
object P2pTransferCrypto {
    private const val EC_CURVE = "secp256r1"
    private const val AES_KEY_BYTES = 32
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val MAX_AAD_BYTES = 4096
    private const val MAX_PLAINTEXT_BYTES = 1024 * 1024

    private val secureRandom = SecureRandom()

    data class EphemeralKeyPair internal constructor(internal val keyPair: KeyPair) {
        fun publicKeyBase64(): String = encodeBase64(keyPair.public.encoded)
    }

    data class EncryptedChunk(
        val sequence: Long,
        val nonce: ByteArray,
        val ciphertext: ByteArray
    )

    /** Generate one fresh P-256 key pair per transfer, not per chunk. */
    fun generateEphemeralKeyPair(): EphemeralKeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
        return EphemeralKeyPair(generator.generateKeyPair())
    }

    /**
     * Derive a 256-bit AES-GCM key using ECDH + HKDF-SHA256.
     * transferId binds the key to one transfer and helps prevent cross-transfer key reuse.
     */
    fun deriveTransferKey(
        local: EphemeralKeyPair,
        remotePublicKeyBase64: String,
        transferId: String
    ): SecretKey {
        require(isValidTransferId(transferId)) { "Invalid transfer ID." }
        val remoteEncoded = decodeBase64Bounded(remotePublicKeyBase64, 1024)
        val remotePublic = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(remoteEncoded))

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(local.keyPair.private)
        agreement.doPhase(remotePublic, true)
        val sharedSecret = agreement.generateSecret()
        try {
            val salt = MessageDigest.getInstance("SHA-256")
                .digest(("SuperiorStudent/P2P/v1/" + transferId).toByteArray(StandardCharsets.UTF_8))
            val info = ("AES-256-GCM/P2P/v1/" + transferId).toByteArray(StandardCharsets.UTF_8)
            val keyBytes = hkdfSha256(sharedSecret, salt, info, AES_KEY_BYTES)
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            sharedSecret.fill(0)
        }
    }

    /** Sequence numbers must be monotonic within a direction for protocol-level replay checks. */
    fun encryptChunk(
        key: SecretKey,
        transferId: String,
        sequence: Long,
        plaintext: ByteArray,
        authenticatedMetadata: ByteArray
    ): EncryptedChunk {
        require(isValidTransferId(transferId)) { "Invalid transfer ID." }
        require(sequence >= 0L) { "Sequence must be non-negative." }
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Chunk is too large." }
        require(authenticatedMetadata.size <= MAX_AAD_BYTES) { "Chunk metadata is too large." }
        val nonce = ByteArray(GCM_NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad(transferId, sequence, authenticatedMetadata))
        return EncryptedChunk(sequence, nonce, cipher.doFinal(plaintext))
    }

    /** Tampering with ciphertext, metadata, transfer ID or sequence fails authentication. */
    fun decryptChunk(
        key: SecretKey,
        transferId: String,
        sequence: Long,
        nonce: ByteArray,
        ciphertext: ByteArray,
        authenticatedMetadata: ByteArray
    ): ByteArray {
        require(isValidTransferId(transferId)) { "Invalid transfer ID." }
        require(sequence >= 0L) { "Sequence must be non-negative." }
        require(nonce.size == GCM_NONCE_BYTES) { "Invalid GCM nonce length." }
        require(ciphertext.size in 16..(MAX_PLAINTEXT_BYTES + 16)) { "Invalid ciphertext length." }
        require(authenticatedMetadata.size <= MAX_AAD_BYTES) { "Chunk metadata is too large." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad(transferId, sequence, authenticatedMetadata))
        return cipher.doFinal(ciphertext)
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun aad(transferId: String, sequence: Long, metadata: ByteArray): ByteArray {
        require(isValidTransferId(transferId)) { "Invalid transfer ID." }
        val idBytes = transferId.toByteArray(StandardCharsets.UTF_8)
        return ByteBuffer.allocate(4 + idBytes.size + 8 + metadata.size)
            .putInt(idBytes.size)
            .put(idBytes)
            .putLong(sequence)
            .put(metadata)
            .array()
    }

    private fun isValidTransferId(value: String): Boolean =
        value.matches(Regex("^[A-Za-z0-9_-]{16,80}$"))

    /** RFC 5869 HKDF with SHA-256. */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * 32))
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extract.doFinal(ikm)
        try {
            val output = ByteArrayOutputStream()
            var previous = ByteArray(0)
            var counter = 1
            while (output.size() < length) {
                val expand = Mac.getInstance("HmacSHA256")
                expand.init(SecretKeySpec(prk, "HmacSHA256"))
                expand.update(previous)
                expand.update(info)
                expand.update(counter.toByte())
                previous = expand.doFinal()
                output.write(previous)
                counter++
            }
            return output.toByteArray().copyOf(length)
        } finally {
            prk.fill(0)
        }
    }

    private fun decodeBase64Bounded(value: String, maxBytes: Int): ByteArray {
        require(value.isNotBlank() && value.length <= maxBytes * 2) { "Encoded key is too large." }
        require(value.length % 4 == 0) { "Remote public key is not valid Base64." }
        val decoded = decodeBase64(value)
        require(decoded.isNotEmpty() && decoded.size <= maxBytes) { "Remote public key size is invalid." }
        require(encodeBase64(decoded) == value) { "Remote public key is not canonical Base64." }
        return decoded
    }

    // Small RFC 4648 Base64 helpers keep minSdk 23 support and make the crypto tests
    // runnable on the plain JVM used by this repository's GitHub Actions workflow.
    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun encodeBase64(input: ByteArray): String {
        val output = StringBuilder(((input.size + 2) / 3) * 4)
        var index = 0
        while (index < input.size) {
            val first = input[index++].toInt() and 0xff
            val hasSecond = index < input.size
            val second = if (hasSecond) input[index++].toInt() and 0xff else 0
            val hasThird = index < input.size
            val third = if (hasThird) input[index++].toInt() and 0xff else 0
            output.append(BASE64_ALPHABET[first ushr 2])
            output.append(BASE64_ALPHABET[((first and 0x03) shl 4) or (second ushr 4)])
            output.append(if (hasSecond) BASE64_ALPHABET[((second and 0x0f) shl 2) or (third ushr 6)] else '=')
            output.append(if (hasThird) BASE64_ALPHABET[third and 0x3f] else '=')
        }
        return output.toString()
    }

    private fun decodeBase64(value: String): ByteArray {
        require(value.isNotEmpty() && value.length % 4 == 0) { "Invalid Base64 length." }
        val padding = when {
            value.endsWith("==") -> 2
            value.endsWith("=") -> 1
            else -> 0
        }
        val decodedSize = value.length / 4 * 3 - padding
        val output = ByteArray(decodedSize)
        var outIndex = 0
        var index = 0
        while (index < value.length) {
            val c0 = base64Value(value[index++])
            val c1 = base64Value(value[index++])
            val c2Char = value[index++]
            val c3Char = value[index++]
            val finalQuartet = index == value.length
            require(c0 >= 0 && c1 >= 0) { "Invalid Base64 character." }
            val c2 = if (c2Char == '=') -2 else base64Value(c2Char)
            val c3 = if (c3Char == '=') -2 else base64Value(c3Char)
            require(c2 != -1 && c3 != -1) { "Invalid Base64 character." }
            require(c2 != -2 || (finalQuartet && c3 == -2)) { "Invalid Base64 padding." }
            require(c3 != -2 || finalQuartet) { "Invalid Base64 padding." }
            require(c2 != -2 || (c1 and 0x0f) == 0) { "Non-canonical Base64 padding." }
            if (c2 >= 0) require(c3 != -2 || (c2 and 0x03) == 0) { "Non-canonical Base64 padding." }

            val combined = (c0 shl 18) or (c1 shl 12) or
                ((if (c2 >= 0) c2 else 0) shl 6) or (if (c3 >= 0) c3 else 0)
            if (outIndex < output.size) output[outIndex++] = (combined ushr 16).toByte()
            if (outIndex < output.size) output[outIndex++] = (combined ushr 8).toByte()
            if (outIndex < output.size) output[outIndex++] = combined.toByte()
        }
        require(outIndex == output.size) { "Invalid Base64 payload." }
        return output
    }

    private fun base64Value(character: Char): Int = BASE64_ALPHABET.indexOf(character)
}
