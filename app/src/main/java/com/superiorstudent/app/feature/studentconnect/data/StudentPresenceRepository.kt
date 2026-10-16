package com.superiorstudent.app.feature.studentconnect.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class StudentPresence(
    val uid: String,
    val isOnline: Boolean,
    val lastSeenAt: Timestamp?,
    val updatedAt: Timestamp?
)

internal object StudentPresenceFormatter {
    const val ONLINE_STALE_AFTER_MS = 75_000L

    fun label(presence: StudentPresence?, nowMillis: Long): String {
        if (presence == null) return "Offline"
        val updatedAt = presence.updatedAt?.toDate()?.time
        val age = updatedAt?.let { nowMillis - it }
        if (presence.isOnline && age != null && age in -5_000L..ONLINE_STALE_AFTER_MS) return "Online"
        val lastSeenAt = presence.lastSeenAt?.toDate()?.time ?: return "Offline"
        val elapsed = (nowMillis - lastSeenAt).coerceAtLeast(0L)
        return when {
            elapsed < 60_000L -> "Last seen just now"
            elapsed < 3_600_000L -> "Last seen ${elapsed / 60_000L}m ago"
            elapsed < 86_400_000L -> "Last seen ${elapsed / 3_600_000L}h ago"
            else -> "Last seen ${elapsed / 86_400_000L}d ago"
        }
    }
}

/** Shares one presence heartbeat across Student Connect and private chat screens. */
object StudentConnectPresenceTracker {
    private const val TAG = "StudentConnectPresence"
    private const val HEARTBEAT_INTERVAL_MS = 30_000L
    private const val OFFLINE_GRACE_MS = 1_500L
    private val handler = Handler(Looper.getMainLooper())
    private val repository by lazy { StudentPresenceRepository() }
    private val activeOwners = mutableMapOf<String, String>()
    private val pendingOffline = mutableMapOf<String, Runnable>()

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            activeOwners.values.toSet().forEach { uid -> writePresence(uid, true) }
            if (activeOwners.isNotEmpty()) handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    fun start(ownerKey: String, uid: String) {
        if (ownerKey.isBlank() || uid.isBlank()) return
        val alreadyActive = activeOwners.values.any { it == uid }
        activeOwners[ownerKey] = uid
        pendingOffline.remove(uid)?.let(handler::removeCallbacks)
        if (!alreadyActive) writePresence(uid, true)
        handler.removeCallbacks(heartbeatRunnable)
        if (activeOwners.isNotEmpty()) handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    fun stop(ownerKey: String, immediate: Boolean = false) {
        val uid = activeOwners.remove(ownerKey) ?: return
        if (activeOwners.values.any { it == uid }) {
            rescheduleHeartbeat()
            return
        }
        pendingOffline.remove(uid)?.let(handler::removeCallbacks)
        val offlineRunnable = Runnable {
            pendingOffline.remove(uid)
            if (activeOwners.values.none { it == uid }) writePresence(uid, false)
            rescheduleHeartbeat()
        }
        pendingOffline[uid] = offlineRunnable
        handler.postDelayed(offlineRunnable, if (immediate) 0L else OFFLINE_GRACE_MS)
        rescheduleHeartbeat()
    }

    private fun rescheduleHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable)
        if (activeOwners.isNotEmpty()) handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    private fun writePresence(uid: String, isOnline: Boolean) {
        repository.updatePresence(uid, isOnline) { error ->
            if (error != null) Log.w(TAG, "Presence heartbeat could not be saved.", error)
        }
    }
}

class StudentPresenceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun updatePresence(uid: String, isOnline: Boolean, callback: (Exception?) -> Unit = {}) {
        if (uid.isBlank()) {
            callback(IllegalArgumentException("A student account is required for presence."))
            return
        }
        firestore.collection(PRESENCE_COLLECTION).document(uid)
            .set(mapOf(
                "uid" to uid,
                "isOnline" to isOnline,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { error -> callback(error) }
    }

    fun listenPresence(
        currentUid: String,
        targetUid: String,
        callback: (StudentPresence?, Exception?) -> Unit
    ): ListenerRegistration? {
        if (currentUid.isBlank() || targetUid.isBlank() || currentUid == targetUid) {
            callback(null, IllegalArgumentException("Two different student accounts are required."))
            return null
        }
        return firestore.collection(PRESENCE_COLLECTION).document(targetUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    callback(null, error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    callback(null, IllegalStateException("The student's presence status was not returned."))
                    return@addSnapshotListener
                }
                if (!snapshot.exists()) {
                    callback(null, null)
                    return@addSnapshotListener
                }
                val uid = snapshot.getString("uid")
                val isOnline = snapshot.getBoolean("isOnline")
                if (uid != targetUid || isOnline == null) {
                    callback(null, IllegalStateException("The student's presence document is invalid."))
                    return@addSnapshotListener
                }
                callback(
                    StudentPresence(uid, isOnline, snapshot.getTimestamp("lastSeenAt"), snapshot.getTimestamp("updatedAt")),
                    null
                )
            }
    }

    companion object {
        private const val PRESENCE_COLLECTION = "studentPresence"
    }
}
