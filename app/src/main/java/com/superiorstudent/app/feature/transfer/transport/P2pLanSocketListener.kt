package com.superiorstudent.app.feature.transfer.transport

import java.io.DataInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Short-lived LAN listener for one accepted incoming file offer.
 *
 * The port is bound only after the receiver accepts the offer. Before a socket can carry frames,
 * the remote must prove possession of a one-transfer secret sent to the authenticated sender via
 * Firestore and complete a nonce-based HMAC challenge. The secret itself is never sent over TCP.
 */
class P2pLanSocketListener(
    private val transferId: String,
    private val callback: Callback
) : AutoCloseable {

    data class Endpoint(
        val ip: String,
        val port: Int,
        val token: String
    )

    interface Callback {
        /** An authenticated sender socket is ready to carry AES-GCM encrypted frames. */
        fun onAuthenticatedTransport(transport: P2pLanSocketTransport)
        /** A complete, length-bounded frame arrived from the authenticated remote sender. */
        fun onEncryptedFrame(frame: ByteArray)
        /** Called when the authenticated remote closes before the owning transfer is disposed. */
        fun onTransportClosed() = Unit
        /** An established LAN connection failed. Do not silently switch transports mid-file. */
        fun onError(error: Exception)
    }

    private val closed = AtomicBoolean(false)
    private val activeTransport = AtomicReference<P2pLanSocketTransport?>(null)
    private val acceptExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "p2p-lan-accept").apply { isDaemon = true }
    }
    private val handshakeExecutor = ThreadPoolExecutor(
        MAX_PARALLEL_HANDSHAKES,
        MAX_PARALLEL_HANDSHAKES,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_QUEUED_HANDSHAKES),
        { runnable -> Thread(runnable, "p2p-lan-handshake").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var endpointToken: String? = null

    /**
     * Starts listening on all local IPv4 interfaces. Returns a private IPv4 address for signalling,
     * the actual ephemeral port, and a random per-transfer token. Failure throws and closes resources.
     */
    @Synchronized
    @Throws(IOException::class)
    fun start(): Endpoint {
        check(!closed.get()) { "LAN listener is closed." }
        check(serverSocket == null) { "LAN listener has already started." }
        require(transferId.matches(TRANSFER_ID_PATTERN)) { "Invalid LAN transfer ID." }

        val bindAddress = InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0)
        val socket = ServerSocket()
        try {
            socket.reuseAddress = true
            socket.bind(bindAddress, SERVER_BACKLOG)
            val ip = findPrivateIpv4Address()?.hostAddress
                ?: throw IOException("No active private IPv4 address is available for LAN transfer.")
            val token = newToken()
            serverSocket = socket
            endpointToken = token
            acceptExecutor.execute { acceptLoop(socket, token) }
            return Endpoint(ip, socket.localPort, token)
        } catch (error: Exception) {
            runCatching { socket.close() }
            serverSocket = null
            endpointToken = null
            if (error is IOException) throw error
            throw IOException("Could not start the local network listener.", error)
        }
    }

    private fun acceptLoop(server: ServerSocket, token: String) {
        while (!closed.get() && !server.isClosed) {
            val peer = try {
                server.accept()
            } catch (error: Exception) {
                if (!closed.get()) notifyError(asIoException("LAN listener failed.", error))
                return
            }

            if (closed.get() || activeTransport.get() != null) {
                runCatching { peer.close() }
                continue
            }
            try {
                handshakeExecutor.execute { authenticateAndAttach(peer, token) }
            } catch (_: RejectedExecutionException) {
                runCatching { peer.close() }
            }
        }
    }

    private fun authenticateAndAttach(peer: Socket, token: String) {
        try {
            peer.tcpNoDelay = true
            peer.soTimeout = HANDSHAKE_TIMEOUT_MS
            if (!P2pLanHandshake.authenticateServer(peer, transferId, token)) {
                runCatching { peer.close() }
                return
            }

            peer.soTimeout = 0
            val transport = P2pLanSocketTransport.fromAcceptedSocket(
                peer,
                object : P2pLanSocketTransport.Callback {
                    override fun onEncryptedFrame(frame: ByteArray) {
                        if (closed.get()) {
                            frame.fill(0)
                            return
                        }
                        try {
                            callback.onEncryptedFrame(frame)
                        } catch (error: Exception) {
                            frame.fill(0)
                            notifyError(asIoException("Could not process an authenticated LAN frame.", error))
                            close()
                        }
                    }

                    override fun onClosed() {
                        activeTransport.set(null)
                        if (!closed.get()) runCatching { callback.onTransportClosed() }
                    }

                    override fun onError(error: Exception) {
                        if (!closed.get()) notifyError(error)
                    }
                },
                beforeReader = { prepared ->
                    if (closed.get() || !activeTransport.compareAndSet(null, prepared)) {
                        throw IOException("Another LAN peer is already active.")
                    }
                }
            )

            callback.onAuthenticatedTransport(transport)
        } catch (error: Exception) {
            runCatching { peer.close() }
            if (!closed.get() && error !is IOException) {
                notifyError(asIoException("LAN peer authentication failed.", error))
            }
            // Unauthenticated LAN peers are silently dropped; the Internet/WebRTC route can remain active.
        }
    }

    private fun findPrivateIpv4Address(): Inet4Address? {
        val interfaces = try {
            Collections.list(NetworkInterface.getNetworkInterfaces()).filter { network ->
                runCatching { network.isUp && !network.isLoopback && !network.isVirtual }
                    .getOrDefault(false)
            }
        } catch (_: Exception) {
            return null
        }

        val candidates = interfaces.flatMap { network ->
            runCatching { Collections.list(network.inetAddresses) }.getOrDefault(emptyList())
                .filterIsInstance<Inet4Address>()
                .filter { address ->
                    address.isSiteLocalAddress && !address.isLoopbackAddress && !address.isLinkLocalAddress
                }
                .map { address -> network to address }
        }
        return candidates.sortedWith(
            compareBy<Pair<NetworkInterface, Inet4Address>> {
                val name = it.first.name.lowercase()
                if ("wlan" in name || "wifi" in name || "ap" == name) 0 else 1
            }.thenBy { it.second.hostAddress }
        ).firstOrNull()?.second
    }

    private fun newToken(): String {
        val random = ByteArray(TOKEN_BYTES)
        randomSource.nextBytes(random)
        return try {
            random.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } finally {
            random.fill(0)
        }
    }

    private fun asIoException(message: String, cause: Throwable): IOException =
        if (cause is IOException) cause else IOException(message, cause)

    private fun notifyError(error: Exception) {
        if (!closed.get()) runCatching { callback.onError(error) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        serverSocket?.let { runCatching { it.close() } }
        serverSocket = null
        endpointToken = null
        activeTransport.getAndSet(null)?.close()
        acceptExecutor.shutdownNow()
        handshakeExecutor.shutdownNow()
    }

    companion object {
        private val TRANSFER_ID_PATTERN = Regex("^[A-Za-z0-9_-]{16,80}$")
        private const val SERVER_BACKLOG = 8
        private const val MAX_PARALLEL_HANDSHAKES = 2
        private const val MAX_QUEUED_HANDSHAKES = 2
        private const val HANDSHAKE_TIMEOUT_MS = 5_000
        private const val TOKEN_BYTES = 32
        private val randomSource = SecureRandom()
    }
}

