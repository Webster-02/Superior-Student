package com.superiorstudent.app.feature.transfer.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Advertises and discovers temporary Student Connect P2P endpoints on the current LAN via mDNS.
 *
 * TXT records contain only a protocol version and an opaque per-transfer session ID; they must
 * never contain student names, UIDs, file names, file contents, or long-lived secrets.
 *
 * Discovery is NOT peer authentication. A caller must still authenticate the remote student
 * and authenticate the encrypted session before accepting a socket or file frame. This helper
 * does not open a TCP listener or initiate a file transfer by itself.
 */
class P2pLanDiscovery(
    context: Context,
    private val callback: Callback
) : AutoCloseable {

    interface Callback {
        fun onPeerDiscovered(peer: Peer)
        fun onPeerLost(serviceName: String)
        fun onAdvertised(serviceName: String)
        fun onError(operation: String, errorCode: Int)
    }

    data class Peer(
        val serviceName: String,
        val sessionId: String,
        val host: InetAddress,
        val port: Int,
        val protocolVersion: Int
    )

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val resolvingNames = ConcurrentHashMap.newKeySet<String>()
    private val discoveredPeers = ConcurrentHashMap<String, Peer>()

    @Volatile private var discoveryListener: NsdManager.DiscoveryListener? = null
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var registeredServiceName: String? = null
    @Volatile private var localSessionId: String? = null
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Announces the caller's already-open TCP listener. [port] must be the actual bound port.
     * This class deliberately does not create a listener on its own.
     */
    fun advertise(sessionId: String, port: Int, deviceLabel: String = "Student"): Boolean {
        if (closed.get()) return false
        if (!P2pLanDiscoveryProtocol.isValidSessionId(sessionId) || port !in 1..65535) {
            reportError("advertise", ERROR_INVALID_ARGUMENT)
            return false
        }
        val manager = nsdManager ?: run {
            reportError("advertise", ERROR_NSD_UNAVAILABLE)
            return false
        }
        if (registrationListener != null) {
            reportError("advertise", ERROR_ALREADY_RUNNING)
            return false
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = P2pLanDiscoveryProtocol.createServiceName(deviceLabel, sessionId)
            serviceType = P2pLanDiscoveryProtocol.SERVICE_TYPE
            this.port = port
            setAttribute(
                P2pLanDiscoveryProtocol.ATTRIBUTE_VERSION,
                P2pLanDiscoveryProtocol.PROTOCOL_VERSION.toString().toByteArray(Charsets.US_ASCII)
            )
            setAttribute(
                P2pLanDiscoveryProtocol.ATTRIBUTE_SESSION_ID,
                sessionId.toByteArray(Charsets.US_ASCII)
            )
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                if (closed.get()) return
                registeredServiceName = serviceInfo.serviceName
                mainHandler.post {
                    if (!closed.get()) callback.onAdvertised(serviceInfo.serviceName)
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                registrationListener = null
                localSessionId = null
                reportError("advertise", errorCode)
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                if (registeredServiceName == serviceInfo.serviceName) registeredServiceName = null
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                reportError("stop-advertise", errorCode)
            }
        }

        localSessionId = sessionId
        registrationListener = listener
        return try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            true
        } catch (error: RuntimeException) {
            registrationListener = null
            localSessionId = null
            reportError("advertise", ERROR_NSD_CALL_FAILED)
            false
        }
    }

    /** Begins discovering other active endpoints of this app on the current local network. */
    fun startDiscovery(): Boolean {
        if (closed.get()) return false
        val manager = nsdManager ?: run {
            reportError("discover", ERROR_NSD_UNAVAILABLE)
            return false
        }
        if (discoveryListener != null) {
            reportError("discover", ERROR_ALREADY_RUNNING)
            return false
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (closed.get() ||
                    !P2pLanDiscoveryProtocol.isOurServiceType(serviceInfo.serviceType) ||
                    serviceInfo.serviceName == registeredServiceName
                ) return
                val name = serviceInfo.serviceName
                if (!resolvingNames.add(name)) return

                try {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            resolvingNames.remove(info.serviceName)
                            if (!closed.get() && errorCode != NsdManager.FAILURE_ALREADY_ACTIVE) {
                                reportError("resolve-peer", errorCode)
                            }
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            resolvingNames.remove(info.serviceName)
                            if (closed.get() || info.serviceName == registeredServiceName) return
                            val peer = parsePeer(info) ?: return
                            if (peer.sessionId == localSessionId) return
                            discoveredPeers[peer.serviceName] = peer
                            mainHandler.post {
                                if (!closed.get()) callback.onPeerDiscovered(peer)
                            }
                        }
                    })
                } catch (error: RuntimeException) {
                    resolvingNames.remove(name)
                    reportError("resolve-peer", ERROR_NSD_CALL_FAILED)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName
                resolvingNames.remove(name)
                val removed = discoveredPeers.remove(name)
                if (removed != null) {
                    mainHandler.post {
                        if (!closed.get()) callback.onPeerLost(name)
                    }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                releaseMulticastLock()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
                releaseMulticastLock()
                reportError("discover", errorCode)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                reportError("stop-discover", errorCode)
            }
        }

        discoveryListener = listener
        return try {
            acquireMulticastLock()
            manager.discoverServices(
                P2pLanDiscoveryProtocol.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
            true
        } catch (error: RuntimeException) {
            discoveryListener = null
            releaseMulticastLock()
            reportError("discover", ERROR_NSD_CALL_FAILED)
            false
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        val lock = wifiManager.createMulticastLock("SuperiorStudentP2pDiscovery")
        lock.setReferenceCounted(false)
        lock.acquire()
        multicastLock = lock
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock
        multicastLock = null
        if (lock?.isHeld == true) runCatching { lock.release() }
    }

    fun currentPeers(): List<Peer> = discoveredPeers.values
        .sortedBy { it.serviceName.lowercase() }

    fun stopDiscovery() {
        val manager = nsdManager
        val listener = discoveryListener
        discoveryListener = null
        resolvingNames.clear()
        discoveredPeers.clear()
        if (manager != null && listener != null) {
            try {
                manager.stopServiceDiscovery(listener)
            } catch (_: RuntimeException) {
                // The platform may already have stopped discovery after an asynchronous failure.
            }
        }
        releaseMulticastLock()
    }

    fun stopAdvertising() {
        val manager = nsdManager
        val listener = registrationListener
        registrationListener = null
        if (manager != null && listener != null) {
            try {
                manager.unregisterService(listener)
            } catch (_: RuntimeException) {
                // Registration may already have been removed by the system.
            }
        }
        registeredServiceName = null
        localSessionId = null
    }

    private fun parsePeer(info: NsdServiceInfo): Peer? {
        if (!P2pLanDiscoveryProtocol.isOurServiceType(info.serviceType)) return null
        val attributes = info.attributes
        val version = attributes[P2pLanDiscoveryProtocol.ATTRIBUTE_VERSION]
            ?.toString(Charsets.US_ASCII)
            ?.toIntOrNull()
            ?: return null
        if (version != P2pLanDiscoveryProtocol.PROTOCOL_VERSION) return null

        val sessionId = attributes[P2pLanDiscoveryProtocol.ATTRIBUTE_SESSION_ID]
            ?.toString(Charsets.US_ASCII)
            ?: return null
        if (!P2pLanDiscoveryProtocol.isValidSessionId(sessionId)) return null
        if (info.port !in 1..65535) return null
        val host = info.host ?: return null
        if (host.isAnyLocalAddress || host.isMulticastAddress) return null
        if (host.isLoopbackAddress) return null

        return Peer(
            serviceName = info.serviceName,
            sessionId = sessionId,
            host = host,
            port = info.port,
            protocolVersion = version
        )
    }

    private fun reportError(operation: String, errorCode: Int) {
        if (closed.get()) return
        mainHandler.post {
            if (!closed.get()) callback.onError(operation, errorCode)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stopDiscovery()
        stopAdvertising()
        resolvingNames.clear()
        discoveredPeers.clear()
    }

    companion object {
        const val ERROR_NSD_UNAVAILABLE = -10_001
        const val ERROR_ALREADY_RUNNING = -10_002
        const val ERROR_INVALID_ARGUMENT = -10_003
        const val ERROR_NSD_CALL_FAILED = -10_004
    }
}

/** Pure protocol helpers kept separate so validation can be tested on the JVM. */
object P2pLanDiscoveryProtocol {
    const val SERVICE_TYPE = "_superiorstudentp2p._tcp."
    const val PROTOCOL_VERSION = 1
    const val ATTRIBUTE_VERSION = "v"
    const val ATTRIBUTE_SESSION_ID = "sid"

    private val sessionIdPattern = Regex("^[A-Za-z0-9_-]{16,80}$")
    private val serviceLabelPattern = Regex("[^A-Za-z0-9_-]+")

    fun isValidSessionId(sessionId: String): Boolean =
        sessionIdPattern.matches(sessionId)

    fun isOurServiceType(serviceType: String?): Boolean {
        val normalized = serviceType?.trim()?.removeSuffix(".")?.lowercase()
        return normalized == SERVICE_TYPE.removeSuffix(".")
    }

    /**
     * DNS-SD instance names are limited to 63 characters. A random session suffix prevents
     * same-name collisions without exposing a student identifier.
     */
    fun createServiceName(deviceLabel: String, sessionId: String): String {
        require(isValidSessionId(sessionId)) { "Invalid LAN session ID." }
        val safeLabel = serviceLabelPattern.replace(deviceLabel.trim(), "-")
            .trim('-', '_')
            .take(32)
            .ifBlank { "Student" }
        return (safeLabel + "-" + sessionId.takeLast(8)).take(63)
    }
}
