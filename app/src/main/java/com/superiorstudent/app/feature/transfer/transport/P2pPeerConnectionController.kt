package com.superiorstudent.app.feature.transfer.transport

import android.content.Context
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SDP/ICE controller for a single authenticated P2P transfer.
 *
 * Callbacks are intended to be bridged to FirebaseP2pTransferSignaling by the owner.
 * This class never embeds STUN/TURN passwords. ICE servers must be supplied by a trusted
 * issuer (TURN credentials should be short-lived) and the remote identity must be checked
 * against the accepted Student Connect conversation before signals are passed here.
 */
class P2pPeerConnectionController(
    context: Context,
    private val iceServers: List<PeerConnection.IceServer>,
    private val listener: Listener
) : AutoCloseable {

    interface Listener {
        fun onLocalSignal(type: SignalType, payload: Map<String, Any?>)
        fun onStateChanged(state: PeerConnection.PeerConnectionState, detail: String? = null)
        fun onLocalDataChannel(channel: DataChannel) = Unit
        fun onRemoteDataChannel(channel: DataChannel)
        fun onError(message: String, error: Throwable? = null)
    }

    enum class SignalType { WEBRTC_OFFER, WEBRTC_ANSWER, ICE_CANDIDATE }

    private val closed = AtomicBoolean(false)
    private val factory: PeerConnectionFactory = P2pWebRtcFactory.get(context)
    @Volatile private var peer: PeerConnection? = null
    @Volatile private var remoteDescriptionSet = false
    @Volatile private var pendingCandidates: MutableList<IceCandidate> = mutableListOf()
    @Volatile private var offerCreating = false
    @Volatile private var localChannelCreated = false

    fun startAsOfferer(): Boolean {
        if (!preparePeer()) return false
        val connection = peer ?: return false
        if (!localChannelCreated) {
            val init = DataChannel.Init().apply {
                ordered = true
                negotiated = false
            }
            val channel = connection.createDataChannel(P2pDataChannelTransport.CHANNEL_LABEL, init)
            if (channel == null) {
                listener.onError("WebRTC could not create the file DataChannel.")
                return false
            }
            localChannelCreated = true
            listener.onLocalDataChannel(channel)
        }
        offerCreating = true
        connection.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription) {
                if (closed.get()) return
                connection.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        if (!closed.get()) {
                            listener.onLocalSignal(SignalType.WEBRTC_OFFER, mapOf("sdp" to description.description))
                        }
                    }
                    override fun onCreateOrSetFailure(error: String) {
                        offerCreating = false
                        listener.onError("Could not set local SDP offer: $error")
                    }
                }, description)
            }
            override fun onCreateOrSetFailure(error: String) {
                offerCreating = false
                listener.onError("Could not create SDP offer: $error")
            }
        }, MediaConstraints())
        return true
    }

    fun acceptOffer(sdp: String): Boolean {
        if (sdp.isBlank() || sdp.length > MAX_SDP_CHARS) {
            listener.onError("Remote SDP offer is invalid.")
            return false
        }
        if (!preparePeer()) return false
        val connection = peer ?: return false
        val remote = SessionDescription(SessionDescription.Type.OFFER, sdp)
        connection.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                if (closed.get()) return
                remoteDescriptionSet = true
                flushPendingCandidates(connection)
                connection.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(description: SessionDescription) {
                        if (closed.get()) return
                        connection.setLocalDescription(object : SdpObserverAdapter() {
                            override fun onSetSuccess() {
                                if (!closed.get()) {
                                    listener.onLocalSignal(SignalType.WEBRTC_ANSWER, mapOf("sdp" to description.description))
                                }
                            }
                            override fun onCreateOrSetFailure(error: String) {
                                listener.onError("Could not set local SDP answer: $error")
                            }
                        }, description)
                    }
                    override fun onCreateOrSetFailure(error: String) {
                        listener.onError("Could not create SDP answer: $error")
                    }
                }, MediaConstraints())
            }
            override fun onCreateOrSetFailure(error: String) {
                listener.onError("Could not apply remote SDP offer: $error")
            }
        }, remote)
        return true
    }

    fun acceptAnswer(sdp: String): Boolean {
        if (sdp.isBlank() || sdp.length > MAX_SDP_CHARS) {
            listener.onError("Remote SDP answer is invalid.")
            return false
        }
        val connection = peer ?: run {
            listener.onError("Create an SDP offer before accepting an answer.")
            return false
        }
        if (connection.signalingState() != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            listener.onError("PeerConnection is not waiting for an SDP answer.")
            return false
        }
        val remote = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        connection.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                if (closed.get()) return
                remoteDescriptionSet = true
                flushPendingCandidates(connection)
            }
            override fun onCreateOrSetFailure(error: String) {
                listener.onError("Could not apply remote SDP answer: $error")
            }
        }, remote)
        return true
    }

    fun addRemoteIceCandidate(payload: Map<String, Any?>): Boolean {
        if (closed.get()) return false
        val candidate = payload["candidate"] as? String ?: return false
        val mid = payload["sdpMid"] as? String
        val index = (payload["sdpMLineIndex"] as? Number)?.toInt() ?: return false
        if (candidate.isBlank() || candidate.length > MAX_CANDIDATE_CHARS || index !in 0..256) return false
        val item = IceCandidate(mid, index, candidate)
        val connection = peer ?: return false
        if (!remoteDescriptionSet) {
            synchronized(pendingCandidates) {
                if (pendingCandidates.size >= MAX_PENDING_CANDIDATES) {
                    listener.onError("Too many ICE candidates arrived before SDP.")
                    return false
                }
                pendingCandidates.add(item)
            }
            return true
        }
        return try {
            connection.addIceCandidate(item)
        } catch (error: RuntimeException) {
            listener.onError("Could not add remote ICE candidate.", error)
            false
        }
    }

    fun currentConnectionState(): PeerConnection.PeerConnectionState? = peer?.connectionState()

    private fun preparePeer(): Boolean {
        if (closed.get()) return false
        if (peer != null) return true
        val configuration = PeerConnection.RTCConfiguration(iceServers).apply {
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                if (!closed.get()) listener.onStateChanged(state)
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                if (closed.get()) return
                listener.onLocalSignal(SignalType.ICE_CANDIDATE, mapOf(
                    "candidate" to candidate.sdp,
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex
                ))
            }
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) {
                if (!closed.get()) listener.onRemoteDataChannel(channel) else channel.close()
            }
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<org.webrtc.MediaStream>) = Unit
            override fun onTrack(transceiver: org.webrtc.RtpTransceiver) = Unit
        }
        val created = factory.createPeerConnection(configuration, observer)
        if (created == null) {
            listener.onError("WebRTC could not create a PeerConnection; check ICE server configuration.")
            return false
        }
        peer = created
        return true
    }

    private fun flushPendingCandidates(connection: PeerConnection) {
        val candidates = synchronized(pendingCandidates) {
            pendingCandidates.toList().also { pendingCandidates.clear() }
        }
        candidates.forEach { candidate ->
            runCatching { connection.addIceCandidate(candidate) }
                .onFailure { listener.onError("Failed to apply queued ICE candidate.", it) }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(pendingCandidates) { pendingCandidates.clear() }
        runCatching { peer?.close() }
        runCatching { peer?.dispose() }
        peer = null
        remoteDescriptionSet = false
    }

    private abstract class SdpObserverAdapter : org.webrtc.SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = onCreateOrSetFailure(error)
        override fun onSetFailure(error: String) = onCreateOrSetFailure(error)
        open fun onCreateOrSetFailure(error: String) = Unit
    }

    companion object {
        private const val MAX_SDP_CHARS = 120_000
        private const val MAX_CANDIDATE_CHARS = 4_096
        private const val MAX_PENDING_CANDIDATES = 256
    }
}
