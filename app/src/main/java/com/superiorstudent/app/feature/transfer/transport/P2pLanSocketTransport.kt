package com.superiorstudent.app.feature.transfer.transport

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounded TCP frame transport for same-LAN transfers.
 *
 * This class carries frames only; callers MUST encrypt/authenticate file frames with
 * P2pEncryptedChunkStream before sending them, and verify the end frame and SHA-256 on receive.
 * A TCP connection alone does not authenticate the remote student's identity. Only connect or
 * wrap accepted sockets after the app's LAN discovery/pairing layer has authorised the peer.
 *
 * The wire protocol is: 4-byte big-endian frame length followed by that many frame bytes.
 * The size is validated before allocating the frame buffer.
 */
class P2pLanSocketTransport private constructor(
    private val socket: Socket,
    private val callback: Callback
) : AutoCloseable {

    interface Callback {
        /** Called once after the TCP socket is established. */
        fun onConnected() = Unit

        /**
         * Receives one complete frame on the reader thread.
         * The callback owns the array and should clear it after processing.
         */
        fun onEncryptedFrame(frame: ByteArray)

        /** Called once when this transport closes, including after a read/write failure. */
        fun onClosed() = Unit

        /** Reports a socket/protocol failure before the transport closes. */
        fun onError(error: Exception) = Unit
    }

    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val errorReported = AtomicBoolean(false)
    private val terminalNotified = AtomicBoolean(false)
    private val writeLock = Any()
    private val readerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "p2p-lan-reader").apply { isDaemon = true }
    }

    private val input = DataInputStream(
        BufferedInputStream(socket.getInputStream(), IO_BUFFER_BYTES)
    )
    private val output = DataOutputStream(
        BufferedOutputStream(socket.getOutputStream(), IO_BUFFER_BYTES)
    )

    init {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.receiveBufferSize = SOCKET_BUFFER_BYTES
        socket.sendBufferSize = SOCKET_BUFFER_BYTES
        // Deliberately no read timeout: user-requested pause may keep a session idle.
        socket.soTimeout = 0
    }

    /**
     * Writes one bounded frame. Returns false if the frame is invalid or the socket is closed.
     * The caller must not mutate [frame] until this method returns.
     */
    fun sendEncryptedFrame(frame: ByteArray): Boolean {
        if (closed.get() || frame.isEmpty() || frame.size > MAX_FRAME_BYTES) return false
        synchronized(writeLock) {
            if (closed.get()) return false
            try {
                output.writeInt(frame.size)
                output.write(frame)
                output.flush()
                return true
            } catch (error: IOException) {
                reportErrorAndClose(error)
                return false
            } catch (error: RuntimeException) {
                reportErrorAndClose(IOException("LAN frame write failed.", error))
                return false
            }
        }
    }

    private fun startReader() {
        if (!started.compareAndSet(false, true)) return
        readerExecutor.execute {
            try {
                while (!closed.get()) {
                    val length = try {
                        input.readInt()
                    } catch (_: EOFException) {
                        break
                    }

                    if (length !in 1..MAX_FRAME_BYTES) {
                        throw IOException("Invalid LAN frame length: $length")
                    }

                    val frame = ByteArray(length)
                    try {
                        input.readFully(frame)
                        callback.onEncryptedFrame(frame)
                    } catch (error: Exception) {
                        frame.fill(0)
                        throw error
                    }
                }
            } catch (error: Exception) {
                if (!closed.get()) {
                    val ioError = if (error is IOException) error
                    else IOException("LAN frame receive failed.", error)
                    reportErrorAndClose(ioError)
                }
            } finally {
                close()
            }
        }
    }

    private fun reportErrorAndClose(error: Exception) {
        if (!closed.get() && errorReported.compareAndSet(false, true)) {
            runCatching { callback.onError(error) }
        }
        close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { socket.close() }
        readerExecutor.shutdownNow()
        if (terminalNotified.compareAndSet(false, true)) {
            runCatching { callback.onClosed() }
        }
    }

    companion object {
        /** Matches the current WebRTC transport frame bound. */
        const val MAX_FRAME_BYTES = 96 * 1024
        private const val IO_BUFFER_BYTES = 256 * 1024
        private const val SOCKET_BUFFER_BYTES = 512 * 1024

        /**
         * Opens a direct TCP connection to a peer's LAN address. Discovery and peer
         * authentication are intentionally owned by the caller.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun connect(
            address: InetAddress,
            port: Int,
            connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
            callback: Callback
        ): P2pLanSocketTransport {
            require(port in 1..65535) { "LAN peer port is invalid." }
            require(connectTimeoutMs > 0) { "LAN connect timeout must be positive." }
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(address, port), connectTimeoutMs)
                return createConnected(socket, callback)
            } catch (error: Exception) {
                runCatching { socket.close() }
                if (error is IOException) throw error
                throw IOException("Could not connect to the LAN peer.", error)
            }
        }

        /**
         * Wraps a socket already accepted by the LAN listener.
         * Keep this internal until the pairing layer authorises accepted peers.
         */
        internal fun fromAcceptedSocket(socket: Socket, callback: Callback): P2pLanSocketTransport {
            require(socket.isConnected && !socket.isClosed) { "A connected LAN socket is required." }
            return createConnected(socket, callback)
        }

        private fun createConnected(socket: Socket, callback: Callback): P2pLanSocketTransport {
            val transport = P2pLanSocketTransport(socket, callback)
            try {
                callback.onConnected()
            } catch (error: Exception) {
                transport.close()
                throw IOException("LAN connected callback failed.", error)
            }
            transport.startReader()
            return transport
        }

        private const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000
    }
}
