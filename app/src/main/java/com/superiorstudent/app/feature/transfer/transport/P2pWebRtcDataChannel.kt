package com.superiorstudent.app.feature.transfer.transport

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * WebRTC binary DataChannel transport for encrypted P2P transfer frames.
 *
 * This component does not upload file bytes to Firebase. The caller signals the
 * local SDP and ICE candidates through FirebaseP2pTransferSignaling, then feeds
 * incoming binary frames to P2pEncryptedChunkStream. Key exchange and file
 * acceptance must be completed by the caller before any frame is accepted.
 *
 * No STUN/TURN servers or credentials are hard-coded. Pass trusted ICE server
 * configuration; TURN credentials should be short-lived and obtained from a
 * trusted issuer, not stored in the APK or Firestore rules.
 */
class P2pWebRtcDataChannel(
    context: Context,
    iceServers: List<PeerConnection.IceServer>,
    private val listener: Listener
) : AutoCloseable {

    interface Listener {
        fun onLocalDescription(type: SessionDescription.Type, sdp: String)
        fun onLocalIceCandidate(candidate: IceCandidate)
        fun onConnectionStateChanged(state: String)
        fun onDataChannelStateChanged(state: String)
        fun onBinaryFrameReceived(frame: ByteArray)
        fun onBufferedAmountChanged(bufferedBytes: Long)
        fun onFailure(message: String)
    }

    enum class SendResult { SENT, NOT_READY, BACKPRESSURE, REJECTED, CLOSED }

    private data class QueuedCandidate(
        val sdpMid: String?,
        val sdpMLineIndex: Int,
        val candidate: String
    )

    private val appContext = context.applicationContext
    private val rtcHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val configuredIceServers = iceServers.toList()

    @Volatile private var closed = false
    @Volatile private var peerConnection: PeerConnection? = null
    @Volatile private var dataChannel: DataChannel? = null
    private var peerObserver: PeerConnection.Observer? = null
    private var channelObserver: DataChannel.Observer? = null
    private var remoteDescriptionReady = false
    private val queuedRemoteCandidates = mutableListOf<QueuedCandidate>()

    fun createOffer() = postToRtcThread {
        if (closed) return@postToRtcThread
        if (peerConnection != null) {
            fail("A WebRTC session is already open.")
            return@postToRtcThread
        }
        val peer = createPeerConnection() ?: return@postToRtcThread
        val init = DataChannel.Init().apply { ordered = true }
        val channel = peer.createDataChannel(DATA_CHANNEL_LABEL, init)
        if (channel == null) {
            fail("Could not create the WebRTC data channel.")
            closeOnRtcThread()
            return@postToRtcThread
        }
        attachDataChannel(channel)
        peer.createOffer(sdpObserver(
            onCreateSuccess = { description ->
                peer.setLocalDescription(sdpObserver(
                    onSetSuccess = {
                        listener.onLocalDescription(description.type, description.description)
                    },
                    onFailure = { fail("Could not set local WebRTC offer: $it") }
                ), description)
            },
            onFailure = { fail("Could not create WebRTC offer: $it") }
        ), MediaConstraints())
    }

    /** Called by the offer recipient after receiving an authenticated SDP offer. */
    fun acceptOffer(offerSdp: String) = postToRtcThread {
        if (closed) return@postToRtcThread
        if (offerSdp.isBlank() || offerSdp.length > MAX_SDP_CHARS) {
            fail("The remote WebRTC offer is invalid.")
            return@postToRtcThread
        }
        if (peerConnection != null) {
            fail("A WebRTC session is already open.")
            return@postToRtcThread
        }
        val peer = createPeerConnection() ?: return@postToRtcThread
        val offer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peer.setRemoteDescription(sdpObserver(
            onSetSuccess = {
                remoteDescriptionReady = true
                flushQueuedRemoteCandidates()
                peer.createAnswer(sdpObserver(
                    onCreateSuccess = { answer ->
                        peer.setLocalDescription(sdpObserver(
                            onSetSuccess = {
                                listener.onLocalDescription(answer.type, answer.description)
                            },
                            onFailure = { fail("Could not set local WebRTC answer: $it") }
                        ), answer)
                    },
                    onFailure = { fail("Could not create WebRTC answer: $it") }
                ), MediaConstraints())
            },
            onFailure = { fail("Could not apply remote WebRTC offer: $it") }
        ), offer)
    }

    /** Called by the offer creator after receiving the peer's authenticated SDP answer. */
    fun acceptAnswer(answerSdp: String) = postToRtcThread {
        if (closed) return@postToRtcThread
        val peer = peerConnection
        if (peer == null || answerSdp.isBlank() || answerSdp.length > MAX_SDP_CHARS) {
            fail("The WebRTC answer is invalid or the session is not ready.")
            return@postToRtcThread
        }
        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peer.setRemoteDescription(sdpObserver(
            onSetSuccess = {
                remoteDescriptionReady = true
                flushQueuedRemoteCandidates()
            },
            onFailure = { fail("Could not apply remote WebRTC answer: $it") }
        ), answer)
    }

    /** ICE candidates may arrive before setRemoteDescription completes, so queue them safely. */
    fun addRemoteIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidateSdp: String) =
        postToRtcThread {
            if (closed) return@postToRtcThread
            if (candidateSdp.isBlank() || candidateSdp.length > MAX_ICE_CANDIDATE_CHARS ||
                sdpMLineIndex !in 0..MAX_SDP_MLINE_INDEX ||
                (sdpMid != null && sdpMid.length > MAX_SDP_MID_CHARS)
            ) {
                fail("A remote ICE candidate is invalid.")
                return@postToRtcThread
            }
            val candidate = QueuedCandidate(sdpMid, sdpMLineIndex, candidateSdp)
            if (!remoteDescriptionReady || peerConnection == null) {
                if (queuedRemoteCandidates.size >= MAX_QUEUED_ICE_CANDIDATES) {
                    fail("Too many ICE candidates arrived before the remote description.")
                    return@postToRtcThread
                }
                queuedRemoteCandidates += candidate
            } else {
                addCandidateNow(candidate)
            }
        }

    /**
     * Sends one already-encrypted binary frame. BACKPRESSURE means the caller must
     * retain the frame and retry after onBufferedAmountChanged falls below the high-water mark.
     */
    fun sendBinaryFrame(frame: ByteArray): SendResult {
        if (closed) return SendResult.CLOSED
        if (frame.isEmpty() || frame.size > MAX_BINARY_FRAME_BYTES) return SendResult.REJECTED
        val channel = dataChannel ?: return SendResult.NOT_READY
        return try {
            if (channel.state() != DataChannel.State.OPEN) return SendResult.NOT_READY
            if (channel.bufferedAmount() >= MAX_BUFFERED_BYTES) return SendResult.BACKPRESSURE
            val accepted = channel.send(DataChannel.Buffer(ByteBuffer.wrap(frame.copyOf()), true))
            if (accepted) SendResult.SENT else SendResult.BACKPRESSURE
        } catch (error: Exception) {
            fail("Could not send the encrypted frame: ${error.message ?: "unknown error"}")
            SendResult.REJECTED
        }
    }

    fun isOpen(): Boolean = !closed && dataChannel?.state() == DataChannel.State.OPEN

    fun bufferedAmountBytes(): Long = runCatching { dataChannel?.bufferedAmount() ?: 0L }.getOrDefault(0L)

    override fun close() {
        if (closed) return
        closed = true
        postToRtcThread { closeOnRtcThread() }
    }

    private fun createPeerConnection(): PeerConnection? {
        val factory = getFactory() ?: return null
        val observer = createPeerObserver()
        peerObserver = observer
        val configuration = PeerConnection.RTCConfiguration(configuredIceServers).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        val peer = try {
            factory.createPeerConnection(configuration, observer)
        } catch (error: Exception) {
            fail("Could not create a WebRTC peer connection: ${error.message ?: "unknown error"}")
            null
        }
        if (peer == null) {
            fail("The WebRTC SDK could not create a peer connection.")
            return null
        }
        peerConnection = peer
        remoteDescriptionReady = false
        return peer
    }

    private fun getFactory(): PeerConnectionFactory? {
        synchronized(FACTORY_LOCK) {
            if (sharedFactory != null) return sharedFactory
            return try {
                val options = PeerConnectionFactory.InitializationOptions
                    .builder(appContext)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                PeerConnectionFactory.builder().createPeerConnectionFactory().also {
                    sharedFactory = it
                }
            } catch (error: Exception) {
                fail("Could not initialize WebRTC: ${error.message ?: "unknown error"}")
                null
            }
        }
    }

    /**
     * A Proxy observer is used intentionally because newer WebRTC SDK releases add observer
     * callbacks over time. Dispatching by callback name keeps this transport source-compatible
     * across those SDK API additions without silently dropping the callbacks we rely on.
     */
    private fun createPeerObserver(): PeerConnection.Observer {
        val handler = InvocationHandler { proxy, method, args ->
            val values = args ?: emptyArray()
            when (method.name) {
                "onIceCandidate" -> (values.firstOrNull() as? IceCandidate)?.let(listener::onLocalIceCandidate)
                "onDataChannel" -> (values.firstOrNull() as? DataChannel)?.let(::attachDataChannel)
                "onIceConnectionChange",
                "onStandardizedIceConnectionChange",
                "onConnectionChange" -> values.firstOrNull()?.toString()?.let(listener::onConnectionStateChanged)
                "onIceCandidateError" -> {
                    val detail = values.lastOrNull()?.toString().orEmpty()
                    fail("WebRTC ICE candidate error: ${detail.take(240)}")
                }
                "toString" -> "P2pWebRtcDataChannel.PeerObserver"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === values.firstOrNull()
            }
            null
        }
        return Proxy.newProxyInstance(
            PeerConnection.Observer::class.java.classLoader,
            arrayOf(PeerConnection.Observer::class.java),
            handler
        ) as PeerConnection.Observer
    }

    private fun attachDataChannel(channel: DataChannel) {
        if (closed) {
            runCatching { channel.close() }
            runCatching { channel.dispose() }
            return
        }
        if (channel.label() != DATA_CHANNEL_LABEL) {
            runCatching { channel.close() }
            runCatching { channel.dispose() }
            fail("The peer opened an unexpected data channel.")
            return
        }
        synchronized(lock) {
            val existing = dataChannel
            if (existing != null && existing !== channel) {
                runCatching { channel.close() }
                runCatching { channel.dispose() }
                fail("More than one transfer data channel was opened.")
                return
            }
            dataChannel = channel
            val observer = createDataChannelObserver()
            channelObserver = observer
            channel.registerObserver(observer)
        }
        listener.onDataChannelStateChanged(channel.state().name)
    }

    private fun createDataChannelObserver(): DataChannel.Observer {
        val handler = InvocationHandler { proxy, method, args ->
            val values = args ?: emptyArray()
            when (method.name) {
                "onStateChange" -> dataChannel?.state()?.name?.let(listener::onDataChannelStateChanged)
                "onBufferedAmountChange" -> listener.onBufferedAmountChanged(bufferedAmountBytes())
                "onMessage" -> {
                    val buffer = values.firstOrNull() as? DataChannel.Buffer
                    if (buffer != null) {
                        if (!buffer.binary) {
                            fail("Text payloads are not accepted on the file transfer channel.")
                        } else {
                            val source = buffer.data.duplicate()
                            if (source.remaining() > MAX_BINARY_FRAME_BYTES) {
                                fail("The remote peer sent an oversized data frame.")
                            } else {
                                val copy = ByteArray(source.remaining())
                                source.get(copy)
                                listener.onBinaryFrameReceived(copy)
                            }
                        }
                    }
                }
                "toString" -> "P2pWebRtcDataChannel.ChannelObserver"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === values.firstOrNull()
            }
            null
        }
        return Proxy.newProxyInstance(
            DataChannel.Observer::class.java.classLoader,
            arrayOf(DataChannel.Observer::class.java),
            handler
        ) as DataChannel.Observer
    }

    private fun addCandidateNow(candidate: QueuedCandidate) {
        val peer = peerConnection ?: return
        val accepted = peer.addIceCandidate(IceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex,
            candidate.candidate
        ))
        if (!accepted) fail("WebRTC rejected a remote ICE candidate.")
    }

    private fun flushQueuedRemoteCandidates() {
        val pending = queuedRemoteCandidates.toList()
        queuedRemoteCandidates.clear()
        pending.forEach(::addCandidateNow)
    }

    private fun sdpObserver(
        onCreateSuccess: (SessionDescription) -> Unit = {},
        onSetSuccess: () -> Unit = {},
        onFailure: (String) -> Unit
    ): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = onCreateSuccess.invoke(description)
        override fun onSetSuccess() = onSetSuccess.invoke()
        override fun onCreateFailure(error: String) = onFailure.invoke(error)
        override fun onSetFailure(error: String) = onFailure.invoke(error)
    }

    private fun postToRtcThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else rtcHandler.post(action)
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        listener.onFailure(message.take(300))
    }

    private fun closeOnRtcThread() {
        synchronized(lock) {
            val channel = dataChannel
            val observer = channelObserver
            channelObserver = null
            dataChannel = null
            if (channel != null) {
                runCatching { if (observer != null) channel.unregisterObserver() }
                runCatching { channel.close() }
                runCatching { channel.dispose() }
            }
            val peer = peerConnection
            peerConnection = null
            if (peer != null) {
                runCatching { peer.close() }
                runCatching { peer.dispose() }
            }
            peerObserver = null
            channelObserver = null
            queuedRemoteCandidates.clear()
            remoteDescriptionReady = false
        }
        listener.onDataChannelStateChanged(DataChannel.State.CLOSED.name)
        listener.onConnectionStateChanged("CLOSED")
    }

    companion object {
        const val DATA_CHANNEL_LABEL = "p2p-file-transfer-v1"
        const val MAX_BUFFERED_BYTES = 512L * 1024L
        const val LOW_BUFFERED_BYTES = 128L * 1024L

        private const val MAX_BINARY_FRAME_BYTES = 64 * 1024
        private const val MAX_SDP_CHARS = 120_000
        private const val MAX_ICE_CANDIDATE_CHARS = 4096
        private const val MAX_SDP_MLINE_INDEX = 256
        private const val MAX_SDP_MID_CHARS = 128
        private const val MAX_QUEUED_ICE_CANDIDATES = 256
        private const val TAG = "P2pWebRtcDataChannel"
        private val FACTORY_LOCK = Any()
        @Volatile private var sharedFactory: PeerConnectionFactory? = null
    }
}
