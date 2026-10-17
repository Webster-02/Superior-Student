package com.superiorstudent.app.feature.transfer

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.superiorstudent.app.feature.transfer.data.FirebaseP2pTransferSignaling
import com.superiorstudent.app.feature.transfer.data.P2pOfferState
import com.superiorstudent.app.feature.transfer.data.P2pTransferFileMetadata
import com.superiorstudent.app.feature.transfer.data.P2pTransferFileMetadataReader
import com.superiorstudent.app.feature.transfer.data.P2pTransferOffer
import com.superiorstudent.app.feature.transfer.data.P2pTransferOfferDraft
import com.superiorstudent.app.feature.transfer.data.P2pTransferValidation
import com.superiorstudent.app.feature.transfer.security.P2pEncryptedChunkStream
import com.superiorstudent.app.feature.transfer.security.P2pTransferCrypto
import com.superiorstudent.app.feature.transfer.transport.P2pDataChannelTransport
import com.superiorstudent.app.feature.transfer.transport.P2pFirebaseWebRtcCoordinator
import com.superiorstudent.app.feature.transfer.transport.P2pLanDiscovery
import com.superiorstudent.app.feature.transfer.transport.P2pLanSocketListener
import com.superiorstudent.app.feature.transfer.transport.P2pLanSocketTransport
import org.webrtc.PeerConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.SecretKey

/**
 * Chat-scoped file transfer orchestration. Firestore carries metadata/signaling only; file bytes
 * move over an authenticated WebRTC DataChannel as AES-GCM authenticated frames.
 *
 * TURN credentials are deliberately not embedded here. The included public STUN server may work
 * for direct connectivity, but relay fallback needs a trusted short-lived credential issuer.
 */
