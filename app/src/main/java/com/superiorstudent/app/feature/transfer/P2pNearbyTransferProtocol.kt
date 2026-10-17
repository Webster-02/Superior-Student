package com.superiorstudent.app.feature.transfer

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Public metadata for an Internet-independent, same-LAN transfer.
 *
 * The six-digit confirmation code fingerprints the complete offer and both ephemeral public keys.
 * Users must compare the code on both devices before accepting; LAN discovery alone is not identity.
 */
internal data class P2pNearbyOffer(
    val transferId: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256: String,
    val chunkSize: Int,
    val senderPublicKeyBase64: String
)

internal object P2pNearbyTransferProtocol {
    const val CONTROL_PACKET: Int = 1
    const val DATA_PACKET: Int = 2
    const val MAX_CONTROL_BYTES: Int = 64 * 1024
    const val MAX_PACKET_BYTES: Int = 96 * 1024

    private val transferIdPattern = Regex("^[A-Za-z0-9_-]{16,80}$")
    private val digestPattern = Regex("^[A-Fa-f0-9]{64}$")
    private val publicKeyPattern = Regex("^[A-Za-z0-9+/]{40,128}={0,2}$")

    fun isValidOffer(offer: P2pNearbyOffer): Boolean =
        transferIdPattern.matches(offer.transferId) &&
            offer.fileName.isNotBlank() &&
            offer.fileName == offer.fileName.trim() &&
            offer.fileName.length <= 180 &&
            offer.fileName != "." &&
            offer.fileName != ".." &&
            offer.fileName.none { it == '/' || it == '\\' || it.isISOControl() } &&
            offer.sizeBytes > 0L &&
            offer.mimeType.isNotBlank() &&
            offer.mimeType.length <= 160 &&
            offer.mimeType.none { it.isISOControl() } &&
            digestPattern.matches(offer.sha256) &&
            offer.chunkSize in 1024..(64 * 1024) &&
            publicKeyPattern.matches(offer.senderPublicKeyBase64)

    /**
     * Returns a six-digit fingerprint for the complete offer transcript. This is not a replacement
     * for out-of-band comparison: both users must explicitly confirm that the displayed values match.
     */
    fun confirmationCode(
        offer: P2pNearbyOffer,
        receiverPublicKeyBase64: String
    ): String {
        require(isValidOffer(offer)) { "Invalid nearby offer." }
        require(publicKeyPattern.matches(receiverPublicKeyBase64)) { "Invalid receiver public key." }
        require(offer.senderPublicKeyBase64 != receiverPublicKeyBase64) {
            "Nearby peers must use different ephemeral keys."
        }

        val keys = listOf(offer.senderPublicKeyBase64, receiverPublicKeyBase64).sorted()
        val transcript = listOf(
            "SuperiorStudent/NearbyTransfer/v1",
            offer.transferId,
            offer.fileName,
            offer.sizeBytes.toString(),
            offer.mimeType,
            offer.sha256.lowercase(),
            offer.chunkSize.toString(),
            keys[0],
            keys[1]
        ).joinToString("|").toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(transcript)
        val code = BigInteger(1, digest.copyOfRange(0, 8))
            .mod(BigInteger.valueOf(1_000_000L))
            .toInt()
        digest.fill(0)
        transcript.fill(0)
        return code.toString().padStart(6, '0')
    }
}
