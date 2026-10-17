package com.superiorstudent.app.feature.transfer.transport

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Length-bounded TCP transport for already-encrypted P2P frames on a private LAN.
 *
 * The handshake is a one-time transfer token delivered through authenticated Firestore
 * signaling. This token prevents accidental/misdirected connections; it is not the file
 * encryption key. File payloads MUST already be encrypted/authenticated by
 * P2pEncryptedChunkStream. Never pass raw file bytes to this transport.
 */
class P2pLanSocketTransport private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    override val transferId: String
) : P2pFrameChannel {

    private val closed = AtomicBoolean(false)

    @Synchronized
    @Throws(IOException::class)
    override fun sendFrame(frame: ByteArray): Boolean {
        if (closed.get() || frame.isEmpty() || frame.size > MAX_FRAME_BYTES) return false
        return try {
            output.writeInt(frame.size)
            output.write(frame)
            output.flush()
            true
        } catch (_: IOException) {
            close()
            false
        }
    }

    /**
     * Reads exactly one framed message. Returns null on a read timeout; EOF is reported as an
     * IOException so caller can distinguish a closed transport from an idle channel.
     */
    @Synchronized
    @Throws(IOException::class)
    override fun receiveFrame(timeoutMillis: Int): ByteArray? {
        if (closed.get()) return null
        require(timeoutMillis in 1..MAX_SOCKET_TIMEOUT_MS) { "Socket timeout is invalid." }
        return try {
            socket.soTimeout = timeoutMillis
            val length = input.readInt()
            require(length in 1..MAX_FRAME_BYTES) { "LAN frame length is invalid." }
            ByteArray(length).also(input::readFully)
        } catch (_: SocketTimeoutException) {
            null
        } catch (error: EOFException) {
            close()
            throw error
        } catch (error: IOException) {
            close()
            throw error
        } catch (error: IllegalArgumentException) {
            close()
            throw IOException("Rejected malformed LAN frame.", error)
        }
    }

    override fun isOpen(): Boolean = !closed.get() && socket.isConnected && !socket.isClosed

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
    }

    companion object {
        private const val HANDSHAKE_MAGIC = 0x53535031 // SSP1
        const val MAX_FRAME_BYTES = 96 * 1024
        const val DEFAULT_READ_TIMEOUT_MS = 15_000
        const val MAX_SOCKET_TIMEOUT_MS = 120_000
        const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000
        const val DEFAULT_HANDSHAKE_TIMEOUT_MS = 4_000
        private const val MAX_TRANSFER_ID_BYTES = 80
        private const val MAX_TOKEN_BYTES = 128

        data class LanCandidate(val ip: String, val port: Int, val token: String)

        fun createToken(): String {
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        /** Bind to every local interface. Advertise only a private IPv4 from localPrivateIpv4(). */
        @Throws(IOException::class)
        fun createServerSocket(): ServerSocket {
            return ServerSocket().apply {
                reuseAddress = false
                soTimeout = DEFAULT_HANDSHAKE_TIMEOUT_MS
                bind(InetSocketAddress("0.0.0.0", 0), 1)
            }
        }

        fun localPrivateIpv4(): String? {
            return try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                interfaces.asSequence()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { Collections.list(it.inetAddresses).asSequence() }
                    .filterIsInstance<Inet4Address>()
                    .filter { address ->
                        val text = address.hostAddress ?: return@filter false
                        !address.isLoopbackAddress && !address.isLinkLocalAddress &&
                            isPrivateIpv4(text)
                    }
                    .mapNotNull { it.hostAddress }
                    .firstOrNull()
            } catch (_: Exception) {
                null
            }
        }

        @Throws(IOException::class)
        fun accept(
            server: ServerSocket,
            transferId: String,
            expectedToken: String,
            handshakeTimeoutMillis: Int = DEFAULT_HANDSHAKE_TIMEOUT_MS
        ): P2pLanSocketTransport {
            validateHandshakeFields(transferId, expectedToken)
            val client = server.accept()
            try {
                client.tcpNoDelay = true
                client.keepAlive = true
                client.soTimeout = handshakeTimeoutMillis.coerceIn(1, MAX_SOCKET_TIMEOUT_MS)
                val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(client.getOutputStream()))
                verifyHandshake(input, transferId, expectedToken)
                client.soTimeout = DEFAULT_READ_TIMEOUT_MS
                return P2pLanSocketTransport(client, input, output, transferId)
            } catch (error: Exception) {
                runCatching { client.close() }
                if (error is IOException) throw error
                throw IOException("LAN handshake was rejected.", error)
            }
        }

        @Throws(IOException::class)
        fun connect(
            candidate: LanCandidate,
            transferId: String,
            connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MS,
            handshakeTimeoutMillis: Int = DEFAULT_HANDSHAKE_TIMEOUT_MS
        ): P2pLanSocketTransport {
            validateHandshakeFields(transferId, candidate.token)
            require(isPrivateIpv4(candidate.ip)) { "LAN candidate is not a private IPv4 address." }
            require(candidate.port in 1..65535) { "LAN candidate port is invalid." }
            val client = Socket()
            try {
                client.tcpNoDelay = true
                client.keepAlive = true
                client.connect(InetSocketAddress(candidate.ip, candidate.port),
                    connectTimeoutMillis.coerceIn(1, MAX_SOCKET_TIMEOUT_MS))
                client.soTimeout = handshakeTimeoutMillis.coerceIn(1, MAX_SOCKET_TIMEOUT_MS)
                val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(client.getOutputStream()))
                output.writeInt(HANDSHAKE_MAGIC)
                writeBoundedString(output, transferId, MAX_TRANSFER_ID_BYTES)
                writeBoundedString(output, candidate.token, MAX_TOKEN_BYTES)
                output.flush()
                client.soTimeout = DEFAULT_READ_TIMEOUT_MS
                return P2pLanSocketTransport(client, input, output, transferId)
            } catch (error: Exception) {
                runCatching { client.close() }
                if (error is IOException) throw error
                throw IOException("Could not establish an authenticated LAN socket.", error)
            }
        }

        private fun verifyHandshake(input: DataInputStream, transferId: String, expectedToken: String) {
            val magic = input.readInt()
            if (magic != HANDSHAKE_MAGIC) throw IOException("Unrecognized LAN transfer peer.")
            val receivedTransferId = readBoundedString(input, MAX_TRANSFER_ID_BYTES)
            val receivedToken = readBoundedString(input, MAX_TOKEN_BYTES)
            if (receivedTransferId != transferId) throw IOException("LAN transfer ID mismatch.")
            val expected = expectedToken.toByteArray(Charsets.UTF_8)
            val received = receivedToken.toByteArray(Charsets.UTF_8)
            if (!MessageDigest.isEqual(expected, received)) throw IOException("LAN connection token mismatch.")
        }

        private fun writeBoundedString(output: DataOutputStream, value: String, maxBytes: Int) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.isNotEmpty() && bytes.size <= maxBytes) { "Handshake field is invalid." }
            output.writeShort(bytes.size)
            output.write(bytes)
        }

        private fun readBoundedString(input: DataInputStream, maxBytes: Int): String {
            val size = input.readUnsignedShort()
            if (size !in 1..maxBytes) throw IOException("LAN handshake field length is invalid.")
            val bytes = ByteArray(size)
            input.readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        private fun validateHandshakeFields(transferId: String, token: String) {
            require(transferId.matches(Regex("^[A-Za-z0-9_-]{16,80}$"))) { "Transfer ID is invalid." }
            require(token.length in 32..MAX_TOKEN_BYTES && token.none { it.isISOControl() }) {
                "LAN token is invalid."
            }
        }

        private fun isPrivateIpv4(text: String): Boolean {
            val parts = text.split('.')
            if (parts.size != 4 || parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            return octets[0] == 10 ||
                (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168) ||
                (octets[0] == 169 && octets[1] == 254)
        }
    }
}
