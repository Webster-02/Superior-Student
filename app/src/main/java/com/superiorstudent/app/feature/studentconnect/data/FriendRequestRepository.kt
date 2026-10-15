package com.superiorstudent.app.feature.studentconnect.data

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class FriendRequest(
    val id: String,
    val senderUid: String,
    val receiverUid: String,
    val senderName: String,
    val senderPublicId: String,
    val receiverName: String,
    val receiverPublicId: String,
    val status: String
)

internal object FriendRequestStatus {
    const val PENDING = "pending"
    const val ACCEPTED = "accepted"
    const val REJECTED = "rejected"
    const val CANCELLED = "cancelled"

    fun documentId(senderUid: String, receiverUid: String): String =
        senderUid + "_" + receiverUid

    fun isKnown(value: String): Boolean =
        value == PENDING || value == ACCEPTED || value == REJECTED || value == CANCELLED

    fun canTransition(
        actorUid: String,
        senderUid: String,
        receiverUid: String,
        currentStatus: String,
        nextStatus: String
    ): Boolean = when {
        actorUid == receiverUid && currentStatus == PENDING &&
            (nextStatus == ACCEPTED || nextStatus == REJECTED) -> true
        actorUid == senderUid && currentStatus == PENDING &&
            nextStatus == CANCELLED -> true
        actorUid == senderUid &&
            (currentStatus == REJECTED || currentStatus == CANCELLED) &&
            nextStatus == PENDING -> true
        else -> false
    }
}

/** One directional request has a stable document ID to prevent duplicate sends. */
class FriendRequestRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun sendRequest(sender: StudentProfile, receiver: StudentProfile, callback: (String?, Exception?) -> Unit) {
        if (sender.uid.isBlank() || receiver.uid.isBlank() || sender.uid == receiver.uid) {
            callback(null, IllegalArgumentException("You cannot send a friend request to yourself."))
            return
        }
        if (!StudentPublicIdFormat.isValid(sender.publicId) ||
            !StudentPublicIdFormat.isValid(receiver.publicId) ||
            sender.displayName.trim().length !in 2..60 ||
            receiver.displayName.trim().length !in 2..60
        ) {
            callback(null, IllegalArgumentException("The student profile is incomplete. Refresh and try again."))
            return
        }

        val requestId = FriendRequestStatus.documentId(sender.uid, receiver.uid)
        val requestRef = firestore.collection(REQUESTS).document(requestId)

        firestore.runTransaction { transaction ->
            val existing = transaction.get(requestRef)
            if (!existing.exists()) {
                transaction.set(
                    requestRef,
                    mapOf(
                        "senderUid" to sender.uid,
                        "receiverUid" to receiver.uid,
                        "senderName" to sender.displayName,
                        "senderPublicId" to sender.publicId,
                        "receiverName" to receiver.displayName,
                        "receiverPublicId" to receiver.publicId,
                        "status" to FriendRequestStatus.PENDING,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                FriendRequestStatus.PENDING
            } else {
                if (existing.getString("senderUid") != sender.uid ||
                    existing.getString("receiverUid") != receiver.uid
                ) {
                    throw IllegalStateException("This friend request ID belongs to a different pair of students.")
                }
                when (existing.getString("status")) {
                    FriendRequestStatus.PENDING -> FriendRequestStatus.PENDING
                    FriendRequestStatus.ACCEPTED -> FriendRequestStatus.ACCEPTED
                    FriendRequestStatus.REJECTED, FriendRequestStatus.CANCELLED -> {
                        transaction.update(
                            requestRef,
                            mapOf(
                                "status" to FriendRequestStatus.PENDING,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        FriendRequestStatus.PENDING
                    }
                    else -> throw IllegalStateException("This friend request has an unknown status.")
                }
            }
        }.addOnSuccessListener { status -> callback(status, null) }
            .addOnFailureListener { error -> callback(null, error) }
    }

    fun loadRequests(uid: String, callback: (List<FriendRequest>?, Exception?) -> Unit) {
        if (uid.isBlank()) {
            callback(null, IllegalArgumentException("Sign in to load friend requests."))
            return
        }

        val found = ConcurrentHashMap<String, FriendRequest>()
        val failure = AtomicReference<Exception?>(null)
        val remaining = AtomicInteger(2)
        val finish: (Task<QuerySnapshot>) -> Unit = { task ->
            if (!task.isSuccessful) {
                failure.compareAndSet(null, task.exception ?: IllegalStateException("Could not load friend requests."))
            } else {
                for (document in task.result.documents) {
                    val request = document.toFriendRequest()
                    if (request == null || (request.senderUid != uid && request.receiverUid != uid)) {
                        failure.compareAndSet(null, IllegalStateException("A friend request record is incomplete."))
                        break
                    }
                    found[request.id] = request
                }
            }

            if (remaining.decrementAndGet() == 0) {
                val error = failure.get()
                callback(if (error == null) found.values.toList() else null, error)
            }
        }

        firestore.collection(REQUESTS).whereEqualTo("senderUid", uid).get()
            .addOnCompleteListener { finish(it) }
        firestore.collection(REQUESTS).whereEqualTo("receiverUid", uid).get()
            .addOnCompleteListener { finish(it) }
    }

    fun updateStatus(
        request: FriendRequest,
        actorUid: String,
        nextStatus: String,
        callback: (Exception?) -> Unit
    ) {
        if (!FriendRequestStatus.canTransition(
                actorUid, request.senderUid, request.receiverUid, request.status, nextStatus
            )
        ) {
            callback(IllegalArgumentException("That friend request action is not allowed. Refresh and try again."))
            return
        }
        firestore.collection(REQUESTS).document(request.id)
            .update(mapOf("status" to nextStatus, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { error -> callback(error) }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toFriendRequest(): FriendRequest? {
        val senderUid = getString("senderUid") ?: return null
        val receiverUid = getString("receiverUid") ?: return null
        val senderName = getString("senderName") ?: return null
        val senderPublicId = getString("senderPublicId") ?: return null
        val receiverName = getString("receiverName") ?: return null
        val receiverPublicId = getString("receiverPublicId") ?: return null
        val status = getString("status") ?: return null

        if (senderUid.isBlank() || receiverUid.isBlank() || senderUid == receiverUid ||
            senderName.isBlank() || receiverName.isBlank() ||
            !StudentPublicIdFormat.isValid(senderPublicId) ||
            !StudentPublicIdFormat.isValid(receiverPublicId) ||
            !FriendRequestStatus.isKnown(status) ||
            id != FriendRequestStatus.documentId(senderUid, receiverUid)
        ) return null

        return FriendRequest(
            id, senderUid, receiverUid, senderName, senderPublicId,
            receiverName, receiverPublicId, status
        )
    }

    private companion object {
        const val REQUESTS = "friendRequests"
    }
}
