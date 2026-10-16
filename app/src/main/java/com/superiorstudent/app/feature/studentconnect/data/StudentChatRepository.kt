package com.superiorstudent.app.feature.studentconnect.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

data class StudentChatConversation(
    val id: String,
    val participantUids: List<String>,
    val createdAt: Timestamp?,
    val updatedAt: Timestamp?
)

data class StudentChatMessage(
    val id: String,
    val senderUid: String,
    val recipientUid: String,
    val senderName: String,
    val text: String,
    val createdAt: Timestamp?
)

/** Stable IDs and message text validation shared by chat data code and unit tests. */
internal object StudentChatIds {
    fun conversationId(firstUid: String, secondUid: String): String {
        require(firstUid.isNotBlank() && secondUid.isNotBlank()) {
            "Both student accounts are required."
        }
        require(firstUid != secondUid) { "You cannot start a chat with yourself." }
        return listOf(firstUid, secondUid).sorted().joinToString("_")
    }

    fun participantUids(firstUid: String, secondUid: String): List<String> =
        listOf(firstUid, secondUid).sorted()
}

internal object StudentChatMessageText {
    const val MAX_LENGTH = 2000

    fun normalize(value: String): String = value.trim()

    fun isValid(value: String): Boolean = normalize(value).length in 1..MAX_LENGTH
}

/**
 * Spark-compatible client repository. Firestore rules, not client-side checks alone,
 * authorize conversation access and every message read/write.
 */
class StudentChatRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun openOrCreateConversation(
        currentUid: String,
        otherUid: String,
        callback: (StudentChatConversation?, Exception?) -> Unit
    ) {
        val conversationId = try {
            StudentChatIds.conversationId(currentUid, otherUid)
        } catch (error: IllegalArgumentException) {
            callback(null, error)
            return
        }

        findAcceptedConnection(currentUid, otherUid) { accepted, connectionError ->
            if (connectionError != null) {
                callback(null, connectionError)
                return@findAcceptedConnection
            }
            if (!accepted) {
                callback(null, IllegalStateException("Chat is available only for an accepted connection."))
                return@findAcceptedConnection
            }

            val conversationRef = firestore.collection(CONVERSATIONS).document(conversationId)
            conversationRef.get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val conversation = snapshot.toStudentChatConversation()
                        if (conversation == null ||
                            conversation.participantUids != StudentChatIds.participantUids(currentUid, otherUid)
                        ) {
                            callback(null, IllegalStateException("The saved conversation is invalid."))
                        } else {
                            callback(conversation, null)
                        }
                        return@addOnSuccessListener
                    }

                    val participants = StudentChatIds.participantUids(currentUid, otherUid)
                    val now = FieldValue.serverTimestamp()
                    conversationRef.set(
                        mapOf(
                            "participantUids" to participants,
                            "createdAt" to now,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).addOnSuccessListener {
                        conversationRef.get()
                            .addOnSuccessListener { created ->
                                val conversation = created.toStudentChatConversation()
                                if (conversation == null) {
                                    callback(null, IllegalStateException("The conversation was saved but could not be read back. Retry once."))
                                } else {
                                    callback(conversation, null)
                                }
                            }
                            .addOnFailureListener { error -> callback(null, error) }
                    }.addOnFailureListener { error -> callback(null, error) }
                }
                .addOnFailureListener { error -> callback(null, error) }
        }
    }

    fun sendMessage(
        conversationId: String,
        currentUid: String,
        otherUid: String,
        senderName: String,
        rawText: String,
        callback: (Exception?) -> Unit
    ) {
        val expectedId = try {
            StudentChatIds.conversationId(currentUid, otherUid)
        } catch (error: IllegalArgumentException) {
            callback(error)
            return
        }
        val text = StudentChatMessageText.normalize(rawText)
        val normalizedName = senderName.trim().replace(WHITESPACE, " ")

        if (conversationId != expectedId) {
            callback(IllegalArgumentException("The conversation does not match these two students."))
            return
        }
        if (normalizedName.length !in 2..60) {
            callback(IllegalArgumentException("The sender profile name is invalid."))
            return
        }
        if (!StudentChatMessageText.isValid(text)) {
            callback(IllegalArgumentException("Messages must contain 1 to 2000 characters."))
            return
        }

        val conversationRef = firestore.collection(CONVERSATIONS).document(conversationId)
        val messageRef = conversationRef.collection(MESSAGES).document()
        val batch = firestore.batch()
        batch.set(
            messageRef,
            mapOf(
                "participantUids" to StudentChatIds.participantUids(currentUid, otherUid),
                "senderUid" to currentUid,
                "recipientUid" to otherUid,
                "senderName" to normalizedName,
                "text" to text,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(conversationRef, "updatedAt", FieldValue.serverTimestamp())
        batch.commit()
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { error -> callback(error) }
    }

    fun listenMessages(
        conversationId: String,
        currentUid: String,
        otherUid: String,
        callback: (List<StudentChatMessage>?, Exception?) -> Unit
    ): ListenerRegistration? {
        val expectedId = try {
            StudentChatIds.conversationId(currentUid, otherUid)
        } catch (error: IllegalArgumentException) {
            callback(null, error)
            return null
        }
        if (conversationId != expectedId) {
            callback(null, IllegalArgumentException("The conversation does not match these two students."))
            return null
        }

        return firestore.collection(CONVERSATIONS)
            .document(conversationId)
            .collection(MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(MAX_MESSAGES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    callback(null, error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    callback(null, IllegalStateException("No chat messages were returned."))
                    return@addSnapshotListener
                }

                val messages = snapshot.documents.mapNotNull { it.toStudentChatMessage() }
                if (messages.size != snapshot.documents.size) {
                    callback(null, IllegalStateException("A chat message record is incomplete."))
                } else {
                    callback(messages, null)
                }
            }
    }

    private fun findAcceptedConnection(
        currentUid: String,
        otherUid: String,
        callback: (Boolean, Exception?) -> Unit
    ) {
        val requests = firestore.collection(FRIEND_REQUESTS)
        requests.whereEqualTo("senderUid", currentUid)
            .whereEqualTo("receiverUid", otherUid)
            .get()
            .addOnSuccessListener { forward ->
                if (forward.documents.any { it.getString("status") == ACCEPTED }) {
                    callback(true, null)
                } else {
                    requests.whereEqualTo("senderUid", otherUid)
                        .whereEqualTo("receiverUid", currentUid)
                        .get()
                        .addOnSuccessListener { reverse ->
                            callback(reverse.documents.any { it.getString("status") == ACCEPTED }, null)
                        }
                        .addOnFailureListener { error -> callback(false, error) }
                }
            }
            .addOnFailureListener { error -> callback(false, error) }
    }

    private fun DocumentSnapshot.toStudentChatConversation(): StudentChatConversation? {
        val participants = get("participantUids") as? List<*>
        if (participants == null || participants.size != 2 ||
            participants.any { it !is String || it.isBlank() } ||
            participants[0] == participants[1] ||
            id != runCatching { StudentChatIds.conversationId(participants[0] as String, participants[1] as String) }.getOrNull()
        ) return null
        return StudentChatConversation(
            id = id,
            participantUids = participants.filterIsInstance<String>(),
            createdAt = getTimestamp("createdAt"),
            updatedAt = getTimestamp("updatedAt")
        )
    }

    private fun DocumentSnapshot.toStudentChatMessage(): StudentChatMessage? {
        val senderUid = getString("senderUid") ?: return null
        val recipientUid = getString("recipientUid") ?: return null
        val senderName = getString("senderName") ?: return null
        val text = getString("text") ?: return null
        if (senderUid.isBlank() || recipientUid.isBlank() || senderUid == recipientUid ||
            senderName.trim().length !in 2..60 || !StudentChatMessageText.isValid(text)
        ) return null
        return StudentChatMessage(
            id = id,
            senderUid = senderUid,
            recipientUid = recipientUid,
            senderName = senderName,
            text = text,
            createdAt = getTimestamp("createdAt")
        )
    }

    private companion object {
        const val CONVERSATIONS = "chatConversations"
        const val MESSAGES = "messages"
        const val FRIEND_REQUESTS = "friendRequests"
        const val ACCEPTED = "accepted"
        const val MAX_MESSAGES = 100L
        val WHITESPACE = Regex("\\s+")
    }
}
