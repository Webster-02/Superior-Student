package com.superiorstudent.app.feature.studentconnect.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.WriteBatch

data class StudentNotification(
    val id: String,
    val recipientUid: String,
    val actorUid: String,
    val actorName: String,
    val type: String,
    val requestId: String,
    val title: String,
    val body: String,
    val eventAt: Timestamp,
    val createdAt: Timestamp?,
    val isRead: Boolean
)

internal object StudentNotificationIds {
    fun requestReceived(requestId: String): String = requestId + "_request"
    fun requestAccepted(requestId: String): String = requestId + "_accepted"
}

/** Spark-compatible inbox. Firestore rules validate the related friend-request state. */
class StudentNotificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun createRequestReceived(request: FriendRequest, eventAt: Timestamp, callback: (Exception?) -> Unit) {
        writeNotification(
            StudentNotificationIds.requestReceived(request.id), request.receiverUid,
            request.senderUid, request.senderName, TYPE_FRIEND_REQUEST, request,
            eventAt, "New friend request", request.senderName + " sent you a friend request.", callback
        )
    }

    fun createRequestAccepted(request: FriendRequest, eventAt: Timestamp, callback: (Exception?) -> Unit) {
        writeNotification(
            StudentNotificationIds.requestAccepted(request.id), request.senderUid,
            request.receiverUid, request.receiverName, TYPE_FRIEND_REQUEST_ACCEPTED, request,
            eventAt, "Friend request accepted",
            request.receiverName + " accepted your friend request.", callback
        )
    }

    private fun writeNotification(
        notificationId: String,
        recipientUid: String,
        actorUid: String,
        actorName: String,
        type: String,
        request: FriendRequest,
        eventAt: Timestamp,
        title: String,
        body: String,
        callback: (Exception?) -> Unit
    ) {
        if (notificationId.isBlank() || recipientUid.isBlank() || actorUid.isBlank() ||
            actorName.trim().length !in 2..60 || request.id.isBlank()
        ) {
            callback(IllegalArgumentException("The notification details are incomplete."))
            return
        }

        val data = mapOf<String, Any?>(
            "recipientUid" to recipientUid,
            "actorUid" to actorUid,
            "actorName" to actorName,
            "type" to type,
            "requestId" to request.id,
            "title" to title,
            "body" to body,
            "eventAt" to eventAt,
            "createdAt" to FieldValue.serverTimestamp(),
            "read" to false,
            "readAt" to null
        )
        firestore.collection(NOTIFICATIONS).document(recipientUid).collection(ITEMS)
            .document(notificationId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it) }
    }

    fun listen(uid: String, callback: (List<StudentNotification>?, Exception?) -> Unit): ListenerRegistration {
        return firestore.collection(NOTIFICATIONS).document(uid).collection(ITEMS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_NOTIFICATIONS.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    callback(null, error)
                    return@addSnapshotListener
                }
                val notifications = snapshot?.documents.orEmpty().mapNotNull { document ->
                    val recipientUid = document.getString("recipientUid") ?: return@mapNotNull null
                    val actorUid = document.getString("actorUid") ?: return@mapNotNull null
                    val actorName = document.getString("actorName") ?: return@mapNotNull null
                    val type = document.getString("type") ?: return@mapNotNull null
                    val requestId = document.getString("requestId") ?: return@mapNotNull null
                    val title = document.getString("title") ?: return@mapNotNull null
                    val body = document.getString("body") ?: return@mapNotNull null
                    val eventAt = document.getTimestamp("eventAt") ?: return@mapNotNull null
                    val isRead = document.getBoolean("read") ?: return@mapNotNull null
                    if (recipientUid != uid || actorUid.isBlank() || actorName.isBlank() ||
                        requestId.isBlank() ||
                        (type != TYPE_FRIEND_REQUEST && type != TYPE_FRIEND_REQUEST_ACCEPTED)
                    ) return@mapNotNull null
                    StudentNotification(
                        document.id, recipientUid, actorUid, actorName, type, requestId,
                        title, body, eventAt, document.getTimestamp("createdAt"), isRead
                    )
                }
                callback(notifications, null)
            }
    }

    fun markAsRead(uid: String, notificationId: String, callback: (Exception?) -> Unit) {
        if (uid.isBlank() || notificationId.isBlank()) {
            callback(IllegalArgumentException("The notification could not be identified."))
            return
        }
        firestore.collection(NOTIFICATIONS).document(uid).collection(ITEMS).document(notificationId)
            .update(mapOf("read" to true, "readAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it) }
    }

    fun markAllAsRead(uid: String, notifications: List<StudentNotification>, callback: (Exception?) -> Unit) {
        if (uid.isBlank()) {
            callback(IllegalArgumentException("Sign in to manage notifications."))
            return
        }
        val unread = notifications.filterNot { it.isRead }
        if (unread.isEmpty()) {
            callback(null)
            return
        }
        val batch: WriteBatch = firestore.batch()
        unread.forEach { item ->
            val ref = firestore.collection(NOTIFICATIONS).document(uid).collection(ITEMS).document(item.id)
            batch.update(ref, mapOf("read" to true, "readAt" to FieldValue.serverTimestamp()))
        }
        batch.commit().addOnSuccessListener { callback(null) }.addOnFailureListener { callback(it) }
    }

    private companion object {
        const val NOTIFICATIONS = "studentNotifications"
        const val ITEMS = "items"
        const val TYPE_FRIEND_REQUEST = "friend_request"
        const val TYPE_FRIEND_REQUEST_ACCEPTED = "friend_request_accepted"
        const val MAX_NOTIFICATIONS = 25
    }
}
