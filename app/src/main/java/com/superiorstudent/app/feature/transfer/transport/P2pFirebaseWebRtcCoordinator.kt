package com.superiorstudent.app.feature.transfer.transport

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.superiorstudent.app.feature.transfer.data.FirebaseP2pTransferSignaling
import com.superiorstudent.app.feature.transfer.data.P2pOfferState
import com.superiorstudent.app.feature.transfer.data.P2pTransferOffer
import org.webrtc.PeerConnection
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Connects one already-accepted transfer offer to its authenticated Firestore SDP/ICE channel
 * and the WebRTC DataChannel session.
 *
 * This is transport setup only: callers must still perform the authenticated ECDH key exchange,
 * stream files through P2pEncryptedChunkStream, and verify the receiver's final SHA-256 digest.
 * TURN relay only works when trusted ICE servers with valid short-lived credentials are supplied.
 */
class P2pFirebaseWebRtcCoordinator(
    context: Context,
    private val signaling: FirebaseP2pTransferSignaling,
    private val offer: P2pTransferOffer,
    private val currentUid: String,
    iceServers: List<PeerConnection.IceServer>,
    private val callback: Callback
) : AutoCloseable {

    interface Callback {
        fun onPeerConnectionStateChanged(
            state: PeerConnection.PeerConnectionState,
            detail: String? = null
        )
        fun onDataChannelStateChanged(
            state: P2pDataChannelTransport.State,
            detail: String? = null
        )
        fun onBinaryFrame(frame: ByteArray)
        fun onBufferedAmountChanged(bufferedBytes: Long)
        fun onRemotePublicKey(publicKeyBase64: String)
        fun onRemoteLanCandidate(payload: Map<String, Any?>) = Unit
        fun onError(message: String, error: Throwable? = null)
    }

    private val closed = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var started = false
    private var bridge: P2pFirebaseSignalingBridge? = null

    private val session = P2pWebRtcSession(
        context = context.applicationContext,
        iceServers = iceServers,
        callback = object : P2pWebRtcSession.Callback {
            override fun onLocalSignal(
                type: P2pPeerConnectionController.SignalType,
                payload: Map<String, Any?>
            ) {
                val activeBridge = bridge
                if (activeBridge == null) {
                    report("WebRTC produced a signal before Firebase signaling was ready.")
                    return
                }
                activeBridge.publish(type, payload)
            }

            override fun onPeerConnectionStateChanged(
                state: PeerConnection.PeerConnectionState,
                detail: String?
            ) {
                dispatch { callback.onPeerConnectionStateChanged(state, detail) }
            }

            override fun onDataChannelStateChanged(
                state: P2pDataChannelTransport.State,
                detail: String?
            ) {
                dispatch { callback.onDataChannelStateChanged(state, detail) }
            }

            override fun onBinaryFrame(frame: ByteArray) {
                dispatch { callback.onBinaryFrame(frame) }
            }

            override fun onBufferedAmountChanged(bufferedBytes: Long) {
                dispatch { callback.onBufferedAmountChanged(bufferedBytes) }
            }

            override fun onError(message: String, error: Throwable?) {
                report(message, error)
            }
        }
    )

    init {
        val user = FirebaseAuth.getInstance().currentUser
        require(user != null && user.isEmailVerified && user.uid == currentUid) {
            "The supplied user must match the signed-in, email-verified Firebase account."
        }
        require(offer.status == P2pOfferState.ACCEPTED || offer.status == P2pOfferState.TRANSFERRING) {
            "Accept the transfer offer before starting WebRTC signaling."
        }
        require(currentUid == offer.senderUid || currentUid == offer.receiverUid) {
            "The current user is not a participant in this transfer."
        }
        val remoteUid = if (currentUid == offer.senderUid) offer.receiverUid else offer.senderUid
        bridge = P2pFirebaseSignalingBridge(
            signaling = signaling,
            offer = offer,
            currentUid = currentUid,
            remoteUid = remoteUid,
            session = session,
            onRemotePublicKey = { publicKey -> dispatch { callback.onRemotePublicKey(publicKey) } },
            onRemoteLanCandidate = { payload -> dispatch { callback.onRemoteLanCandidate(payload) } },
            report = { message, error -> report(message, error) }
        )
    }

    /**
     * Starts listening before generating an SDP offer to avoid missing early replies.
     * Only the original sender creates the offer; the receiver answers when it arrives.
     */
    @Synchronized
    fun start(): Boolean {
        if (closed.get() || started) return false
        val activeBridge = bridge ?: return false
        started = true
        try {
            activeBridge.startListening()
        } catch (error: RuntimeException) {
            started = false
            report("Could not start Firebase signaling.", error)
            return false
        }

        if (currentUid == offer.senderUid && !session.startAsOfferer()) {
            started = false
            activeBridge.close()
            report("Could not start the WebRTC offerer session.")
            return false
        }
        return true
    }

    /** Sends the receiver's ECDH public key over the authenticated Firestore signal channel. */
    fun publishReceiverPublicKey(publicKeyBase64: String, callback: (Exception?) -> Unit = {}) {
        if (closed.get() || !started) {
            callback(IllegalStateException("WebRTC transfer session is not active."))
            return
        }
        val activeBridge = bridge
        if (activeBridge == null) {
            callback(IllegalStateException("Firebase signaling bridge is unavailable."))
            return
        }
        activeBridge.publishReceiverPublicKey(publicKeyBase64, callback)
    }

    /** Publishes the receiver's short-lived LAN endpoint through authenticated Firestore signaling. */
    fun publishLanCandidate(ip: String, port: Int, token: String, callback: (Exception?) -> Unit = {}) {
        if (closed.get() || !started) {
            callback(IllegalStateException("WebRTC transfer session is not active."))
            return
        }
        val activeBridge = bridge
        if (activeBridge == null) {
            callback(IllegalStateException("Firebase signaling bridge is unavailable."))
            return
        }
        activeBridge.publishLanCandidate(ip, port, token, callback)
    }

    /** Sends exactly one already-encrypted frame. False means retry later or channel not ready. */
    fun sendEncryptedFrame(frame: ByteArray): Boolean =
        !closed.get() && started && session.sendEncryptedFrame(frame)

    fun currentPeerConnectionState(): PeerConnection.PeerConnectionState? =
        if (closed.get()) null else session.currentConnectionState()

    fun currentDataChannelState(): P2pDataChannelTransport.State = session.currentDataChannelState()

    private fun report(message: String, error: Throwable? = null) {
        dispatch { callback.onError(message, error) }
    }

    private fun dispatch(action: () -> Unit) {
        mainHandler.post {
            if (!closed.get()) action()
        }
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        bridge?.close()
        bridge = null
        session.close()
        started = false
    }
}
