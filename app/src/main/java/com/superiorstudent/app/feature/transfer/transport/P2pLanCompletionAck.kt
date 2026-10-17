package com.superiorstudent.app.feature.transfer.transport

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Local receipt proof used only after the receiver has authenticated the end frame and SHA-256.
 * It lets an already-authenticated LAN transfer finish even if Internet signaling disappears.
 * This receipt is not a file frame and must never be written to the destination file.
 */
internal object P2pLanCompletionAck {
    private val MAGIC = "SSP2PACK".toByteArray(StandardCharsets.US_ASCII)
    private val TRANSFER_ID_PATTERN = Regex("^[A-Za-z0-9_-]{16,80}$")
    private val SHA256_PATTERN = Regex("^[a-fA-F0-9]{64}$")
    private const val MAC_BYTES = 32

    fun create(transferId: String, sha256Hex: String, token: String): ByteArray {
        require(TRANSFER_ID_PATTERN.matches(transferId)) { "Invalid transfer ID for LAN receipt." }
        require(SHA256_PATTERN.matches(sha256Hex)) { "Invalid SHA-256 for LAN receipt." }
        require(token.length in 32..128 && token.none { it.isISOControl() }) {
            "Invalid LAN receipt token."
        }
        val transferBytes = transferId.toByteArray(StandardCharsets.US_ASCII)
        val digestBytes = sha256Hex.lowercase().toByteArray(StandardCharsets.US_ASCII)
        val body = ByteArray(MAGIC.size + transferBytes.size + digestBytes.size)
        System.arraycopy(MAGIC, 0, body, 0, MAGIC.size)
        System.arraycopy(transferBytes, 0, body, MAGIC.size, transferBytes.size)
        System.arraycopy(digestBytes, 0, body, MAGIC.size + transferBytes.size, digestBytes.size)
        transferBytes.fill(0)
        digestBytes.fill(0)

        val tokenBytes = token.toByteArray(StandardCharsets.UTF_8)
        val macBytes = try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(tokenBytes, "HmacSHA256"))
            mac.doFinal(body)
        } finally {
            tokenBytes.fill(0)
        }

        return try {
            ByteArray(body.size + macBytes.size).also { result ->
                System.arraycopy(body, 0, result, 0, body.size)
                System.arraycopy(macBytes, 0, result, body.size, macBytes.size)
            }
        } finally {
            body.fill(0)
            macBytes.fill(0)
        }
    }

    fun verify(frame: ByteArray, transferId: String, sha256Hex: String, token: String): Boolean {
        val expected = try {
            create(transferId, sha256Hex, token)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return try {
            frame.size == expected.size && MessageDigest.isEqual(expected, frame)
        } finally {
            expected.fill(0)
        }
    }
}
