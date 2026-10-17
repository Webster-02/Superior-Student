package com.superiorstudent.app.feature.transfer.data

import com.google.firebase.Timestamp
import java.util.UUID

enum class P2pOfferState(val wireValue: String) {
    OFFERED("offered"), ACCEPTED("accepted"), REJECTED("rejected"),
    TRANSFERRING("transferring"), PAUSED("paused"), COMPLETED("completed"),
    FAILED("failed"), CANCELLED("cancelled");

    companion object {
        fun fromWire(value: String?): P2pOfferState? = values().firstOrNull { it.wireValue == value }
    }
}

enum class P2pSignalType(val wireValue: String) {
    LAN_CANDIDATES("lan_candidates"), KEY_EXCHANGE("key_exchange"), WEBRTC_OFFER("webrtc_offer"),
    WEBRTC_ANSWER("webrtc_answer"), ICE_CANDIDATE("ice_candidate"),
    RESUME_REQUEST("resume_request"), TRANSFER_PROGRESS("transfer_progress"),
    TRANSFER_COMPLETE("transfer_complete"), TRANSFER_CANCEL("transfer_cancel"), ERROR("error");

    companion object {
        fun fromWire(value: String?): P2pSignalType? = values().firstOrNull { it.wireValue == value }
    }
}

data class P2pTransferOffer(
    val transferId: String, val conversationId: String, val senderUid: String,
    val receiverUid: String, val fileName: String, val sizeBytes: Long,
    val mimeType: String, val sha256: String, val chunkSize: Int,
    val senderPublicKeyBase64: String, val thumbnailBase64: String?,
    val status: P2pOfferState, val createdAt: Timestamp?, val updatedAt: Timestamp?
)

data class P2pTransferSignal(
    val signalId: String, val transferId: String, val senderUid: String,
    val targetUid: String, val type: P2pSignalType,
    val payload: Map<String, Any?>, val createdAt: Timestamp?
)

data class P2pTransferOfferDraft(
    val conversationId: String, val receiverUid: String, val fileName: String,
    val sizeBytes: Long, val mimeType: String, val sha256: String,
    val chunkSize: Int = 64 * 1024, val senderPublicKeyBase64: String,
    val thumbnailBase64: String? = null, val transferId: String = UUID.randomUUID().toString()
)

/** Pure validation shared by the repository and unit tests. */
internal object P2pTransferValidation {
    private val idRegex = Regex("^[A-Za-z0-9_-]{16,80}$")
    private val hashRegex = Regex("^[A-Fa-f0-9]{64}$")
    private val publicKeyRegex = Regex("^[A-Za-z0-9+/]{40,128}={0,2}$")

    fun isValidTransferId(value: String): Boolean = idRegex.matches(value)
    fun isValidFileName(value: String): Boolean =
        value.isNotBlank() && value == value.trim() && value.length <= 180 &&
            value != "." && value != ".." && value.none { it == '/' || it == '\\' || it.isISOControl() }
    fun isValidMimeType(value: String): Boolean =
        value.isNotBlank() && value.length <= 160 && value.none { it.isISOControl() }
    fun isValidSha256(value: String): Boolean = hashRegex.matches(value)
    fun isValidPublicKey(value: String): Boolean = publicKeyRegex.matches(value)
    fun isValidThumbnail(value: String?): Boolean =
        value == null || (value.length <= 20 * 1024 && value.all { it.isLetterOrDigit() || it in "+/=\r\n" })

    fun isConversationIdForPair(conversationId: String, firstUid: String, secondUid: String): Boolean {
        if (firstUid.isBlank() || secondUid.isBlank() || firstUid == secondUid) return false
        return conversationId == listOf(firstUid, secondUid).sorted().joinToString("_")
    }

    fun isPrivateLanAddress(value: String): Boolean {
        val octets = value.split('.')
        if (octets.size == 4 && octets.all { part -> part.isNotEmpty() && part.all { it.isDigit() } }) {
            val parts = octets.map { it.toIntOrNull() ?: return false }
            if (parts.any { it !in 0..255 }) return false
            return parts[0] == 10 ||
                (parts[0] == 192 && parts[1] == 168) ||
                (parts[0] == 172 && parts[1] in 16..31) ||
                (parts[0] == 169 && parts[1] == 254)
        }
        val normalized = value.lowercase()
        return ':' in normalized &&
            (normalized.startsWith("fc") || normalized.startsWith("fd") || normalized.startsWith("fe80:"))
    }

    fun isValidSignalPayload(type: P2pSignalType, payload: Map<String, Any?>): Boolean {
        fun hasExactly(vararg keys: String): Boolean = payload.keys == keys.toSet()
        fun text(key: String, max: Int): Boolean {
            val value = payload[key] as? String ?: return false
            return value.isNotBlank() && value.length <= max && value.none { it.isISOControl() }
        }
        fun nonNegativeLong(key: String): Long? =
            (payload[key] as? Number)?.toLong()?.takeIf { it >= 0L }

        return when (type) {
            P2pSignalType.LAN_CANDIDATES -> {
                val port = (payload["port"] as? Number)?.toInt()
                val token = payload["token"] as? String
                hasExactly("ip", "port", "token") &&
                    (payload["ip"] as? String)?.let { isPrivateLanAddress(it) } == true &&
                    port != null && port in 1..65535 &&
                    token != null && token.length in 32..128 && token.none { it.isISOControl() }
            }
            P2pSignalType.KEY_EXCHANGE ->
                hasExactly("publicKey") && text("publicKey", 2048)
            P2pSignalType.WEBRTC_OFFER, P2pSignalType.WEBRTC_ANSWER ->
                hasExactly("sdp") && text("sdp", 120_000)
            P2pSignalType.ICE_CANDIDATE -> {
                val lineIndex = (payload["sdpMLineIndex"] as? Number)?.toInt()
                hasExactly("candidate", "sdpMid", "sdpMLineIndex") &&
                    text("candidate", 4_096) &&
                    (payload["sdpMid"] == null || text("sdpMid", 128)) &&
                    lineIndex != null && lineIndex in 0..256
            }
            P2pSignalType.RESUME_REQUEST -> {
                val bitmap = payload["bitmapBase64"] as? String
                hasExactly("bitmapBase64", "fileSha256") &&
                    bitmap != null && bitmap.length <= 80_000 &&
                    bitmap.all { it.isLetterOrDigit() || it in "+/=\r\n" } &&
                    (payload["fileSha256"] as? String)?.let { isValidSha256(it) } == true
            }
            P2pSignalType.TRANSFER_PROGRESS -> {
                val done = nonNegativeLong("bytesDone")
                val total = nonNegativeLong("totalBytes")
                val speed = nonNegativeLong("bytesPerSecond")
                val eta = nonNegativeLong("etaSeconds")
                hasExactly("bytesDone", "totalBytes", "bytesPerSecond", "etaSeconds") &&
                    done != null && total != null && speed != null && eta != null &&
                    total > 0L && done <= total
            }
            P2pSignalType.TRANSFER_COMPLETE ->
                hasExactly("sha256") && (payload["sha256"] as? String)?.let { isValidSha256(it) } == true
            P2pSignalType.TRANSFER_CANCEL ->
                hasExactly("reason") && (payload["reason"] as? String)?.length?.let { it <= 200 } == true
            P2pSignalType.ERROR ->
                hasExactly("code", "message") && text("code", 80) && text("message", 300)
        }
    }
}
