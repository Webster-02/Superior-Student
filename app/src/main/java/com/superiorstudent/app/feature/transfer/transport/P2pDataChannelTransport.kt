package com.superiorstudent.app.feature.transfer.transport

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Binary DataChannel transport primitives for P2P file frames.
 *
 * This class deliberately does not create Firebase signaling, offer/answer SDP or TURN
 * credentials. A controller must create/configure PeerConnection with authenticated remote
 * signaling and short-lived ICE server credentials before attaching this transport.
 *
 * Treat the DataChannel as untrusted: application frames still require AEAD authentication
 * and end-of-file digest verification in P2pEncryptedChunkStream. The transport never marks
 * a file complete on channel-open or DataChannel delivery alone.
 */
class P2pDataChannelTransport(
    private val callback: Callback,
    private val maxBufferedBytes: Long = 512L * 1024L,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) : AutoCloseable {

    interface Callback {
        /** A small text-free binary frame arrived. Copy/consume it promptly off the callback thread. */
        fun onBinaryFrame(frame: ByteArray)
        fun onStateChanged(state: State, message: String? = null)
        fun onLocalIceCandidate(candidate: IceCandidate)
        fun onBufferedAmountChanged(bufferedBytes: Long)
    }

    enum class State {
        NEW, CONNECTING, CONNECTED, CLOSING, CLOSED, FAILED
    }

    private val closed = AtomicBoolean(false)
    @Volatile private var state: State = State.NEW
    @Volatile private var peerConnection: PeerConnection? = null
    @Volatile private var dataChannel: DataChannel? = null
    @Volatile private var localOfferer = false

    fun currentState(): State = state

    /**
     * Attach a PeerConnection created by the signaling controller. Offerer creates a channel;
     * answerer awaits onDataChannel. Ownership is transferred to this transport.
     */
    fun attachPeerConnection(connection: PeerConnection, offerer: Boolean) {
        check(!closed.get()) { "Transport has been closed." }
        check(peerConnection == null) { "PeerConnection has already been attached." }
        peerConnection = connection
        localOfferer = offerer
        setState(State.CONNECTING)
        if (offerer) {
            val init = DataChannel.Init().apply {
                ordered = true
                negotiated = false
            }
            val channel = connection.createDataChannel(CHANNEL_LABEL, init)
                ?: run {
                    fail("WebRTC could not create the file DataChannel.")
                    return
                }
            registerDataChannel(channel)
        }
    }

    /** Called by the owning PeerConnection.Observer when the remote creates a channel. */
    /** Attach a DataChannel created/received by P2pPeerConnectionController. */
    fun attachExistingDataChannel(channel: DataChannel) {
        if (closed.get()) {
            channel.close()
            return
        }
        if (dataChannel != null) {
            channel.close()
            fail("Unexpected duplicate DataChannel.")
            return
        }
        registerDataChannel(channel)
    }

    /** Kept for callers that hand ownership of a PeerConnection to this transport directly. */
    fun onRemoteDataChannel(channel: DataChannel) = attachExistingDataChannel(channel)

    /** ICE callback is forwarded unchanged to the trusted signaling controller. */
    fun onIceCandidate(candidate: IceCandidate) {
        if (!closed.get()) mainHandler.post { callback.onLocalIceCandidate(candidate) }
    }

    /**
     * Non-blocking send with strict DataChannel backpressure. Returns false when not ready,
     * the transport is closing, the payload is too large, or the peer's send buffer is full.
     */
    fun sendBinaryFrame(frame: ByteArray): Boolean {
        if (closed.get() || state != State.CONNECTED || frame.isEmpty() ||
            frame.size > MAX_FRAME_BYTES
        ) return false
        val channel = dataChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        val buffered = channel.bufferedAmount()
        if (buffered > maxBufferedBytes || buffered + frame.size > maxBufferedBytes) {
            mainHandler.post { callback.onBufferedAmountChanged(buffered) }
            return false
        }
        val buffer = ByteBuffer.wrap(frame.copyOf())
        val sent = try {
            channel.send(DataChannel.Buffer(buffer, true))
        } catch (_: RuntimeException) {
            false
        }
        mainHandler.post { callback.onBufferedAmountChanged(channel.bufferedAmount()) }
        return sent
    }

    private fun registerDataChannel(channel: DataChannel) {
        if (closed.get()) {
            channel.close()
            return
        }
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {
                val amount = runCatching { channel.bufferedAmount() }.getOrDefault(-1L)
                mainHandler.post { callback.onBufferedAmountChanged(amount) }
            }

            override fun onStateChange() {
                when (channel.state()) {
                    DataChannel.State.OPEN -> setState(State.CONNECTED)
                    DataChannel.State.CLOSING -> setState(State.CLOSING)
                    DataChannel.State.CLOSED -> setState(State.CLOSED)
                    DataChannel.State.CONNECTING -> setState(State.CONNECTING)
                    else -> Unit
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (closed.get()) return
                if (!buffer.binary) {
                    fail("Unexpected text frame on file DataChannel.")
                    return
                }
                if (buffer.data.remaining() !in 1..MAX_FRAME_BYTES) {
                    fail("Invalid DataChannel frame size.")
                    return
                }
                val copy = ByteArray(buffer.data.remaining())
                buffer.data.get(copy)
                mainHandler.post {
                    if (!closed.get()) callback.onBinaryFrame(copy)
                    else copy.fill(0)
                }
            }
        })
        when (channel.state()) {
            DataChannel.State.OPEN -> setState(State.CONNECTED)
            DataChannel.State.CLOSING -> setState(State.CLOSING)
            DataChannel.State.CLOSED -> setState(State.CLOSED)
            else -> setState(State.CONNECTING)
        }
    }

    private fun setState(next: State, message: String? = null) {
        if (closed.get() && next != State.CLOSED) return
        state = next
        mainHandler.post { callback.onStateChanged(next, message) }
    }

    private fun fail(message: String) {
        if (closed.get()) return
        setState(State.FAILED, message)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        state = State.CLOSING
        runCatching { dataChannel?.unregisterObserver() }
        runCatching { dataChannel?.close() }
        runCatching { peerConnection?.close() }
        dataChannel = null
        peerConnection = null
        state = State.CLOSED
        mainHandler.post { callback.onStateChanged(State.CLOSED) }
    }

    companion object {
        const val CHANNEL_LABEL = "superior-student-p2p-files-v1"
        /** Includes the encrypted frame header and GCM authentication tag. */
        const val MAX_FRAME_BYTES = 96 * 1024
    }
}

/**
 * Small factory helper to initialise the native WebRTC library once per application process.
 */
object P2pWebRtcFactory {
    @Volatile private var factory: PeerConnectionFactory? = null

    @Synchronized
    fun get(context: Context): PeerConnectionFactory {
        factory?.let { return it }
        val appContext = context.applicationContext
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )
        return PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
            .also { factory = it }
    }
}
