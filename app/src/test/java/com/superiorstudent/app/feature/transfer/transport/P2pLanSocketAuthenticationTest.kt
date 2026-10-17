package com.superiorstudent.app.feature.transfer.transport

import java.io.IOException
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pLanSocketAuthenticationTest {
    private val transferId = "transfer_0123456789ab"
    private val token = "a".repeat(64)

    @Test
    fun validTokenAuthenticatesPeerBeforeEncryptedFramesAreAccepted() {
        val address = InetAddress.getLoopbackAddress()
        val serverSocket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(address, 0), 1)
        }
        val acceptExecutor = Executors.newSingleThreadExecutor()
        val serverCallback = RecordingCallback()
        var serverTransport: P2pLanSocketTransport? = null
        var clientTransport: P2pLanSocketTransport? = null
        try {
            val serverAccepted = acceptExecutor.submit<Boolean> {
                val accepted = serverSocket.accept()
                try {
                    accepted.soTimeout = 3_000
                    if (!P2pLanHandshake.authenticateServer(accepted, transferId, token)) {
                        accepted.close()
                        false
                    } else {
                        accepted.soTimeout = 0
                        serverTransport = P2pLanSocketTransport.fromAcceptedSocket(accepted, serverCallback)
                        true
                    }
                } catch (error: Exception) {
                    runCatching { accepted.close() }
                    throw error
                }
            }

            clientTransport = P2pLanSocketTransport.connectAuthenticated(
                address = address,
                port = serverSocket.localPort,
                transferId = transferId,
                token = token,
                connectTimeoutMs = 3_000,
                handshakeTimeoutMs = 3_000,
                callback = RecordingCallback()
            )
            assertTrue(serverAccepted.get(3, TimeUnit.SECONDS))

            val frame = ByteArray(128) { (it xor 0x5a).toByte() }
            assertTrue(clientTransport!!.sendEncryptedFrame(frame))
            val received = serverCallback.frames.poll(3, TimeUnit.SECONDS)
            assertNotNull("No frame arrived after successful peer authentication.", received)
            assertArrayEquals(frame, received)
            received!!.fill(0)
        } finally {
            runCatching { clientTransport?.close() }
            runCatching { serverTransport?.close() }
            runCatching { serverSocket.close() }
            acceptExecutor.shutdownNow()
        }
    }

    @Test
    fun incorrectTokenIsRejectedBeforeDataTransportIsCreated() {
        val address = InetAddress.getLoopbackAddress()
        val serverSocket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(address, 0), 1)
        }
        val acceptExecutor = Executors.newSingleThreadExecutor()
        try {
            val serverAccepted = acceptExecutor.submit<Boolean> {
                val accepted = serverSocket.accept()
                try {
                    accepted.soTimeout = 3_000
                    P2pLanHandshake.authenticateServer(accepted, transferId, token).also {
                        runCatching { accepted.close() }
                    }
                } catch (error: Exception) {
                    runCatching { accepted.close() }
                    throw error
                }
            }

            assertThrows(IOException::class.java) {
                P2pLanSocketTransport.connectAuthenticated(
                    address = address,
                    port = serverSocket.localPort,
                    transferId = transferId,
                    token = "b".repeat(64),
                    connectTimeoutMs = 3_000,
                    handshakeTimeoutMs = 3_000,
                    callback = RecordingCallback()
                )
            }
            assertFalse(serverAccepted.get(3, TimeUnit.SECONDS))
        } finally {
            runCatching { serverSocket.close() }
            acceptExecutor.shutdownNow()
        }
    }

    @Test
    fun changingTransferIdInvalidatesTheHandshakeProof() {
        val nonce = ByteArray(32) { it.toByte() }
        val expected = P2pLanHandshake.proofForTests(token, transferId, nonce)
        val wrongTransfer = P2pLanHandshake.proofForTests(token, transferId + "_other", nonce)
        val wrongToken = P2pLanHandshake.proofForTests("c".repeat(64), transferId, nonce)

        assertTrue(!expected.contentEquals(wrongTransfer))
        assertTrue(!expected.contentEquals(wrongToken))
        expected.fill(0)
        wrongTransfer.fill(0)
        wrongToken.fill(0)
        nonce.fill(0)
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
