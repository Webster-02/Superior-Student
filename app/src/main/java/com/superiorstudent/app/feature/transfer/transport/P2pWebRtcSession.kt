package com.superiorstudent.app.feature.transfer.transport

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.DataChannel
import com.superiorstudent.app.feature.transfer.data.P2pSignalType
import com.superiorstudent.app.feature.transfer.data.P2pTransferValidation
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns one WebRTC file-transfer session and wires SDP/ICE handling to the binary DataChannel.
 *
 * The caller remains responsible for authenticated Firebase signaling and supplying trusted ICE
 * servers (including short-lived TURN credentials). File bytes must still be encrypted and
 * authenticated by P2pEncryptedChunkStream before this transport sends them.
 */
class P2pWebRtcSession(
    context: Context,
    iceServers: List<PeerConnection.IceServer>,
    private val callback: Callback
) : AutoCloseable {

    interface Callback {
        fun onLocalSignal(type: P2pPeerConnectionController.SignalType, payload: Map<String, Any?>)
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
        fun onError(message: String, error: Throwable? = null)
    }

    private enum class Role { OFFERER, ANSWERER }

    private val closed = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var role: Role? = null
    private val pendingRemoteCandidates = mutableListOf<Map<String, Any?>>()

    // The controller owns and disposes the PeerConnection. The transport owns only the channel.
    private val transport = P2pDataChannelTransport(
        callback = object : P2pDataChannelTransport.Callback {
            override fun onBinaryFrame(frame: ByteArray) {
                dispatch { callback.onBinaryFrame(frame) }
            }

            override fun onStateChanged(state: P2pDataChannelTransport.State, message: String?) {
                dispatch { callback.onDataChannelStateChanged(state, message) }
            }

            // ICE candidates originate from the PeerConnection controller so they are signaled once.
            override fun onLocalIceCandidate(candidate: IceCandidate) = Unit

            override fun onBufferedAmountChanged(bufferedBytes: Long) {
                dispatch { callback.onBufferedAmountChanged(bufferedBytes) }
            }
        },
        closePeerConnectionOnClose = false
    )

    private val controller = P2pPeerConnectionController(
        context = context.applicationContext,
        iceServers = iceServers,
        listener = object : P2pPeerConnectionController.Listener {
            override fun onLocalSignal(
                type: P2pPeerConnectionController.SignalType,
                payload: Map<String, Any?>
            ) {
                dispatch { callback.onLocalSignal(type, payload) }
            }

            override fun onStateChanged(
                state: PeerConnection.PeerConnectionState,
                detail: String?
            ) {
                dispatch { callback.onPeerConnectionStateChanged(state, detail) }
            }

            override fun onRemoteDataChannel(channel: DataChannel) {
                transport.onRemoteDataChannel(channel)
            }

            override fun onPeerConnectionCreated(
                connection: PeerConnection,
                offerer: Boolean
            ): Boolean {
                if (closed.get()) return false
                val expectedRole = if (offerer) Role.OFFERER else Role.ANSWERER
                if (role != expectedRole) {
                    dispatch { callback.onError("WebRTC session role did not match the negotiated connection.") }
                    return false
                }
                return try {
                    transport.attachPeerConnection(connection, offerer)
                } catch (error: RuntimeException) {
                    dispatch { callback.onError("Could not attach the file DataChannel.", error) }
                    false
                }
            }

            override fun onError(message: String, error: Throwable?) {
                dispatch { callback.onError(message, error) }
            }
        }
    )

    /** Starts one offerer session. The DataChannel is created before SDP is generated. */
    @Synchronized
    fun startAsOfferer(): Boolean {
        if (closed.get() || role != null) return false
        role = Role.OFFERER
        val started = controller.startAsOfferer()
        if (!started && controller.currentConnectionState() == null) role = null
        if (started) flushPendingRemoteCandidates()
        return started
    }

    /** Accepts the remote offer and automatically creates/signals the SDP answer. */
    @Synchronized
    fun acceptOffer(sdp: String): Boolean {
        if (closed.get() || role != null) return false
        role = Role.ANSWERER
        val accepted = controller.acceptOffer(sdp)
        if (!accepted && controller.currentConnectionState() == null) role = null
        if (accepted) flushPendingRemoteCandidates()
        return accepted
    }

    fun acceptAnswer(sdp: String): Boolean =
        !closed.get() && role == Role.OFFERER && controller.acceptAnswer(sdp)

    @Synchronized
    fun addRemoteIceCandidate(payload: Map<String, Any?>): Boolean {
        if (closed.get() || !P2pTransferValidation.isValidSignalPayload(P2pSignalType.ICE_CANDIDATE, payload)) {
            return false
        }
        // Firestore snapshots may deliver ICE before the SDP offer. Hold only a bounded number
        // until the role creates the PeerConnection; the controller then queues them until SDP.
        if (role != null) return controller.addRemoteIceCandidate(payload)
        synchronized(pendingRemoteCandidates) {
            if (pendingRemoteCandidates.size >= MAX_PENDING_REMOTE_CANDIDATES) return false
            pendingRemoteCandidates.add(payload.toMap())
        }
        return true
    }

    private fun flushPendingRemoteCandidates() {
        val candidates = synchronized(pendingRemoteCandidates) {
            pendingRemoteCandidates.toList().also { pendingRemoteCandidates.clear() }
        }
        candidates.forEach { candidate ->
            if (!controller.addRemoteIceCandidate(candidate)) {
                dispatch { callback.onError("Could not apply a queued remote ICE candidate.") }
            }
        }
    }

    /** Sends one already-encrypted binary frame; false means retry later or transport not ready. */
    fun sendEncryptedFrame(frame: ByteArray): Boolean =
        !closed.get() && role != null && transport.sendBinaryFrame(frame)

    fun currentConnectionState(): PeerConnection.PeerConnectionState? =
        if (closed.get()) null else controller.currentConnectionState()

    fun currentDataChannelState(): P2pDataChannelTransport.State = transport.currentState()

    private fun dispatch(action: () -> Unit) {
        mainHandler.post {
            if (!closed.get()) action()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Close the channel first; the controller then closes and disposes the PeerConnection.
        synchronized(pendingRemoteCandidates) { pendingRemoteCandidates.clear() }
        transport.close()
        controller.close()
    }

    private companion object {
        const val MAX_PENDING_REMOTE_CANDIDATES = 256
    }
}