/** Fixed-size HMAC challenge/response. The shared token is never put on the socket. */
internal object P2pLanHandshake {
    private val MAGIC = "SSP2PLN1".toByteArray(StandardCharsets.US_ASCII)
    private const val NONCE_BYTES = 32
    private const val PROOF_BYTES = 32
    private const val ACCEPTED = 1
    private const val REJECTED = 0
    private val random = SecureRandom()

    fun authenticateServer(socket: Socket, transferId: String, token: String): Boolean {
        val input = DataInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val suppliedProof = ByteArray(PROOF_BYTES)
        try {
            output.write(MAGIC)
            output.write(nonce)
            output.flush()
            input.readFully(suppliedProof)
            val expected = proof(token, transferId, nonce)
            val matches = MessageDigest.isEqual(expected, suppliedProof)
            expected.fill(0)
            output.write(if (matches) ACCEPTED else REJECTED)
            output.flush()
            return matches
        } catch (_: IOException) {
            return false
        } finally {
            nonce.fill(0)
            suppliedProof.fill(0)
        }
    }

    fun authenticateClient(socket: Socket, transferId: String, token: String) {
        val input = DataInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        val receivedMagic = ByteArray(MAGIC.size)
        val nonce = ByteArray(NONCE_BYTES)
        try {
            input.readFully(receivedMagic)
            require(MessageDigest.isEqual(MAGIC, receivedMagic)) { "The LAN peer protocol is invalid." }
            input.readFully(nonce)
            val clientProof = proof(token, transferId, nonce)
            output.write(clientProof)
            output.flush()
            clientProof.fill(0)
            val result = input.read()
            require(result == ACCEPTED) { "The LAN peer rejected authentication." }
        } finally {
            receivedMagic.fill(0)
            nonce.fill(0)
        }
    }

    internal fun proofForTests(token: String, transferId: String, nonce: ByteArray): ByteArray =
        proof(token, transferId, nonce)

    private fun proof(token: String, transferId: String, nonce: ByteArray): ByteArray {
        require(token.length in 32..128) { "LAN token length is invalid." }
        require(transferId.matches(Regex("^[A-Za-z0-9_-]{16,80}$"))) { "LAN transfer ID is invalid." }
        require(nonce.size == NONCE_BYTES) { "LAN challenge length is invalid." }
        val transferBytes = transferId.toByteArray(StandardCharsets.UTF_8)
        val message = ByteBuffer.allocate(MAGIC.size + 4 + transferBytes.size + NONCE_BYTES)
            .put(MAGIC)
            .putInt(transferBytes.size)
            .put(transferBytes)
            .put(nonce)
            .array()
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(token.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            mac.doFinal(message)
        } finally {
            transferBytes.fill(0)
            message.fill(0)
        }
    }
}
