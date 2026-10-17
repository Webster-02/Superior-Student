package com.superiorstudent.app.feature.transfer.transport

import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pLanSocketTransportTest {

    @Test
    fun loopbackTransportDeliversBinaryFrameUnchanged() {
        connectedPair().use { pair ->
            val payload = ByteArray(32 * 1024 + 71) { ((it * 17) xor (it shr 4)).toByte() }

            assertTrue(pair.client.sendEncryptedFrame(payload))
            val received = pair.serverCallback.frames.poll(3, TimeUnit.SECONDS)

            assertNotNull("Server did not receive the frame.", received)
            assertArrayEquals(payload, received)
            received!!.fill(0)
        }
    }

    @Test
    fun invalidOutgoingFrameIsRejectedWithoutClosingSocket() {
        connectedPair().use { pair ->
            assertFalse(pair.client.sendEncryptedFrame(ByteArray(0)))
            assertFalse(
                pair.client.sendEncryptedFrame(
                    ByteArray(P2pLanSocketTransport.MAX_FRAME_BYTES + 1)
                )
            )

            val payload = byteArrayOf(0, 1, 2, 0xff.toByte(), 0x80.toByte())
            assertTrue(pair.client.sendEncryptedFrame(payload))
            val received = pair.serverCallback.frames.poll(3, TimeUnit.SECONDS)

            assertNotNull("Connection should remain usable after rejecting an invalid outgoing frame.", received)
            assertArrayEquals(payload, received)
            received!!.fill(0)
        }
    }

    @Test
    fun oversizedIncomingLengthIsRejectedBeforeReadingFrameBody() {
        val callback = RecordingCallback()
        val address = InetAddress.getLoopbackAddress()
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(address, 0), 1)
        val acceptExecutor = Executors.newSingleThreadExecutor()
        var transport: P2pLanSocketTransport? = null
        var rawClient: Socket? = null
        try {
            val accepted = acceptExecutor.submit<P2pLanSocketTransport> {
                P2pLanSocketTransport.fromAcceptedSocket(serverSocket.accept(), callback)
            }
            val clientSocket = Socket(address, serverSocket.localPort)
            rawClient = clientSocket
            transport = accepted.get(3, TimeUnit.SECONDS)

            DataOutputStream(clientSocket.getOutputStream()).use { output ->
                output.writeInt(P2pLanSocketTransport.MAX_FRAME_BYTES + 1)
                output.flush()
            }

            val error = callback.errors.poll(3, TimeUnit.SECONDS)
            assertNotNull("Invalid oversized frame must report a protocol error.", error)
            assertTrue(error!!.message.orEmpty().contains("Invalid LAN frame length"))
        } finally {
            runCatching { transport?.close() }
            runCatching { rawClient?.close() }
            runCatching { serverSocket.close() }
            acceptExecutor.shutdownNow()
        }
    }

    @Test
    fun truncatedLengthPrefixIsReportedAsProtocolError() {
        val callback = RecordingCallback()
        val address = InetAddress.getLoopbackAddress()
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(address, 0), 1)
        val acceptExecutor = Executors.newSingleThreadExecutor()
        var transport: P2pLanSocketTransport? = null
        var rawClient: Socket? = null
        try {
            val accepted = acceptExecutor.submit<P2pLanSocketTransport> {
                P2pLanSocketTransport.fromAcceptedSocket(serverSocket.accept(), callback)
            }
            val clientSocket = Socket(address, serverSocket.localPort)
            rawClient = clientSocket
            transport = accepted.get(3, TimeUnit.SECONDS)
            clientSocket.getOutputStream().write(byteArrayOf(0, 0))
            clientSocket.getOutputStream().flush()
            clientSocket.close()
            rawClient = null

            assertNotNull(
                "A partial frame header must not be treated as a clean EOF.",
                callback.errors.poll(3, TimeUnit.SECONDS)
            )
        } finally {
            runCatching { transport?.close() }
            runCatching { rawClient?.close() }
            runCatching { serverSocket.close() }
            acceptExecutor.shutdownNow()
        }
    }

    private fun connectedPair(): ConnectedPair {
        val address = InetAddress.getLoopbackAddress()
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(address, 0), 1)
        val acceptExecutor = Executors.newSingleThreadExecutor()
        val serverCallback = RecordingCallback()
        val clientCallback = RecordingCallback()
        try {
            val accepted = acceptExecutor.submit<P2pLanSocketTransport> {
                P2pLanSocketTransport.fromAcceptedSocket(serverSocket.accept(), serverCallback)
            }
            val client = P2pLanSocketTransport.connect(
                address = address,
                port = serverSocket.localPort,
                connectTimeoutMs = 3_000,
                callback = clientCallback
            )
            val server = accepted.get(3, TimeUnit.SECONDS)
            assertTrue(clientCallback.connected.await(2, TimeUnit.SECONDS))
            assertTrue(serverCallback.connected.await(2, TimeUnit.SECONDS))
            return ConnectedPair(serverSocket, acceptExecutor, client, server, serverCallback)
        } catch (error: Exception) {
            runCatching { serverSocket.close() }
            acceptExecutor.shutdownNow()
            throw error
        }
    }

    private class ConnectedPair(
        private val serverSocket: ServerSocket,
        private val acceptExecutor: java.util.concurrent.ExecutorService,
        val client: P2pLanSocketTransport,
        val server: P2pLanSocketTransport,
        val serverCallback: RecordingCallback
    ) : AutoCloseable {
        override fun close() {
            client.close()
            server.close()
            runCatching { serverSocket.close() }
            acceptExecutor.shutdownNow()
        }
    }

    private class RecordingCallback : P2pLanSocketTransport.Callback {
        val connected = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val frames = LinkedBlockingQueue<ByteArray>()
        val errors = LinkedBlockingQueue<Exception>()

        override fun onConnected() {
            connected.countDown()
        }

        override fun onEncryptedFrame(frame: ByteArray) {
            frames.offer(frame)
        }

        override fun onClosed() {
            closed.countDown()
        }

        override fun onError(error: Exception) {
            errors.offer(error)
        }
    }
}