class P2pChatFileTransferManager(
    context: Context,
    private val callback: Callback,
    private val signaling: FirebaseP2pTransferSignaling = FirebaseP2pTransferSignaling()
) : AutoCloseable {

    interface Callback {
        fun onStatus(fileName: String, detail: String)
        fun onCompleted(fileName: String, outgoing: Boolean)
        fun onError(fileName: String, message: String, error: Throwable? = null)
    }

    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val closed = AtomicBoolean(false)
    private val workExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val runtimes = ConcurrentHashMap<String, RuntimeTransfer>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    private data class LanCandidate(val ip: String, val port: Int, val token: String)

    private class RuntimeTransfer(
        @Volatile var offer: P2pTransferOffer,
        val outgoing: Boolean,
        val localKeyPair: P2pTransferCrypto.EphemeralKeyPair,
        val sourceUri: Uri? = null,
        val destinationUri: Uri? = null,
        @Volatile var temporaryFile: File? = null,
        @Volatile var key: SecretKey? = null
    ) {
        /** Sender pauses only at encrypted-frame boundaries; the live stream stays open. */
        @Volatile var pauseRequested: Boolean = false
        val disposed = AtomicBoolean(false)
        val sending = AtomicBoolean(false)
        val transferStarted = AtomicBoolean(false)
        val transferStatusConfirmed = AtomicBoolean(false)
        val completed = AtomicBoolean(false)
        val keyReceived = AtomicBoolean(false)
        val receiveExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        @Volatile var coordinator: P2pFirebaseWebRtcCoordinator? = null
        @Volatile var offerListener: ListenerRegistration? = null
        @Volatile var senderStream: P2pEncryptedChunkStream.Sender? = null
        @Volatile var receiverStream: P2pEncryptedChunkStream.Receiver? = null
        @Volatile var lastReportedBytes: Long = 0L
        @Volatile var lanDiscovery: P2pLanDiscovery? = null
        @Volatile var lanListener: P2pLanSocketListener? = null
        @Volatile var lanTransport: P2pLanSocketTransport? = null
        @Volatile var lanCandidate: LanCandidate? = null
        @Volatile var lanPeer: P2pLanDiscovery.Peer? = null
        val lanConnectStarted = AtomicBoolean(false)
        val lanPathDecisionMade = AtomicBoolean(false)
        val lanFallbackScheduled = AtomicBoolean(false)
        val lanReceiverGraceScheduled = AtomicBoolean(false)
    }

    /**
     * Creates metadata and a fresh sender key, then waits for the receiver to accept.
     * File contents are read only for hashing and later streamed through the encrypted channel.
     */
    fun sendFile(
        metadata: P2pTransferFileMetadata,
        conversationId: String,
        receiverUid: String,
        onOfferCreated: (P2pTransferOffer?, Exception?) -> Unit
    ) {
        if (closed.get()) {
            onOfferCreated(null, IllegalStateException("File-transfer manager is closed."))
            return
        }
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified) {
            onOfferCreated(null, IllegalStateException("A verified Student Connect account is required."))
            return
        }
        if (metadata.sizeBytes <= 0L) {
            onOfferCreated(null, IllegalArgumentException("Choose a non-empty file with a known size."))
            return
        }
        if (!P2pTransferValidation.isConversationIdForPair(conversationId, user.uid, receiverUid)) {
            onOfferCreated(null, IllegalArgumentException("This file transfer does not match the current chat participants."))
            return
        }

        callbackStatus(metadata.fileName, "Preparing file securely…")
        workExecutor.execute {
            try {
                val digest = hashUri(metadata.uri, metadata.sizeBytes)
                val keyPair = P2pTransferCrypto.generateEphemeralKeyPair()
                val draft = P2pTransferOfferDraft(
                    conversationId = conversationId,
                    receiverUid = receiverUid,
                    fileName = metadata.fileName,
                    sizeBytes = metadata.sizeBytes,
                    mimeType = metadata.mimeType,
                    sha256 = digest,
                    chunkSize = P2pEncryptedChunkStream.MAX_CHUNK_BYTES,
                    senderPublicKeyBase64 = keyPair.publicKeyBase64()
                )
                signaling.createOffer(draft) { offer, error ->
                    if (closed.get()) {
                        onOfferCreated(null, IllegalStateException("Chat closed before the offer was created."))
                        return@createOffer
                    }
                    if (error != null || offer == null) {
                        onOfferCreated(null, error ?: IllegalStateException("File offer could not be created."))
                        callbackError(metadata.fileName, "Could not create the file offer. Check the published Firestore P2P rules.", error)
                        return@createOffer
                    }

                    val runtime = RuntimeTransfer(
                        offer = offer,
                        outgoing = true,
                        localKeyPair = keyPair,
                        sourceUri = metadata.uri
                    )
                    runtimes[offer.transferId] = runtime
                    watchOutgoingOffer(runtime)
                    callbackStatus(metadata.fileName, "File offer sent. Waiting for your classmate to accept…")
                    onOfferCreated(offer, null)
                }
            } catch (error: Exception) {
                callbackError(metadata.fileName, "Could not prepare the file for encrypted transfer.", error)
                onOfferCreated(null, error)
            }
        }
    }

    /** Accepts an incoming offer only after the user has selected a save destination. */
    fun acceptIncoming(
        offer: P2pTransferOffer,
        destinationUri: Uri,
        onAccepted: (Exception?) -> Unit
    ) {
        if (closed.get()) {
            onAccepted(IllegalStateException("File-transfer manager is closed."))
            return
        }
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified || user.uid != offer.receiverUid) {
            onAccepted(IllegalStateException("Only the authenticated receiver can accept this offer."))
            return
        }
        if (offer.status != P2pOfferState.OFFERED ||
            !P2pTransferValidation.isConversationIdForPair(offer.conversationId, offer.senderUid, offer.receiverUid)
        ) {
            onAccepted(IllegalArgumentException("This file offer is no longer valid."))
            return
        }

        workExecutor.execute {
            var receiverStream: P2pEncryptedChunkStream.Receiver? = null
            var temporaryFile: File? = null
            try {
                val keyPair = P2pTransferCrypto.generateEphemeralKeyPair()
                // The sender key is bound to the signed-in sender's accepted offer document.
                val key = P2pTransferCrypto.deriveTransferKey(
                    keyPair, offer.senderPublicKeyBase64, offer.transferId
                )
                val createdTempFile = File.createTempFile(
                    "p2p-" + offer.transferId.take(32) + "-",
                    ".part",
                    appContext.cacheDir
                )
                temporaryFile = createdTempFile
                val output = FileOutputStream(createdTempFile)
                val descriptor = descriptorFor(offer)
                val preparedReceiver = P2pEncryptedChunkStream.Receiver(output, key, descriptor)
                receiverStream = preparedReceiver

                signaling.acceptOffer(offer.conversationId, offer.transferId) { error ->
                    if (closed.get()) {
                        preparedReceiver.close()
                        createdTempFile.delete()
                        deletePartialDestination(destinationUri)
                        onAccepted(IllegalStateException("Chat closed before accepting the offer."))
                        return@acceptOffer
                    }
                    if (error != null) {
                        preparedReceiver.close()
                        createdTempFile.delete()
                        deletePartialDestination(destinationUri)
                        callbackError(offer.fileName, "Could not accept the file offer. Check the published Firestore P2P rules.", error)
                        onAccepted(error)
                        return@acceptOffer
                    }

                    val acceptedOffer = offer.copy(status = P2pOfferState.ACCEPTED)
                    val runtime = RuntimeTransfer(
                        offer = acceptedOffer,
                        outgoing = false,
                        localKeyPair = keyPair,
                        destinationUri = destinationUri,
                        temporaryFile = createdTempFile,
                        key = key
                    )
                    runtime.receiverStream = preparedReceiver
                    runtimes[offer.transferId] = runtime
                    val startError = startCoordinator(runtime)
                    if (startError != null) {
                        failRuntime(runtime, "Could not start the secure file connection.", startError, deleteDestination = true)
                        onAccepted(startError)
                        return@acceptOffer
                    }
                    callbackStatus(offer.fileName, "Accepted. Connecting securely to the sender…")
                    onAccepted(null)
                }
            } catch (error: Exception) {
                runCatching { receiverStream?.close() }
                temporaryFile?.delete()
                deletePartialDestination(destinationUri)
                callbackError(offer.fileName, "Could not prepare the secure download.", error)
                onAccepted(error)
            }
        }
    }

    fun rejectIncoming(offer: P2pTransferOffer, onRejected: (Exception?) -> Unit = {}) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified || user.uid != offer.receiverUid) {
            onRejected(IllegalStateException("Only the authenticated receiver can reject this offer."))
            return
        }
        signaling.rejectOffer(offer.conversationId, offer.transferId, onRejected)
    }

    private fun watchOutgoingOffer(runtime: RuntimeTransfer) {
        runtime.offerListener = signaling.listenForOffer(
            runtime.offer.conversationId,
            runtime.offer.transferId
        ) { observed, error ->
            if (runtime.disposed.get() || closed.get()) return@listenForOffer
            if (error != null) {
                failRuntime(runtime, "Could not monitor the file offer.", error, deleteDestination = false)
                return@listenForOffer
            }
            if (observed == null) return@listenForOffer
            runtime.offer = observed
            when (observed.status) {
                P2pOfferState.ACCEPTED -> {
                    if (runtime.coordinator == null) {
                        val startError = startCoordinator(runtime)
                        if (startError != null) failRuntime(runtime, "Could not start the secure file connection.", startError, false)
                        else callbackStatus(observed.fileName, "Your classmate accepted. Establishing encrypted connection…")
                    }
                }
                P2pOfferState.TRANSFERRING -> {
                    // A possibly delayed TRANSFERRING snapshot must not undo a local pause
                    // request. The explicit resume callback clears pauseRequested.
                    if (!runtime.pauseRequested) {
                        callbackStatus(observed.fileName, "Encrypted file transfer in progress…")
                    }
                }
                P2pOfferState.PAUSED -> {
                    runtime.pauseRequested = true
                    callbackStatus(observed.fileName, "File transfer paused. Tap Resume to continue.")
                }
                P2pOfferState.COMPLETED -> completeRuntime(runtime)
                P2pOfferState.REJECTED -> failRuntime(runtime, "Your classmate declined the file.", null, false)
                P2pOfferState.CANCELLED, P2pOfferState.FAILED -> failRuntime(runtime, "The file transfer did not complete.", null, false)
                else -> Unit
            }
        }
    }

    /** Returns null when the signaling/DataChannel coordinator starts successfully. */
    private fun startCoordinator(runtime: RuntimeTransfer): Exception? {
        if (closed.get() || runtime.disposed.get()) return IllegalStateException("Transfer is no longer active.")
        if (runtime.coordinator != null) return null
        return try {
            val user = auth.currentUser ?: throw IllegalStateException("Sign-in expired.")
            val acceptedOffer = runtime.offer.copy(status = P2pOfferState.ACCEPTED)
            val coordinator = P2pFirebaseWebRtcCoordinator(
                context = appContext,
                signaling = signaling,
                offer = acceptedOffer,
                currentUid = user.uid,
                iceServers = iceServers,
                callback = object : P2pFirebaseWebRtcCoordinator.Callback {
                    override fun onPeerConnectionStateChanged(
                        state: PeerConnection.PeerConnectionState,
                        detail: String?
                    ) {
                        when (state) {
                            PeerConnection.PeerConnectionState.CONNECTED ->
                                callbackStatus(runtime.offer.fileName, "Peer connected. Waiting for encrypted file channel…")
                            PeerConnection.PeerConnectionState.FAILED,
                            PeerConnection.PeerConnectionState.CLOSED -> {
                                if (!runtime.completed.get() && !runtime.disposed.get()) {
                                    if (runtime.lanTransport != null) {
                                        callbackStatus(
                                            runtime.offer.fileName,
                                            "WebRTC is unavailable; continuing on the authenticated local Wi-Fi connection."
                                        )
                                    } else if (runtime.outgoing) {
                                        scheduleLanFallback(runtime)
                                    } else {
                                        scheduleLanReceiverGrace(runtime)
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }

                    override fun onDataChannelStateChanged(
                        state: P2pDataChannelTransport.State,
                        detail: String?
                    ) {
                        when (state) {
                            P2pDataChannelTransport.State.CONNECTED -> {
                                markTransferring(runtime)
                                if (runtime.outgoing) {
                                    scheduleLanFallback(runtime)
                                    maybeStartSender(runtime)
                                } else {
                                    callbackStatus(runtime.offer.fileName, "Secure channel ready. Receiving encrypted file…")
                                }
                            }
                            P2pDataChannelTransport.State.FAILED -> {
                                if (runtime.lanTransport != null) {
                                    callbackStatus(
                                        runtime.offer.fileName,
                                        "WebRTC data channel failed; continuing on the authenticated local Wi-Fi connection."
                                    )
                                } else if (runtime.outgoing) {
                                    scheduleLanFallback(runtime)
                                } else {
                                    scheduleLanReceiverGrace(runtime)
                                }
                            }
                            else -> Unit
                        }
                    }

                    override fun onBinaryFrame(frame: ByteArray) {
                        if (runtime.outgoing || runtime.disposed.get()) {
                            frame.fill(0)
                            return
                        }
                        try {
                            runtime.receiveExecutor.execute { processIncomingFrame(runtime, frame) }
                        } catch (error: Exception) {
                            frame.fill(0)
                            failRuntime(runtime, "Could not queue an incoming encrypted file frame.", error, true)
                        }
                    }

                    override fun onBufferedAmountChanged(bufferedBytes: Long) = Unit

                    override fun onRemoteLanCandidate(payload: Map<String, Any?>) {
                        if (runtime.outgoing && !runtime.disposed.get()) {
                            handleRemoteLanCandidate(runtime, payload)
                        }
                    }

                    override fun onRemotePublicKey(publicKeyBase64: String) {
                        if (!runtime.outgoing || !runtime.keyReceived.compareAndSet(false, true)) {
                            failRuntime(runtime, "Unexpected or duplicate peer encryption key.", null, !runtime.outgoing)
                            return
                        }
                        workExecutor.execute {
                            try {
                                runtime.key = P2pTransferCrypto.deriveTransferKey(
                                    runtime.localKeyPair, publicKeyBase64, runtime.offer.transferId
                                )
                                callbackStatus(runtime.offer.fileName, "Peer key authenticated. Waiting for channel…")
                                maybeStartSender(runtime)
                            } catch (error: Exception) {
                                failRuntime(runtime, "Could not verify the peer's encryption key.", error, false)
                            }
                        }
                    }

                    override fun onError(message: String, error: Throwable?) {
                        if (!runtime.disposed.get()) callbackError(runtime.offer.fileName, message, error)
                    }
                }
            )
            runtime.coordinator = coordinator
            if (!coordinator.start()) {
                runtime.coordinator = null
                coordinator.close()
                throw IllegalStateException("WebRTC signaling could not start.")
            }
            if (!runtime.outgoing) {
                coordinator.publishReceiverPublicKey(runtime.localKeyPair.publicKeyBase64()) { error ->
                    if (error != null && !runtime.disposed.get()) {
                        failRuntime(runtime, "Could not publish receiver encryption key.", error, true)
                    }
                }
                startLanReceiverEndpoint(runtime)
            } else {
                startLanDiscovery(runtime)
            }
            null
        } catch (error: Exception) {
            error
        }
    }

    private fun markTransferring(runtime: RuntimeTransfer) {
        // Firestore rules intentionally reserve the accepted -> transferring transition for sender.
        if (!runtime.outgoing || !runtime.transferStarted.compareAndSet(false, true)) return
        signaling.startTransfer(runtime.offer.conversationId, runtime.offer.transferId) { error ->
            if (runtime.disposed.get()) return@startTransfer
            if (error != null) {
                failRuntime(runtime, "Connection opened, but Firestore could not confirm transfer status.", error, false)
            } else {
                runtime.transferStatusConfirmed.set(true)
                maybeStartSender(runtime)
            }
        }
    }

    private fun maybeStartSender(runtime: RuntimeTransfer) {
        if (!runtime.outgoing || runtime.disposed.get() || runtime.completed.get()) return
        val hasLanTransport = runtime.lanTransport != null
        val hasWebRtcTransport =
            runtime.coordinator?.currentDataChannelState() == P2pDataChannelTransport.State.CONNECTED
        if (!runtime.lanPathDecisionMade.get() || runtime.key == null ||
            !runtime.transferStatusConfirmed.get() || (!hasLanTransport && !hasWebRtcTransport) ||
            !runtime.sending.compareAndSet(false, true)
        ) return
        workExecutor.execute { runSender(runtime) }
    }

    /** Pause an active outgoing transfer without closing its authenticated stream. */
    fun pauseOutgoingTransfer(transferId: String, callback: (Exception?) -> Unit = {}) {
        val user = auth.currentUser
        val runtime = runtimes[transferId]
        if (user == null || !user.isEmailVerified) {
            callback(IllegalStateException("A verified Student Connect account is required."))
            return
        }
        if (runtime == null || !runtime.outgoing || runtime.disposed.get() || runtime.completed.get()) {
            callback(IllegalStateException("This outgoing transfer is no longer active."))
            return
        }
        if (!runtime.transferStatusConfirmed.get()) {
            callback(IllegalStateException("Wait until the file transfer has started before pausing."))
            return
        }
        if (runtime.pauseRequested) {
            callback(null)
            return
        }

        // Stop producing/sending frames immediately while Firestore confirms the shared state.
        runtime.pauseRequested = true
        signaling.pauseTransfer(runtime.offer.conversationId, runtime.offer.transferId) { error ->
            if (runtime.disposed.get()) {
                callback(error ?: IllegalStateException("Transfer is no longer active."))
                return@pauseTransfer
            }
            if (error != null) {
                runtime.pauseRequested = false
                callback(error)
            } else {
                runtime.offer = runtime.offer.copy(status = P2pOfferState.PAUSED)
                callbackStatus(runtime.offer.fileName, "File transfer paused at a safe chunk boundary.")
                callback(null)
            }
        }
    }

    /** Resume an outgoing transfer that was paused while its secure session remained alive. */
    fun resumeOutgoingTransfer(transferId: String, callback: (Exception?) -> Unit = {}) {
        val user = auth.currentUser
        val runtime = runtimes[transferId]
        if (user == null || !user.isEmailVerified) {
            callback(IllegalStateException("A verified Student Connect account is required."))
            return
        }
        if (runtime == null || !runtime.outgoing || runtime.disposed.get() || runtime.completed.get()) {
            callback(IllegalStateException("This outgoing transfer is no longer active."))
            return
        }
        if (!runtime.pauseRequested) {
            callback(IllegalStateException("This transfer is not paused."))
            return
        }

        signaling.startTransfer(runtime.offer.conversationId, runtime.offer.transferId) { error ->
            if (runtime.disposed.get()) {
                callback(error ?: IllegalStateException("Transfer is no longer active."))
                return@startTransfer
            }
            if (error != null) {
                callback(error)
            } else {
                runtime.offer = runtime.offer.copy(status = P2pOfferState.TRANSFERRING)
                runtime.pauseRequested = false
                callbackStatus(runtime.offer.fileName, "File transfer resumed.")
                callback(null)
            }
        }
    }

    /**
     * The receiver opens one short-lived TCP listener only after the user accepts the offer.
     * Its random token is sent to the authenticated sender via Firestore, never in mDNS TXT data
     * and never directly over the LAN socket.
     */
    private fun startLanReceiverEndpoint(runtime: RuntimeTransfer) {
        workExecutor.execute {
            if (closed.get() || runtime.disposed.get()) return@execute
            var listener: P2pLanSocketListener? = null
            var discovery: P2pLanDiscovery? = null
            try {
                val createdListener = P2pLanSocketListener(runtime.offer.transferId,
                    object : P2pLanSocketListener.Callback {
                        override fun onAuthenticatedTransport(transport: P2pLanSocketTransport) {
                            if (closed.get() || runtime.disposed.get()) {
                                transport.close()
                                return
                            }
                            runtime.lanTransport = transport
                            callbackStatus(
                                runtime.offer.fileName,
                                "Authenticated direct Wi-Fi connection established. Receiving encrypted file…"
                            )
                        }

                        override fun onEncryptedFrame(frame: ByteArray) {
                            if (closed.get() || runtime.disposed.get()) {
                                frame.fill(0)
                                return
                            }
                            try {
                                runtime.receiveExecutor.execute { processIncomingFrame(runtime, frame) }
                            } catch (error: Exception) {
                                frame.fill(0)
                                failRuntime(
                                    runtime,
                                    "Could not queue an authenticated LAN frame.",
                                    error,
                                    deleteDestination = true
                                )
                            }
                        }

                        override fun onError(error: Exception) {
                            if (runtime.disposed.get() || runtime.completed.get()) return
                            if (runtime.lanTransport != null) {
                                failRuntime(
                                    runtime,
                                    "Direct LAN connection was interrupted. Restart the file transfer.",
                                    error,
                                    deleteDestination = true
                                )
                            } else {
                                callbackStatus(
                                    runtime.offer.fileName,
                                    "Direct LAN unavailable; keeping the encrypted WebRTC fallback ready."
                                )
                            }
                        }
                    })
                listener = createdListener
                val endpoint = createdListener.start()
                runtime.lanListener = createdListener

                val createdDiscovery = P2pLanDiscovery(appContext,
                    object : P2pLanDiscovery.Callback {
                        override fun onPeerDiscovered(peer: P2pLanDiscovery.Peer) = Unit
                        override fun onPeerLost(serviceName: String) = Unit
                        override fun onAdvertised(serviceName: String) {
                            callbackStatus(
                                runtime.offer.fileName,
                                "Advertising a temporary local endpoint for this accepted transfer…"
                            )
                        }
                        override fun onError(operation: String, errorCode: Int) {
                            if (!runtime.disposed.get()) {
                                callbackStatus(
                                    runtime.offer.fileName,
                                    "LAN discovery unavailable; the secure WebRTC fallback remains available."
                                )
                            }
                        }
                    })
                discovery = createdDiscovery
                runtime.lanDiscovery = createdDiscovery

                if (!createdDiscovery.advertise(runtime.offer.transferId, endpoint.port)) {
                    throw IllegalStateException("Android could not advertise the local LAN endpoint.")
                }

                val coordinator = runtime.coordinator
                    ?: throw IllegalStateException("Authenticated transfer signaling is not ready.")
                coordinator.publishLanCandidate(endpoint.ip, endpoint.port, endpoint.token) { error ->
                    if (runtime.disposed.get()) return@publishLanCandidate
                    if (error != null) {
                        createdDiscovery.close()
                        createdListener.close()
                        if (runtime.lanDiscovery === createdDiscovery) runtime.lanDiscovery = null
                        if (runtime.lanListener === createdListener) runtime.lanListener = null
                        callbackStatus(
                            runtime.offer.fileName,
                            "Could not share the LAN endpoint; the secure WebRTC fallback remains available."
                        )
                    } else {
                        callbackStatus(
                            runtime.offer.fileName,
                            "Local endpoint ready. Waiting for the sender to verify this device…"
                        )
                    }
                }
            } catch (error: Exception) {
                runCatching { discovery?.close() }
                runCatching { listener?.close() }
                if (runtime.lanDiscovery === discovery) runtime.lanDiscovery = null
                if (runtime.lanListener === listener) runtime.lanListener = null
                if (!runtime.disposed.get()) {
                    callbackStatus(
                        runtime.offer.fileName,
                        "Direct LAN setup unavailable; the secure WebRTC fallback remains available."
                    )
                }
            }
        }
    }

    /** Sender discovers only the one-time service belonging to this accepted transfer. */
    private fun startLanDiscovery(runtime: RuntimeTransfer) {
        val discovery = P2pLanDiscovery(appContext,
            object : P2pLanDiscovery.Callback {
                override fun onPeerDiscovered(peer: P2pLanDiscovery.Peer) {
                    if (closed.get() || runtime.disposed.get() || !runtime.outgoing) return
                    if (peer.sessionId != runtime.offer.transferId) return
                    if (peer.host !is Inet4Address ||
                        !P2pTransferValidation.isPrivateLanAddress(peer.host.hostAddress)
                    ) return
                    runtime.lanPeer = peer
                    tryConnectLanSender(runtime)
                }

                override fun onPeerLost(serviceName: String) {
                    if (runtime.lanPeer?.serviceName == serviceName) runtime.lanPeer = null
                }

                override fun onAdvertised(serviceName: String) = Unit

                override fun onError(operation: String, errorCode: Int) {
                    if (!runtime.disposed.get() && !runtime.lanPathDecisionMade.get()) {
                        callbackStatus(
                            runtime.offer.fileName,
                            "Local discovery is unavailable; waiting for the secure WebRTC fallback."
                        )
                    }
                }
            })
        runtime.lanDiscovery = discovery
        if (!discovery.startDiscovery()) {
            chooseWebRtcFallback(runtime, "Local discovery could not start; using secure WebRTC.")
        }
    }

    private fun handleRemoteLanCandidate(runtime: RuntimeTransfer, payload: Map<String, Any?>) {
        if (!runtime.outgoing || runtime.disposed.get() || runtime.lanPathDecisionMade.get()) return
        val ip = payload["ip"] as? String
        val port = (payload["port"] as? Number)?.toInt()
        val token = payload["token"] as? String
        if (ip.isNullOrBlank() || port == null || port !in 1..65535 || token.isNullOrBlank() ||
            !P2pTransferValidation.isPrivateLanAddress(ip) || token.length !in 32..128
        ) {
            chooseWebRtcFallback(runtime, "Invalid LAN endpoint; using secure WebRTC.")
            return
        }
        runtime.lanCandidate = LanCandidate(ip, port!!, token)
        tryConnectLanSender(runtime)
    }

    private fun tryConnectLanSender(runtime: RuntimeTransfer) {
        if (!runtime.outgoing || runtime.disposed.get() || runtime.completed.get() ||
            runtime.lanPathDecisionMade.get() || runtime.sending.get()
        ) return
        val peer = runtime.lanPeer ?: return
        val candidate = runtime.lanCandidate ?: return
        if (!runtime.lanConnectStarted.compareAndSet(false, true)) return

        // Require the NSD result and authenticated Firestore candidate to identify the same endpoint.
        // If the router/device returns a different interface address, fail closed to WebRTC.
        if (peer.sessionId != runtime.offer.transferId ||
            peer.port != candidate.port ||
            peer.host.hostAddress != candidate.ip
        ) {
            chooseWebRtcFallback(runtime, "LAN endpoint could not be verified through local discovery.")
            return
        }

        callbackStatus(runtime.offer.fileName, "Verifying the local Wi-Fi endpoint…")
        workExecutor.execute {
            try {
                val transport = P2pLanSocketTransport.connectAuthenticated(
                    address = peer.host,
                    port = candidate.port,
                    transferId = runtime.offer.transferId,
                    token = candidate.token,
                    connectTimeoutMs = LAN_CONNECT_TIMEOUT_MS,
                    handshakeTimeoutMs = LAN_HANDSHAKE_TIMEOUT_MS,
                    callback = object : P2pLanSocketTransport.Callback {
                        override fun onEncryptedFrame(frame: ByteArray) {
                            frame.fill(0)
                            if (!runtime.disposed.get()) {
                                failRuntime(
                                    runtime,
                                    "Unexpected reverse-direction LAN data was rejected.",
                                    null,
                                    deleteDestination = false
                                )
                            }
                        }

                        override fun onError(error: Exception) {
                            if (!runtime.disposed.get() && !runtime.completed.get()) {
                                failRuntime(
                                    runtime,
                                    "Direct LAN connection was interrupted. Restart the file transfer.",
                                    error,
                                    deleteDestination = false
                                )
                            }
                        }
                    }
                )

                if (runtime.disposed.get() || runtime.sending.get() ||
                    !runtime.lanPathDecisionMade.compareAndSet(false, true)
                ) {
                    transport.close()
                    return@execute
                }
                runtime.lanTransport = transport
                runtime.lanDiscovery?.stopDiscovery()
                callbackStatus(
                    runtime.offer.fileName,
                    "Direct Wi-Fi connection authenticated. Sending encrypted file locally…"
                )
                markTransferring(runtime)
                maybeStartSender(runtime)
            } catch (error: Exception) {
                if (!runtime.disposed.get()) {
                    chooseWebRtcFallback(runtime, "Direct LAN connection failed; using secure WebRTC.")
                }
            }
        }
    }

    private fun scheduleLanFallback(runtime: RuntimeTransfer) {
        if (!runtime.outgoing || !runtime.lanFallbackScheduled.compareAndSet(false, true)) return
        workExecutor.execute {
            try {
                Thread.sleep(LAN_DISCOVERY_GRACE_MS)
            } catch (_: InterruptedException) {
                return@execute
            }
            if (!runtime.disposed.get() && !runtime.completed.get() && runtime.lanTransport == null) {
                runtime.lanPathDecisionMade.set(true)
                runtime.lanDiscovery?.stopDiscovery()
                val webRtcReady =
                    runtime.coordinator?.currentDataChannelState() == P2pDataChannelTransport.State.CONNECTED
                if (!webRtcReady) {
                    failRuntime(
                        runtime,
                        "Neither direct LAN nor WebRTC could connect. Keep both phones on the same Wi-Fi and retry.",
                        null,
                        deleteDestination = !runtime.outgoing
                    )
                    return@execute
                }
                callbackStatus(
                    runtime.offer.fileName,
                    "No verified LAN endpoint found; sending through the secure WebRTC route."
                )
                maybeStartSender(runtime)
            }
        }
    }

    private fun scheduleLanReceiverGrace(runtime: RuntimeTransfer) {
        if (runtime.outgoing || !runtime.lanReceiverGraceScheduled.compareAndSet(false, true)) return
        workExecutor.execute {
            try {
                Thread.sleep(LAN_DISCOVERY_GRACE_MS)
            } catch (_: InterruptedException) {
                return@execute
            }
            if (runtime.disposed.get() || runtime.completed.get() || runtime.lanTransport != null) return@execute
            if (runtime.coordinator?.currentDataChannelState() == P2pDataChannelTransport.State.CONNECTED) return@execute
            failRuntime(
                runtime,
                "Neither direct LAN nor WebRTC could connect. Keep both phones on the same Wi-Fi and retry.",
                null,
                deleteDestination = true
            )
        }
    }

    private fun chooseWebRtcFallback(runtime: RuntimeTransfer, status: String) {
        if (!runtime.outgoing || runtime.disposed.get() || runtime.completed.get()) return
        if (runtime.lanPathDecisionMade.compareAndSet(false, true)) {
            runtime.lanDiscovery?.stopDiscovery()
            callbackStatus(runtime.offer.fileName, status)
            maybeStartSender(runtime)
        }
    }

    private fun runSender(runtime: RuntimeTransfer) {
        val sourceUri = runtime.sourceUri
        val key = runtime.key
        if (sourceUri == null || key == null) {
            failRuntime(runtime, "The outgoing file source or encryption key is missing.", null, false)
            return
        }
        try {
            val input: InputStream = appContext.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Android could not reopen the selected source file.")
            P2pEncryptedChunkStream.Sender(
                input,
                key,
                descriptorFor(runtime.offer)
            ).use { sender ->
                runtime.senderStream = sender
                callbackStatus(
                    runtime.offer.fileName,
                    "Sending encrypted file… ${P2pTransferFileMetadataReader.formatBytes(0)} / ${P2pTransferFileMetadataReader.formatBytes(runtime.offer.sizeBytes)}"
                )
                while (true) {
                    waitWhilePaused(runtime)
                    val frame = sender.nextFrame() ?: break
                    try {
                        sendFrameWithBackpressure(runtime, frame)
                    } finally {
                        frame.fill(0)
                    }
                    val bytes = sender.bytesProcessed
                    if (bytes - runtime.lastReportedBytes >= 1024L * 1024L) {
                        runtime.lastReportedBytes = bytes
                        callbackStatus(runtime.offer.fileName, "Sending encrypted file… ${P2pTransferFileMetadataReader.formatBytes(bytes)} / ${P2pTransferFileMetadataReader.formatBytes(runtime.offer.sizeBytes)}")
                    }
                }
                val endFrame = sender.finishFrame()
                try {
                    sendFrameWithBackpressure(runtime, endFrame)
                    sender.markEndFrameSent()
                } finally {
                    endFrame.fill(0)
                }
            }
            runtime.senderStream = null
            callbackStatus(runtime.offer.fileName, "All encrypted chunks sent. Waiting for receiver's integrity check…")
        } catch (error: Exception) {
            if (error !is InterruptedException && !runtime.disposed.get()) {
                failRuntime(runtime, "Encrypted file sending failed.", error, false)
            }
        }
    }

    private fun sendFrameWithBackpressure(runtime: RuntimeTransfer, frame: ByteArray) {
        var deadline = System.currentTimeMillis() + SEND_FRAME_TIMEOUT_MS
        while (true) {
            if (runtime.pauseRequested) {
                val pauseStartedAt = System.currentTimeMillis()
                waitWhilePaused(runtime)
                // An intentional user pause must not consume the channel-stall timeout.
                deadline += System.currentTimeMillis() - pauseStartedAt
            } else {
                waitWhilePaused(runtime)
            }
            val lanTransport = runtime.lanTransport
            if (runtime.outgoing && lanTransport != null) {
                if (lanTransport.sendEncryptedFrame(frame)) return
                throw IllegalStateException("Direct LAN connection was lost during transfer. Restart the file transfer.")
            }
            val coordinator = runtime.coordinator ?: throw IllegalStateException("WebRTC session closed.")
            if (coordinator.currentDataChannelState() == P2pDataChannelTransport.State.CONNECTED &&
                coordinator.sendEncryptedFrame(frame)
            ) return
            if (System.currentTimeMillis() >= deadline) throw IllegalStateException("File channel stalled while sending a frame.")
            Thread.sleep(SEND_RETRY_DELAY_MS)
        }
    }

    private fun waitWhilePaused(runtime: RuntimeTransfer) {
        while (runtime.pauseRequested) {
            if (runtime.disposed.get() || closed.get()) {
                throw InterruptedException("Transfer was cancelled.")
            }
            Thread.sleep(PAUSE_POLL_DELAY_MS)
        }
        if (runtime.disposed.get() || closed.get()) {
            throw InterruptedException("Transfer was cancelled.")
        }
    }

    private fun processIncomingFrame(runtime: RuntimeTransfer, frame: ByteArray) {
        try {
            if (runtime.disposed.get()) return
            val receiver = runtime.receiverStream
                ?: throw IllegalStateException("Incoming file destination is not ready.")
            receiver.acceptFrame(frame)
            if (receiver.isComplete) {
                receiver.finish()
                receiver.close()
                promoteVerifiedFile(runtime)
                if (runtime.completed.compareAndSet(false, true)) {
                    signaling.completeTransfer(runtime.offer.conversationId, runtime.offer.transferId) { error ->
                        if (error != null) {
                            runtime.completed.set(false)
                            failRuntime(runtime, "File was received, but completion could not be confirmed.", error, false)
                        } else {
                            callbackStatus(runtime.offer.fileName, "File received and SHA-256 verified.")
                            callbackComplete(runtime.offer.fileName, false)
                            disposeRuntime(runtime, deleteDestination = false)
                        }
                    }
                }
            } else {
                val received = receiver.bytesReceived
                if (received - runtime.lastReportedBytes >= 1024L * 1024L) {
                    runtime.lastReportedBytes = received
                    callbackStatus(runtime.offer.fileName, "Receiving encrypted file… ${P2pTransferFileMetadataReader.formatBytes(received)} / ${P2pTransferFileMetadataReader.formatBytes(runtime.offer.sizeBytes)}")
                }
            }
        } catch (error: Exception) {
            failRuntime(runtime, "Received file failed authentication or integrity validation.", error, true)
        } finally {
            frame.fill(0)
        }
    }

    private fun hashUri(uri: Uri, expectedSize: Long): String {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Android could not read the selected file.")
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
                    if (total > expectedSize) throw IllegalStateException("File changed while being prepared.")
                    digest.update(buffer, 0, count)
                }
            } finally {
                buffer.fill(0)
            }
        }
        if (total != expectedSize) throw IllegalStateException("File size changed while being prepared.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun descriptorFor(offer: P2pTransferOffer) = P2pEncryptedChunkStream.TransferDescriptor(
        transferId = offer.transferId,
        fileName = offer.fileName,
        mimeType = offer.mimeType,
        totalBytes = offer.sizeBytes,
        sha256Hex = offer.sha256,
        chunkSize = offer.chunkSize
    )

    private fun completeRuntime(runtime: RuntimeTransfer) {
        if (!runtime.completed.compareAndSet(false, true)) return
        callbackStatus(runtime.offer.fileName, "Receiver verified the completed file.")
        callbackComplete(runtime.offer.fileName, true)
        disposeRuntime(runtime, deleteDestination = false)
    }

    private fun failRuntime(
        runtime: RuntimeTransfer,
        message: String,
        error: Throwable?,
        deleteDestination: Boolean
    ) {
        if (runtime.completed.get() || !runtime.disposed.compareAndSet(false, true)) return
        runtimes.remove(runtime.offer.transferId, runtime)
        runtime.offerListener?.remove()
        runtime.offerListener = null
        runtime.coordinator?.close()
        runtime.coordinator = null
        runCatching { runtime.lanDiscovery?.close() }
        runtime.lanDiscovery = null
        runCatching { runtime.lanListener?.close() }
        runtime.lanListener = null
        runCatching { runtime.lanTransport?.close() }
        runtime.lanTransport = null
        runtime.lanCandidate = null
        runtime.lanPeer = null
        runCatching { runtime.senderStream?.close() }
        runCatching { runtime.receiverStream?.close() }
        runtime.receiveExecutor.shutdownNow()
        runtime.temporaryFile?.delete()
        runtime.temporaryFile = null
        if (deleteDestination) deletePartialDestination(runtime.destinationUri)
        signaling.failTransfer(runtime.offer.conversationId, runtime.offer.transferId) { }
        callbackError(runtime.offer.fileName, message, error)
    }

    private fun disposeRuntime(runtime: RuntimeTransfer, deleteDestination: Boolean) {
        if (!runtime.disposed.compareAndSet(false, true)) return
        runtimes.remove(runtime.offer.transferId, runtime)
        runtime.offerListener?.remove()
        runtime.offerListener = null
        runtime.coordinator?.close()
        runtime.coordinator = null
        runCatching { runtime.lanDiscovery?.close() }
        runtime.lanDiscovery = null
        runCatching { runtime.lanListener?.close() }
        runtime.lanListener = null
        runCatching { runtime.lanTransport?.close() }
        runtime.lanTransport = null
        runtime.lanCandidate = null
        runtime.lanPeer = null
        runCatching { runtime.senderStream?.close() }
        runCatching { runtime.receiverStream?.close() }
        runtime.receiveExecutor.shutdownNow()
        runtime.temporaryFile?.delete()
        runtime.temporaryFile = null
        if (deleteDestination) deletePartialDestination(runtime.destinationUri)
    }

    /** Copies from private cache only after the receiver has verified the authenticated end frame. */
    private fun promoteVerifiedFile(runtime: RuntimeTransfer) {
        val file = runtime.temporaryFile ?: throw IllegalStateException("Temporary download file is missing.")
        if (!file.isFile || file.length() != runtime.offer.sizeBytes) {
            throw IllegalStateException("Verified temporary file length does not match the offer.")
        }
        val destination = runtime.destinationUri
            ?: throw IllegalStateException("The chosen save destination is missing.")
        val input = FileInputStream(file)
        val output = appContext.contentResolver.openOutputStream(destination, "w")
            ?: throw IllegalStateException("Android could not open the chosen save destination.")
        input.use { source ->
            output.use { target ->
                source.copyTo(target, 64 * 1024)
                target.flush()
            }
        }
        if (file.length() != runtime.offer.sizeBytes) {
            throw IllegalStateException("Temporary download changed during promotion.")
        }
    }

    private fun deletePartialDestination(uri: Uri?) {
        if (uri == null) return
        runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, uri) }
    }

    private fun callbackStatus(fileName: String, detail: String) {
        if (closed.get()) return
        mainHandler.post { if (!closed.get()) callback.onStatus(fileName, detail) }
    }

    private fun callbackComplete(fileName: String, outgoing: Boolean) {
        if (closed.get()) return
        mainHandler.post { if (!closed.get()) callback.onCompleted(fileName, outgoing) }
    }

    private fun callbackError(fileName: String, message: String, error: Throwable?) {
        if (closed.get()) return
        mainHandler.post { if (!closed.get()) callback.onError(fileName, message, error) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runtimes.values.toList().forEach {
            disposeRuntime(it, deleteDestination = !it.outgoing && !it.completed.get())
        }
        workExecutor.shutdownNow()
    }

    private companion object {
        const val SEND_RETRY_DELAY_MS = 30L
        const val PAUSE_POLL_DELAY_MS = 50L
        const val SEND_FRAME_TIMEOUT_MS = 120_000L
        const val LAN_DISCOVERY_GRACE_MS = 5_000L
        const val LAN_CONNECT_TIMEOUT_MS = 3_000
        const val LAN_HANDSHAKE_TIMEOUT_MS = 3_000
    }
}
