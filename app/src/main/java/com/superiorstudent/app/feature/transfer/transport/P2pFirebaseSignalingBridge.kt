package com.superiorstudent.app.feature.transfer.transport

import com.google.firebase.firestore.ListenerRegistration
import com.superiorstudent.app.feature.transfer.data.FirebaseP2pTransferSignaling
import com.superiorstudent.app.feature.transfer.data.P2pSignalType
import com.superiorstudent.app.feature.transfer.data.P2pTransferOffer
import com.superiorstudent.app.feature.transfer.data.P2pTransferSignal
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Routes a single transfer's trusted peer signals between Firestore and a peer controller.
 *
 * A bridge instance must only be created after validating that the current user is one of
 * the offer's two participants and that the offer was accepted. Signal sender/target identity
 * is checked again before parsing or applying the payload.
 *
 * This component is signaling-only. It never carries file chunks and does not authenticate
 * public keys by itself; end-to-end transfer must also compare the accepted offer's sender key
 * and complete a trusted ephemeral key exchange before deriving a data-encryption key.
 */
class P2pFirebaseSignalingBridge(
    private val signaling: FirebaseP2pTransferSignaling,
    private val offer: P2pTransferOffer,
    private val currentUid: String,
    private val remoteUid: String,
    private val controller: P2pPeerConnectionController,
    private val report: (String, Throwable?) -> Unit,
    private val onRemotePublicKey: (String) -> Unit = {}
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private var listenerRegistration: ListenerRegistration? = null
    private val processedSignals = HashSet<String>()
    private var remoteOfferSeen = false
    private var remoteAnswerSeen = false
    private var keyExchangeSeen = false

    init {
        require(offer.status.name == "ACCEPTED" || offer.status.name == "TRANSFERRING") {
            "Transfer must be accepted before WebRTC signaling starts."
        }
        require(currentUid.isNotBlank() && remoteUid.isNotBlank() && currentUid != remoteUid) {
            "The two authenticated participant IDs are required."
        }
        require(setOf(currentUid, remoteUid) == setOf(offer.senderUid, offer.receiverUid)) {
            "Signaling participants do not match the accepted offer."
        }
    }

    fun startListening() {
        check(!closed.get()) { "Signaling bridge is closed." }
        if (listenerRegistration != null) return
        listenerRegistration = signaling.listenForSignals(offer.conversationId, offer.transferId) { signal, error ->
            if (closed.get()) return@listenForSignals
            if (error != null) {
                report("Could not listen for P2P signaling.", error)
                return@listenForSignals
            }
            consume(signal)
        }
    }

    /** Receiver sends its ephemeral public key to the authenticated sender after accepting an offer. */
    fun publishKeyExchange(publicKeyBase64: String) {
        if (closed.get()) return
        require(currentUid == offer.receiverUid && remoteUid == offer.senderUid) {
            "Only the accepted receiver may publish the receiver-side key exchange."
        }
        require(publicKeyBase64.isNotBlank() && publicKeyBase64.length <= 2048) {
            "Public key payload is invalid."
        }
        signaling.sendSignal(
            conversationId = offer.conversationId,
            transferId = offer.transferId,
            targetUid = remoteUid,
            type = P2pSignalType.KEY_EXCHANGE,
            payload = mapOf("publicKey" to publicKeyBase64)
        ) { _, error ->
            if (error != null && !closed.get()) report("Could not publish ephemeral key exchange.", error)
        }
    }

    /** Called by the controller for each local SDP or ICE signal. */
    fun publish(type: P2pPeerConnectionController.SignalType, payload: Map<String, Any?>) {
        if (closed.get()) return
        val wireType = when (type) {
            P2pPeerConnectionController.SignalType.WEBRTC_OFFER -> P2pSignalType.WEBRTC_OFFER
            P2pPeerConnectionController.SignalType.WEBRTC_ANSWER -> P2pSignalType.WEBRTC_ANSWER
            P2pPeerConnectionController.SignalType.ICE_CANDIDATE -> P2pSignalType.ICE_CANDIDATE
        }
        signaling.sendSignal(
            conversationId = offer.conversationId,
            transferId = offer.transferId,
            targetUid = remoteUid,
            type = wireType,
            payload = payload
        ) { _, error ->
            if (error != null && !closed.get()) report("Could not publish P2P signaling.", error)
        }
    }

    private fun consume(signal: P2pTransferSignal) {
        if (signal.signalId.isBlank() || !processedSignals.add(signal.signalId)) return
        if (signal.transferId != offer.transferId ||
            signal.targetUid != currentUid ||
            signal.senderUid != remoteUid ||
            signal.senderUid == currentUid
        ) {
            report("Discarded a signaling message with a mismatched peer identity.", null)
            return
        }

        try {
            require(com.superiorstudent.app.feature.transfer.data.P2pTransferValidation.isValidSignalPayload(signal.type, signal.payload)) {
                "Signaling payload failed model validation."
            }
            when (signal.type) {
                P2pSignalType.WEBRTC_OFFER -> {
                    require(!remoteOfferSeen && currentUid == offer.receiverUid) {
                        "Unexpected or duplicate SDP offer."
                    }
                    val sdp = signal.payload["sdp"] as? String
                        ?: throw IllegalArgumentException("SDP offer is missing.")
                    remoteOfferSeen = true
                    if (!controller.acceptOffer(sdp)) {
                        throw IllegalArgumentException("PeerConnection refused the SDP offer.")
                    }
                }
                P2pSignalType.WEBRTC_ANSWER -> {
                    require(!remoteAnswerSeen && currentUid == offer.senderUid) {
                        "Unexpected or duplicate SDP answer."
                    }
                    val sdp = signal.payload["sdp"] as? String
                        ?: throw IllegalArgumentException("SDP answer is missing.")
                    remoteAnswerSeen = true
                    if (!controller.acceptAnswer(sdp)) {
                        throw IllegalArgumentException("PeerConnection refused the SDP answer.")
                    }
                }
                P2pSignalType.ICE_CANDIDATE -> {
                    if (!controller.addRemoteIceCandidate(signal.payload)) {
                        throw IllegalArgumentException("PeerConnection refused the ICE candidate.")
                    }
                }
                P2pSignalType.KEY_EXCHANGE -> {
                    require(currentUid == offer.senderUid && remoteUid == offer.receiverUid) {
                        "Receiver-side ephemeral key may only be consumed by the accepted sender."
                    }
                    val publicKey = signal.payload["publicKey"] as? String
                        ?: throw IllegalArgumentException("Peer public key is missing.")
                    require(publicKey.length <= 2048 && publicKey.none { it.isISOControl() }) {
                        "Peer public key is invalid."
                    }
                    onRemotePublicKey(publicKey)
                    keyExchangeSeen = true
                }
                else -> Unit
            }
            signaling.acknowledgeSignal(offer.conversationId, offer.transferId, signal.signalId) { ackError ->
                if (ackError != null && !closed.get()) report("Could not acknowledge signaling message.", ackError)
            }
        } catch (error: Exception) {
            report("Rejected P2P signaling message.", error)
        }
    }

    fun hasSeenKeyExchange(): Boolean = keyExchangeSeen

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        listenerRegistration?.remove()
        listenerRegistration = null
        synchronized(processedSignals) { processedSignals.clear() }
    }
}
