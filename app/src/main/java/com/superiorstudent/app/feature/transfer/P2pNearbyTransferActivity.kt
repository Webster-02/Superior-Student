package com.superiorstudent.app.feature.transfer

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.superiorstudent.app.feature.transfer.data.P2pTransferFileMetadata
import com.superiorstudent.app.feature.transfer.data.P2pTransferFileMetadataReader
import com.superiorstudent.app.feature.transfer.data.P2pTransferValidation
import com.superiorstudent.app.feature.transfer.security.P2pEncryptedChunkStream
import com.superiorstudent.app.feature.transfer.security.P2pTransferCrypto
import com.superiorstudent.app.feature.transfer.transport.P2pLanDiscovery
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nearby-only file transfer. Offer, consent, ephemeral key exchange, encrypted frames and receipt
 * travel over the local socket; Firebase and Internet signaling are not used by this screen.
 */
class P2pNearbyTransferActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var sendButton: Button
    private lateinit var receiveButton: Button
    private lateinit var stopButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val sending = AtomicBoolean(false)
    private val receiving = AtomicBoolean(false)
    private val activeIncomingSocket = AtomicBoolean(false)

    @Volatile private var outgoingDiscovery: P2pLanDiscovery? = null
    @Volatile private var advertisingDiscovery: P2pLanDiscovery? = null
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var pendingIncomingSession: PendingIncomingSession? = null
    @Volatile private var preparedOutgoing: PreparedOutgoing? = null
    @Volatile private var peerPromptShown = false
    @Volatile private var temporaryDownload: File? = null

    private data class PreparedOutgoing(
        val metadata: P2pTransferFileMetadata,
        val sha256: String
    )

    private class PendingIncomingSession(
        val socket: Socket,
        val input: DataInputStream,
        val output: DataOutputStream,
        val offer: P2pNearbyOffer
    ) {
        val selectionReady = CountDownLatch(1)
        @Volatile var accepted: Boolean = false
        @Volatile var destinationUri: Uri? = null
    }

    private data class Packet(val kind: Int, val payload: ByteArray)

    private val outgoingFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) prepareOutgoingFile(uri)
            else showStatus("File selection cancelled.")
        }

    private val incomingSavePicker =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            val pending = pendingIncomingSession
            if (pending == null) return@registerForActivityResult
            pending.destinationUri = uri
            pending.accepted = uri != null
            pending.selectionReady.countDown()
            pendingIncomingSession = null
            if (uri == null) showStatus("Save location cancelled. The sender will not transfer the file.")
            else showStatus("Save location selected. Comparing the security code…")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderScreen()
    }

    private fun renderScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(22), dp(26), dp(22), dp(18))
            setBackgroundColor(0xFFF7F7FC.toInt())
        }
        val title = TextView(this).apply {
            text = "Nearby file transfer"
            textSize = 25f
            setTextColor(0xFF211B36.toInt())
            setPadding(0, 0, 0, dp(8))
        }
        val description = TextView(this).apply {
            text = "Send directly between two phones on the same Wi-Fi network. This screen does not use Firebase or Internet signaling. Keep both phones nearby and compare the six-digit security code on both screens before approving."
            textSize = 14f
            setTextColor(0xFF5B5768.toInt())
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        sendButton = Button(this).apply {
            text = "Send a file"
            isAllCaps = false
            setOnClickListener {
                if (!sending.get() && !receiving.get()) outgoingFilePicker.launch(arrayOf("*/*"))
            }
        }
        receiveButton = Button(this).apply {
            text = "Receive a nearby file"
            isAllCaps = false
            setOnClickListener { startReceiving() }
        }
        stopButton = Button(this).apply {
            text = "Cancel / Stop"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { cancelCurrentAction() }
        }
        statusText = TextView(this).apply {
            text = "Choose Send a file or Receive a nearby file."
            textSize = 14f
            setTextColor(0xFF322B46.toInt())
            setPadding(0, dp(18), 0, 0)
            setLineSpacing(dp(3).toFloat(), 1f)
        }

        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(description, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(20) })
        root.addView(sendButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(receiveButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        root.addView(stopButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        root.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)
    }

    private fun prepareOutgoingFile(uri: Uri) {
        val metadata = P2pTransferFileMetadataReader.read(this, uri)
        if (metadata == null || metadata.sizeBytes <= 0L) {
            showStatus("Could not read this file or its size is unknown. Choose a non-empty file.")
            return
        }
        if (!sending.compareAndSet(false, true)) {
            showStatus("Another nearby transfer operation is already active.")
            return
        }
        setOperationUi(true)
        showStatus("Checking file integrity and preparing encrypted transfer…")

        ioExecutor.execute {
            try {
                val digest = hashUri(metadata)
                if (!sending.get()) return@execute
                preparedOutgoing = PreparedOutgoing(metadata, digest)
                peerPromptShown = false
                val discovery = P2pLanDiscovery(
                    this,
                    object : P2pLanDiscovery.Callback {
                        override fun onPeerDiscovered(peer: P2pLanDiscovery.Peer) {
                            if (!sending.get() || peerPromptShown) return
                            if (!peer.sessionId.startsWith(NEARBY_SESSION_PREFIX)) return
                            val address = peer.host.hostAddress.orEmpty()
                            if (peer.host !is Inet4Address ||
                                !P2pTransferValidation.isPrivateLanAddress(address) ||
                                peer.port !in 1..65535
                            ) return
                            peerPromptShown = true
                            runOnUiThread {
                                if (isFinishing || isDestroyed || !sending.get()) return@runOnUiThread
                                AlertDialog.Builder(this@P2pNearbyTransferActivity)
                                    .setTitle("Nearby receiver found")
                                    .setMessage("Connect to a phone offering nearby file reception? Make sure the intended recipient started Receive a nearby file.")
                                    .setPositiveButton("Connect") { _, _ ->
                                        val current = preparedOutgoing
                                        if (current != null && sending.get()) {
                                            outgoingDiscovery?.close()
                                            outgoingDiscovery = null
                                            ioExecutor.execute { sendToPeer(peer, current) }
                                        }
                                    }
                                    .setNegativeButton("Cancel") { _, _ -> cancelCurrentAction() }
                                    .setOnCancelListener { cancelCurrentAction() }
                                    .show()
                            }
                        }

                        override fun onPeerLost(serviceName: String) = Unit
                        override fun onAdvertised(serviceName: String) = Unit
                        override fun onError(operation: String, errorCode: Int) {
                            if (sending.get()) showStatus("Nearby discovery is unavailable. Keep both phones on the same Wi-Fi and try again.")
                        }
                    }
                )
                outgoingDiscovery = discovery
                runOnUiThread {
                    if (sending.get()) {
                        showStatus("Searching for a nearby receiver on the current Wi-Fi…")
                        if (!discovery.startDiscovery()) {
                            showStatus("Could not start nearby discovery. Check that Wi-Fi is enabled.")
                            cancelCurrentAction()
                        }
                    } else {
                        discovery.close()
                    }
                }
            } catch (error: Exception) {
                if (sending.get()) showStatus("Could not prepare this file: " + safeMessage(error))
                finishSending()
            }
        }
    }

    private fun sendToPeer(peer: P2pLanDiscovery.Peer, prepared: PreparedOutgoing) {
        var socket: Socket? = null
        try {
            if (!sending.get()) return
            val keyPair = P2pTransferCrypto.generateEphemeralKeyPair()
            val transferId = UUID.randomUUID().toString()
            val offer = P2pNearbyOffer(
                transferId = transferId,
                fileName = prepared.metadata.fileName,
                sizeBytes = prepared.metadata.sizeBytes,
                mimeType = prepared.metadata.mimeType,
                sha256 = prepared.sha256,
                chunkSize = P2pEncryptedChunkStream.MAX_CHUNK_BYTES,
                senderPublicKeyBase64 = keyPair.publicKeyBase64()
            )
            if (!P2pNearbyTransferProtocol.isValidOffer(offer)) {
                throw IOException("The selected file metadata could not be validated.")
            }

            socket = Socket()
            activeSocket = socket
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            writeControl(output, "offer", mapOf(
                "transferId" to offer.transferId,
                "fileName" to offer.fileName,
                "sizeBytes" to offer.sizeBytes,
                "mimeType" to offer.mimeType,
                "sha256" to offer.sha256,
                "chunkSize" to offer.chunkSize,
                "senderPublicKey" to offer.senderPublicKeyBase64
            ))
            showStatus("Connected locally. Waiting for the receiver to approve the file…")

            val acceptance = readControl(input)
            when (acceptance.optString("type")) {
                "reject" -> throw IOException("The receiver declined or cancelled this transfer.")
                "accept" -> Unit
                else -> throw IOException("Unexpected response from the receiver.")
            }
            val receiverPublicKey = acceptance.optString("receiverPublicKey")
            if (receiverPublicKey.length !in 40..132) {
                throw IOException("The receiver's public key is invalid.")
            }
            val key = P2pTransferCrypto.deriveTransferKey(keyPair, receiverPublicKey, transferId)
            val code = P2pNearbyTransferProtocol.confirmationCode(offer, receiverPublicKey)
            if (!askForCodeConfirmation(
                    "Confirm nearby security code",
                    "File: " + offer.fileName + "\nSize: " +
                        P2pTransferFileMetadataReader.formatBytes(offer.sizeBytes) +
                        "\n\nSecurity code: " + code +
                        "\n\nCompare all six digits with the receiver's phone. Continue only if they match exactly.",
                    "Codes match"
                )
            ) {
                writeControl(output, "cancel")
                throw IOException("Security code was not confirmed.")
            }
            writeControl(output, "sender_confirm")
            val receiverConfirmation = readControl(input)
            when (receiverConfirmation.optString("type")) {
                "cancel", "reject" -> throw IOException("The receiver cancelled security confirmation.")
                "receiver_confirm" -> Unit
                else -> throw IOException("Receiver security confirmation was not received.")
            }
            writeControl(output, "start")
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            showStatus("Sending directly over local Wi-Fi…")

            val source = contentResolver.openInputStream(prepared.metadata.uri)
                ?: throw IOException("Android could not reopen the selected file.")
            val descriptor = P2pEncryptedChunkStream.TransferDescriptor(
                transferId = transferId,
                fileName = prepared.metadata.fileName,
                mimeType = prepared.metadata.mimeType,
                totalBytes = prepared.metadata.sizeBytes,
                sha256Hex = prepared.sha256,
                chunkSize = offer.chunkSize
            )
            P2pEncryptedChunkStream.Sender(source, key, descriptor).use { sender ->
                var lastStatusBytes = 0L
                while (true) {
                    if (!sending.get()) throw IOException("Transfer cancelled.")
                    val frame = sender.nextFrame() ?: break
                    try {
                        writePacket(output, P2pNearbyTransferProtocol.DATA_PACKET, frame)
                    } finally {
                        frame.fill(0)
                    }
                    val processed = sender.bytesProcessed
                    if (processed - lastStatusBytes >= STATUS_UPDATE_BYTES) {
                        lastStatusBytes = processed
                        showStatus("Sending directly over local Wi-Fi… " +
                            P2pTransferFileMetadataReader.formatBytes(processed) + " / " +
                            P2pTransferFileMetadataReader.formatBytes(offer.sizeBytes))
                    }
                }
                val endFrame = sender.finishFrame()
                try {
                    writePacket(output, P2pNearbyTransferProtocol.DATA_PACKET, endFrame)
                    sender.markEndFrameSent()
                } finally {
                    endFrame.fill(0)
                }
            }

            val receipt = readControl(input)
            if (receipt.optString("type") != "complete" ||
                !receipt.optString("sha256").equals(prepared.sha256, ignoreCase = true)
            ) {
                throw IOException("The receiver did not confirm the expected SHA-256 digest.")
            }
            showStatus("File sent and SHA-256 verified by the nearby receiver: " + offer.fileName)
            runOnUiThread {
                Toast.makeText(this, "Nearby transfer completed", Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            if (sending.get()) showStatus("Nearby transfer stopped: " + safeMessage(error))
        } finally {
            runCatching { socket?.close() }
            if (activeSocket === socket) activeSocket = null
            finishSending()
        }
    }

    private fun startReceiving() {
        if (sending.get()) {
            showStatus("Stop the current send attempt before receiving.")
            return
        }
        if (!receiving.compareAndSet(false, true)) {
            showStatus("Nearby reception is already active.")
            return
        }
        setReceiveUi(true)
        showStatus("Opening a temporary local receiver…")
        ioExecutor.execute {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress("0.0.0.0", 0), 4)
                serverSocket = server
                if (!receiving.get()) {
                    runCatching { server.close() }
                    if (serverSocket === server) serverSocket = null
                    return@execute
                }
                val sessionId = NEARBY_SESSION_PREFIX + UUID.randomUUID().toString()
                val discovery = P2pLanDiscovery(
                    this,
                    object : P2pLanDiscovery.Callback {
                        override fun onPeerDiscovered(peer: P2pLanDiscovery.Peer) = Unit
                        override fun onPeerLost(serviceName: String) = Unit
                        override fun onAdvertised(serviceName: String) {
                            showStatus("Ready. Keep this screen open while the sender chooses your phone.")
                        }
                        override fun onError(operation: String, errorCode: Int) {
                            if (receiving.get()) showStatus("Local receiver discovery failed. Stop and start reception again.")
                        }
                    }
                )
                advertisingDiscovery = discovery
                runOnUiThread {
                    if (receiving.get()) {
                        if (!discovery.advertise(sessionId, server.localPort, "StudentReceiver")) {
                            showStatus("Could not advertise this phone on the local Wi-Fi.")
                            stopReceiving()
                        } else {
                            showStatus("Waiting for a nearby sender. Internet is not required for this transfer.")
                        }
                    } else {
                        discovery.close()
                    }
                }
                acceptLoop(server)
            } catch (error: Exception) {
                if (receiving.get()) showStatus("Could not start nearby reception: " + safeMessage(error))
                stopReceiving()
            }
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (receiving.get() && !server.isClosed) {
            val socket = try {
                server.accept()
            } catch (error: Exception) {
                if (receiving.get()) showStatus("Nearby receiver stopped: " + safeMessage(error))
                return
            }
            if (!receiving.get() || !activeIncomingSocket.compareAndSet(false, true)) {
                runCatching { socket.close() }
                continue
            }
            ioExecutor.execute {
                try {
                    handleIncomingSocket(socket)
                } finally {
                    activeIncomingSocket.set(false)
                    if (activeSocket === socket) activeSocket = null
                    runCatching { socket.close() }
                }
            }
        }
    }

    private fun handleIncomingSocket(socket: Socket) {
        var temp: File? = null
        var receiver: P2pEncryptedChunkStream.Receiver? = null
        try {
            activeSocket = socket
            socket.tcpNoDelay = true
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val first = readPacket(input)
            if (first.kind != P2pNearbyTransferProtocol.CONTROL_PACKET ||
                first.payload.size > P2pNearbyTransferProtocol.MAX_CONTROL_BYTES
            ) {
                first.payload.fill(0)
                writeControl(output, "reject", mapOf("reason" to "invalid_offer"))
                throw IOException("The nearby device sent an invalid offer.")
            }
            val offerJson = try {
                JSONObject(String(first.payload, Charsets.UTF_8))
            } finally {
                first.payload.fill(0)
            }
            if (offerJson.optString("type") != "offer") {
                writeControl(output, "reject", mapOf("reason" to "expected_offer"))
                throw IOException("No valid file offer was received.")
            }
            val offer = parseOffer(offerJson)
            if (!P2pNearbyTransferProtocol.isValidOffer(offer)) {
                writeControl(output, "reject", mapOf("reason" to "invalid_metadata"))
                throw IOException("The incoming file metadata failed validation.")
            }

            val pending = PendingIncomingSession(socket, input, output, offer)
            pendingIncomingSession = pending
            runOnUiThread { showIncomingOffer(pending) }
            val decisionReady = pending.selectionReady.await(USER_DECISION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            if (!decisionReady || !pending.accepted || pending.destinationUri == null) {
                runCatching { writeControl(output, "reject", mapOf("reason" to "declined")) }
                showStatus("Incoming nearby transfer was declined or timed out.")
                return
            }
            val destinationUri = pending.destinationUri
                ?: throw IOException("The save destination was not selected.")
            val keyPair = P2pTransferCrypto.generateEphemeralKeyPair()
            val key = P2pTransferCrypto.deriveTransferKey(
                keyPair, offer.senderPublicKeyBase64, offer.transferId
            )
            writeControl(output, "accept", mapOf("receiverPublicKey" to keyPair.publicKeyBase64()))
            val code = P2pNearbyTransferProtocol.confirmationCode(offer, keyPair.publicKeyBase64())
            if (!askForCodeConfirmation(
                    "Confirm nearby security code",
                    "Incoming file: " + offer.fileName + "\nSize: " +
                        P2pTransferFileMetadataReader.formatBytes(offer.sizeBytes) +
                        "\n\nSecurity code: " + code +
                        "\n\nCompare all six digits with the sender's phone. Continue only if they match exactly.",
                    "Codes match"
                )
            ) {
                writeControl(output, "cancel")
                throw IOException("Security code was not confirmed.")
            }
            writeControl(output, "receiver_confirm")
            val senderConfirmation = readControl(input)
            when (senderConfirmation.optString("type")) {
                "cancel", "reject" -> throw IOException("The sender cancelled security confirmation.")
                "sender_confirm" -> Unit
                else -> throw IOException("Sender security confirmation was not received.")
            }
            val start = readControl(input)
            if (start.optString("type") != "start") {
                throw IOException("The sender did not start the approved transfer.")
            }
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            showStatus("Receiving encrypted file directly over local Wi-Fi…")

            temp = File.createTempFile("p2p-nearby-", ".part", cacheDir)
            temporaryDownload = temp
            val descriptor = P2pEncryptedChunkStream.TransferDescriptor(
                transferId = offer.transferId,
                fileName = offer.fileName,
                mimeType = offer.mimeType,
                totalBytes = offer.sizeBytes,
                sha256Hex = offer.sha256,
                chunkSize = offer.chunkSize
            )
            receiver = P2pEncryptedChunkStream.Receiver(FileOutputStream(temp), key, descriptor)
            var lastStatusBytes = 0L
            while (!receiver.isComplete) {
                val packet = readPacket(input)
                if (packet.kind == P2pNearbyTransferProtocol.CONTROL_PACKET) {
                    val control = try {
                        JSONObject(String(packet.payload, Charsets.UTF_8))
                    } finally {
                        packet.payload.fill(0)
                    }
                    if (control.optString("type") == "cancel") throw IOException("The sender cancelled the transfer.")
                    throw IOException("Unexpected control packet during file transfer.")
                }
                if (packet.kind != P2pNearbyTransferProtocol.DATA_PACKET) {
                    packet.payload.fill(0)
                    throw IOException("Unexpected frame type during transfer.")
                }
                try {
                    receiver.acceptFrame(packet.payload)
                } finally {
                    packet.payload.fill(0)
                }
                val receivedBytes = receiver.bytesReceived
                if (receivedBytes - lastStatusBytes >= STATUS_UPDATE_BYTES) {
                    lastStatusBytes = receivedBytes
                    showStatus("Receiving encrypted file… " +
                        P2pTransferFileMetadataReader.formatBytes(receivedBytes) + " / " +
                        P2pTransferFileMetadataReader.formatBytes(offer.sizeBytes))
                }
            }
            receiver.finish()
            receiver.close()
            receiver = null
            promoteVerifiedFile(temp, destinationUri, offer.sizeBytes)
            writeControl(output, "complete", mapOf("sha256" to offer.sha256))
            showStatus("File received and SHA-256 verified over local Wi-Fi: " + offer.fileName)
            runOnUiThread {
                Toast.makeText(this, "Nearby file received and verified", Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            if (receiving.get()) showStatus("Nearby receive stopped: " + safeMessage(error))
            runCatching { writeControl(
                DataOutputStream(BufferedOutputStream(socket.getOutputStream())),
                "error",
                mapOf("reason" to safeMessage(error).take(180))
            ) }
        } finally {
            runCatching { receiver?.close() }
            temp?.delete()
            if (temporaryDownload === temp) temporaryDownload = null
            if (pendingIncomingSession?.socket === socket) pendingIncomingSession = null
        }
    }

    private fun showIncomingOffer(pending: PendingIncomingSession) {
        if (isFinishing || isDestroyed || !receiving.get()) {
            pending.accepted = false
            pending.selectionReady.countDown()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Incoming nearby file")
            .setMessage(
                pending.offer.fileName + "\n" +
                    P2pTransferFileMetadataReader.formatBytes(pending.offer.sizeBytes) +
                    "\n" + pending.offer.mimeType +
                    "\n\nOnly accept if you expected this file from the person next to you."
            )
            .setPositiveButton("Accept & choose save location") { _, _ ->
                pendingIncomingSession = pending
                incomingSavePicker.launch(pending.offer.fileName)
            }
            .setNegativeButton("Decline") { _, _ ->
                pending.accepted = false
                pending.selectionReady.countDown()
            }
            .setOnCancelListener {
                pending.accepted = false
                pending.selectionReady.countDown()
            }
            .show()
    }

    private fun askForCodeConfirmation(
        title: String,
        message: String,
        positiveLabel: String
    ): Boolean {
        val latch = CountDownLatch(1)
        val confirmed = AtomicBoolean(false)
        mainHandler.post {
            if (isFinishing || isDestroyed) {
                latch.countDown()
                return@post
            }
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveLabel) { _, _ ->
                    confirmed.set(true)
                    latch.countDown()
                }
                .setNegativeButton("Cancel transfer") { _, _ ->
                    confirmed.set(false)
                    latch.countDown()
                }
                .setOnCancelListener {
                    confirmed.set(false)
                    latch.countDown()
                }
                .show()
        }
        return latch.await(CONFIRMATION_TIMEOUT_MINUTES, TimeUnit.MINUTES) && confirmed.get()
    }

    private fun parseOffer(json: JSONObject): P2pNearbyOffer = P2pNearbyOffer(
        transferId = json.optString("transferId"),
        fileName = json.optString("fileName"),
        sizeBytes = json.optLong("sizeBytes", -1L),
        mimeType = json.optString("mimeType"),
        sha256 = json.optString("sha256"),
        chunkSize = json.optInt("chunkSize", -1),
        senderPublicKeyBase64 = json.optString("senderPublicKey")
    )

    private fun hashUri(metadata: P2pTransferFileMetadata): String {
        val input = contentResolver.openInputStream(metadata.uri)
            ?: throw IOException("Android could not read the selected file.")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        input.use { stream ->
            val buffer = ByteArray(32 * 1024)
            try {
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total = Math.addExact(total, count.toLong())
                    if (total > metadata.sizeBytes) throw IOException("The selected file changed while being prepared.")
                    digest.update(buffer, 0, count)
                }
            } finally {
                buffer.fill(0)
            }
        }
        if (total != metadata.sizeBytes) throw IOException("The selected file size changed while being prepared.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun promoteVerifiedFile(temp: File, destinationUri: Uri, expectedSize: Long) {
        if (!temp.isFile || temp.length() != expectedSize) {
            throw IOException("The verified temporary file has an unexpected length.")
        }
        val source = FileInputStream(temp)
        val destination = contentResolver.openOutputStream(destinationUri, "w")
            ?: throw IOException("Android could not open the selected save destination.")
        source.use { input ->
            destination.use { output ->
                input.copyTo(output, 64 * 1024)
                output.flush()
            }
        }
    }

    private fun writeControl(
        output: DataOutputStream,
        type: String,
        values: Map<String, Any?> = emptyMap()
    ) {
        val json = JSONObject()
        json.put("type", type)
        values.forEach { (key, value) -> if (value != null) json.put(key, value) }
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        try {
            if (payload.size > P2pNearbyTransferProtocol.MAX_CONTROL_BYTES) {
                throw IOException("Nearby control message is too large.")
            }
            writePacket(output, P2pNearbyTransferProtocol.CONTROL_PACKET, payload)
        } finally {
            payload.fill(0)
        }
    }

    private fun readControl(input: DataInputStream): JSONObject {
        val packet = readPacket(input)
        if (packet.kind != P2pNearbyTransferProtocol.CONTROL_PACKET ||
            packet.payload.size > P2pNearbyTransferProtocol.MAX_CONTROL_BYTES
        ) {
            packet.payload.fill(0)
            throw IOException("Expected a bounded nearby control message.")
        }
        return try {
            JSONObject(String(packet.payload, Charsets.UTF_8))
        } finally {
            packet.payload.fill(0)
        }
    }

    private fun writePacket(output: DataOutputStream, kind: Int, payload: ByteArray) {
        if (payload.isEmpty() || payload.size > P2pNearbyTransferProtocol.MAX_PACKET_BYTES) {
            throw IOException("Nearby packet size is outside protocol limits.")
        }
        output.writeByte(kind)
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }

    private fun readPacket(input: DataInputStream): Packet {
        val kind = input.readUnsignedByte()
        val length = input.readInt()
        if (length !in 1..P2pNearbyTransferProtocol.MAX_PACKET_BYTES) {
            throw IOException("Nearby packet length is invalid.")
        }
        val payload = ByteArray(length)
        input.readFully(payload)
        return Packet(kind, payload)
    }

    private fun cancelCurrentAction() {
        if (receiving.get()) {
            stopReceiving()
            showStatus("Nearby reception stopped.")
            return
        }
        sending.set(false)
        outgoingDiscovery?.close()
        outgoingDiscovery = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        finishSending()
        showStatus("Nearby transfer cancelled.")
    }

    private fun stopReceiving() {
        receiving.set(false)
        advertisingDiscovery?.close()
        advertisingDiscovery = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        val pending = pendingIncomingSession
        if (pending != null) {
            pending.accepted = false
            pending.selectionReady.countDown()
            pendingIncomingSession = null
        }
        if (!isFinishing && !isDestroyed) setReceiveUi(false)
    }

    private fun finishSending() {
        sending.set(false)
        outgoingDiscovery?.close()
        outgoingDiscovery = null
        preparedOutgoing = null
        runOnUiThread {
            if (!isFinishing && !isDestroyed && !receiving.get()) setOperationUi(false)
        }
    }

    private fun setOperationUi(active: Boolean) {
        sendButton.isEnabled = !active && !receiving.get()
        receiveButton.isEnabled = !active && !receiving.get()
        stopButton.isEnabled = active || receiving.get()
    }

    private fun setReceiveUi(active: Boolean) {
        sendButton.isEnabled = !active && !sending.get()
        receiveButton.isEnabled = !active && !sending.get()
        stopButton.isEnabled = active || sending.get()
    }

    private fun showStatus(message: String) {
        mainHandler.post {
            if (!isFinishing && !isDestroyed && ::statusText.isInitialized) {
                statusText.text = message
            }
        }
    }

    private fun safeMessage(error: Exception): String =
        error.message?.takeIf { it.isNotBlank() }?.take(180) ?: "Please retry on the same Wi-Fi."

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        val pending = pendingIncomingSession
        if (pending != null) {
            pending.accepted = false
            pending.selectionReady.countDown()
        }
        receiving.set(false)
        sending.set(false)
        runCatching { advertisingDiscovery?.close() }
        runCatching { outgoingDiscovery?.close() }
        advertisingDiscovery = null
        outgoingDiscovery = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        temporaryDownload?.delete()
        temporaryDownload = null
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val HANDSHAKE_TIMEOUT_MS = 3 * 60 * 1000
        const val SOCKET_READ_TIMEOUT_MS = 2 * 60 * 1000
        const val USER_DECISION_TIMEOUT_MINUTES = 3L
        const val CONFIRMATION_TIMEOUT_MINUTES = 3L
        const val STATUS_UPDATE_BYTES = 1024L * 1024L
        const val NEARBY_SESSION_PREFIX = "nearby_"
    }
}
