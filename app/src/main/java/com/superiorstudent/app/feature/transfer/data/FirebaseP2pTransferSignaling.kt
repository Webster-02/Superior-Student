package com.superiorstudent.app.feature.transfer.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Firestore transports small signaling metadata only. File bytes must travel through
 * encrypted LAN HTTPS, a WebRTC DataChannel, or TURN.
 *
 * Server-side Firestore rules must be deployed before testing this adapter.
 */
class FirebaseP2pTransferSignaling(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun createOffer(
        draft: P2pTransferOfferDraft,
        callback: (P2pTransferOffer?, Exception?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified) {
            callback(null, IllegalStateException("A verified Student Connect account is required."))
            return
        }
        val reason = validateDraft(user.uid, draft)
        if (reason != null) {
            callback(null, IllegalArgumentException(reason))
            return
        }

        val reference = offerRef(draft.conversationId, draft.transferId)
        val data = hashMapOf<String, Any>(
            "transferId" to draft.transferId,
            "conversationId" to draft.conversationId,
            "senderUid" to user.uid,
            "receiverUid" to draft.receiverUid,
            "fileName" to draft.fileName,
            "sizeBytes" to draft.sizeBytes,
            "mimeType" to draft.mimeType,
            "sha256" to draft.sha256.lowercase(),
            "chunkSize" to draft.chunkSize,
            "senderPublicKey" to draft.senderPublicKeyBase64,
            "status" to P2pOfferState.OFFERED.wireValue,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        draft.thumbnailBase64?.let { data["thumbnailBase64"] = it }

        // Do not pre-read a missing offer: strict Firestore rules only permit get
        // for a valid existing participant offer. A direct set is checked as create
        // when the document is absent. If the ID already exists, the strict update
        // rule rejects replacing its full metadata, so duplicate protection remains.
        reference.set(data)
            .addOnSuccessListener {
                reference.get()
                    .addOnSuccessListener { saved ->
                        val offer = saved.toP2pTransferOffer()
                        if (offer == null) callback(null, IllegalStateException("Saved offer metadata failed validation."))
                        else callback(offer, null)
                    }
                    .addOnFailureListener { callback(null, it) }
            }
            .addOnFailureListener { callback(null, it) }
    }

    /** Firestore preserves small offer metadata while a receiver is offline. */
    fun listenForIncomingOffers(
        conversationId: String,
        onChanged: (List<P2pTransferOffer>, Exception?) -> Unit
    ): ListenerRegistration? {
        val uid = auth.currentUser?.takeIf { it.isEmailVerified }?.uid
        if (uid.isNullOrBlank()) {
            onChanged(emptyList(), IllegalStateException("Sign in with a verified Student Connect account first."))
            return null
        }
        return firestore.collection(CONVERSATIONS).document(conversationId)
            .collection(TRANSFER_OFFERS)
            .whereEqualTo("receiverUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onChanged(emptyList(), error)
                    return@addSnapshotListener
                }
                val offers = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toP2pTransferOffer() }
                    .filter { it.receiverUid == uid && it.status == P2pOfferState.OFFERED }
                onChanged(offers, null)
            }
    }

    fun listenForOffer(
        conversationId: String,
        transferId: String,
        onChanged: (P2pTransferOffer?, Exception?) -> Unit
    ): ListenerRegistration? {
        if (auth.currentUser?.let { it.isEmailVerified } != true) {
            onChanged(null, IllegalStateException("Sign in with a verified Student Connect account first."))
            return null
        }
        return offerRef(conversationId, transferId).addSnapshotListener { snapshot, error ->
            if (error != null) onChanged(null, error)
            else {
                val offer = snapshot?.takeIf { it.exists() }?.toP2pTransferOffer()
                onChanged(offer, if (snapshot?.exists() == true && offer == null) {
                    IllegalStateException("Transfer offer metadata is invalid.")
                } else null)
            }
        }
    }

    fun acceptOffer(conversationId: String, transferId: String, callback: (Exception?) -> Unit) =
        updateStatus(conversationId, transferId, P2pOfferState.ACCEPTED, callback)

    fun rejectOffer(conversationId: String, transferId: String, callback: (Exception?) -> Unit) =
        updateStatus(conversationId, transferId, P2pOfferState.REJECTED, callback)

    fun startTransfer(conversationId: String, transferId: String, callback: (Exception?) -> Unit) =
        updateStatus(conversationId, transferId, P2pOfferState.TRANSFERRING, callback)

    fun pauseTransfer(conversationId: String, transferId: String, callback: (Exception?) -> Unit) =
        updateStatus(conversationId, transferId, P2pOfferState.PAUSED, callback)

    fun completeTransfer(conversationId: String, transferId: String, callback: (Exception?) -> Unit) =
        updateStatus(conversationId, transferId, P2pOfferState.COMPLETED, callback)

    fun failTransfer(conversationId: String, transferId: String, callback: (Exception?) -> Unit) =
        updateStatus(conversationId, transferId, P2pOfferState.FAILED, callback)

    fun cancelTransfer(conversationId: String, transferId: String, callback: (Exception?) -> Unit) =
        updateStatus(conversationId, transferId, P2pOfferState.CANCELLED, callback)

    fun sendSignal(
        conversationId: String,
        transferId: String,
        targetUid: String,
        type: P2pSignalType,
        payload: Map<String, Any?>,
        callback: (String?, Exception?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified) {
            callback(null, IllegalStateException("A verified Student Connect account is required."))
            return
        }
        if (targetUid.isBlank() || targetUid == user.uid ||
            !P2pTransferValidation.isValidTransferId(transferId) ||
            !P2pTransferValidation.isValidSignalPayload(type, payload)
        ) {
            callback(null, IllegalArgumentException("Signaling payload or destination is invalid."))
            return
        }

        val ref = offerRef(conversationId, transferId).collection(SIGNALS).document()
        ref.set(mapOf(
            "transferId" to transferId,
            "senderUid" to user.uid,
            "targetUid" to targetUid,
            "type" to type.wireValue,
            "payload" to payload,
            "createdAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener { callback(ref.id, null) }
            .addOnFailureListener { callback(null, it) }
    }

    /**
     * Query is filtered by targetUid to match Firestore rules. Acknowledge a signal
     * only after the consuming handler has successfully applied it.
     */
    fun listenForSignals(
        conversationId: String,
        transferId: String,
        onSignal: (P2pTransferSignal, Exception?) -> Unit
    ): ListenerRegistration? {
        val uid = auth.currentUser?.takeIf { it.isEmailVerified }?.uid
        if (uid.isNullOrBlank()) {
            onSignal(P2pTransferSignal("", transferId, "", "", P2pSignalType.ERROR, emptyMap(), null),
                IllegalStateException("A verified Student Connect account is required."))
            return null
        }
        return offerRef(conversationId, transferId).collection(SIGNALS)
            .whereEqualTo("targetUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSignal(P2pTransferSignal("", transferId, "", uid, P2pSignalType.ERROR, emptyMap(), null), error)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.filter { it.type.name == "ADDED" }?.forEach { change ->
                    val signal = change.document.toP2pTransferSignal() ?: return@forEach
                    if (signal.targetUid == uid && signal.transferId == transferId) onSignal(signal, null)
                }
            }
    }

    fun acknowledgeSignal(
        conversationId: String,
        transferId: String,
        signalId: String,
        callback: (Exception?) -> Unit
    ) {
        if (signalId.isBlank()) {
            callback(IllegalArgumentException("Signal ID is required."))
            return
        }
        offerRef(conversationId, transferId).collection(SIGNALS).document(signalId)
            .delete()
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it) }
    }

    private fun updateStatus(
        conversationId: String,
        transferId: String,
        newState: P2pOfferState,
        callback: (Exception?) -> Unit
    ) {
        if (auth.currentUser?.let { it.isEmailVerified } != true) {
            callback(IllegalStateException("A verified Student Connect account is required."))
            return
        }
        if (!P2pTransferValidation.isValidTransferId(transferId)) {
            callback(IllegalArgumentException("Transfer ID is invalid."))
            return
        }
        offerRef(conversationId, transferId).update(
            mapOf("status" to newState.wireValue, "updatedAt" to FieldValue.serverTimestamp())
        ).addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it) }
    }

    private fun validateDraft(senderUid: String, draft: P2pTransferOfferDraft): String? {
        if (!P2pTransferValidation.isValidTransferId(draft.transferId)) return "Transfer ID is invalid."
        if (!P2pTransferValidation.isConversationIdForPair(draft.conversationId, senderUid, draft.receiverUid)) {
            return "Receiver must be the other participant in this conversation."
        }
        if (!P2pTransferValidation.isValidFileName(draft.fileName)) return "File name is invalid."
        if (draft.sizeBytes <= 0L) return "File size must be greater than zero."
        if (!P2pTransferValidation.isValidMimeType(draft.mimeType)) return "MIME type is invalid."
        if (!P2pTransferValidation.isValidSha256(draft.sha256)) return "SHA-256 must contain 64 hexadecimal characters."
        if (!P2pTransferValidation.isValidChunkSize(draft.chunkSize)) return "Chunk size must be between 1 KiB and 64 KiB."
        if (!P2pTransferValidation.isValidPublicKey(draft.senderPublicKeyBase64)) return "Sender public key is invalid."
        if (!P2pTransferValidation.isValidThumbnail(draft.thumbnailBase64)) return "Thumbnail metadata is too large or invalid."
        return null
    }

    private fun offerRef(conversationId: String, transferId: String): DocumentReference =
        firestore.collection(CONVERSATIONS).document(conversationId)
            .collection(TRANSFER_OFFERS).document(transferId)

    private fun DocumentSnapshot.toP2pTransferOffer(): P2pTransferOffer? {
        val transferId = getString("transferId") ?: return null
        val conversationId = getString("conversationId") ?: return null
        val senderUid = getString("senderUid") ?: return null
        val receiverUid = getString("receiverUid") ?: return null
        val fileName = getString("fileName") ?: return null
        val sizeBytes = getLong("sizeBytes") ?: return null
        val mimeType = getString("mimeType") ?: return null
        val sha256 = getString("sha256") ?: return null
        val chunkSize = getLong("chunkSize")?.toInt() ?: return null
        val publicKey = getString("senderPublicKey") ?: return null
        val status = P2pOfferState.fromWire(getString("status")) ?: return null
        val thumbnail = getString("thumbnailBase64")
        if (id != transferId || !P2pTransferValidation.isValidTransferId(transferId) ||
            !P2pTransferValidation.isValidFileName(fileName) || sizeBytes <= 0L ||
            !P2pTransferValidation.isValidMimeType(mimeType) ||
            !P2pTransferValidation.isValidSha256(sha256) ||
            !P2pTransferValidation.isValidChunkSize(chunkSize) ||
            !P2pTransferValidation.isValidPublicKey(publicKey) ||
            !P2pTransferValidation.isValidThumbnail(thumbnail) ||
            !P2pTransferValidation.isConversationIdForPair(conversationId, senderUid, receiverUid)
        ) return null
        return P2pTransferOffer(
            transferId, conversationId, senderUid, receiverUid, fileName, sizeBytes,
            mimeType, sha256, chunkSize, publicKey, thumbnail, status,
            getTimestamp("createdAt"), getTimestamp("updatedAt")
        )
    }

    private fun DocumentSnapshot.toP2pTransferSignal(): P2pTransferSignal? {
        val transferId = getString("transferId") ?: return null
        val senderUid = getString("senderUid") ?: return null
        val targetUid = getString("targetUid") ?: return null
        val type = P2pSignalType.fromWire(getString("type")) ?: return null
        @Suppress("UNCHECKED_CAST")
        val payload = get("payload") as? Map<String, Any?> ?: return null
        if (id.isBlank() || !P2pTransferValidation.isValidTransferId(transferId) ||
            senderUid.isBlank() || targetUid.isBlank() || senderUid == targetUid ||
            !P2pTransferValidation.isValidSignalPayload(type, payload)
        ) return null
        return P2pTransferSignal(id, transferId, senderUid, targetUid, type, payload, getTimestamp("createdAt"))
    }

    private companion object {
        const val CONVERSATIONS = "chatConversations"
        const val TRANSFER_OFFERS = "transferOffers"
        const val SIGNALS = "signals"
    }
}
