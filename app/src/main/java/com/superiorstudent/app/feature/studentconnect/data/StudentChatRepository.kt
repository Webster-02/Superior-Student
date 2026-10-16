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

data class StudentChatSummary(
    val conversationId: String,
    val otherUid: String,
    val otherDisplayName: String,
    val lastMessageText: String?,
    val lastMessageSenderUid: String?,
    val lastMessageAt: Timestamp?,
    val updatedAt: Timestamp?,
    val unreadCount: Int
)

/** Ephemeral typing state, stored separately from immutable chat messages. */
data class StudentChatTypingStatus(val uid: String, val updatedAt: Timestamp)

/** Derives an unread count from recent messages and the user's last-read timestamp. */
internal object StudentChatUnreadCounter {
    fun count(
        messages: List<StudentChatMessage>,
        currentUid: String,
        lastReadAt: Timestamp?
    ): Int = messages.count { message ->
        message.senderUid != currentUid &&
            message.recipientUid == currentUid &&
            (lastReadAt == null ||
                message.createdAt?.toDate()?.after(lastReadAt.toDate()) == true)
    }
}

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
                    conversationRef.set(
                        mapOf(
                            "participantUids" to participants,
                            "createdAt" to FieldValue.serverTimestamp(),
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

    fun markConversationRead(
        conversationId: String,
        currentUid: String,
        callback: (Exception?) -> Unit
    ) {
        if (currentUid.isBlank() || conversationId.isBlank()) {
            callback(IllegalArgumentException("The chat and student account are required."))
            return
        }
        firestore.collection(CONVERSATIONS)
            .document(conversationId)
            .collection(READ_STATES)
            .document(currentUid)
            .set(
                mapOf(
                    "uid" to currentUid,
                    "lastReadAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { error -> callback(error) }
    }

    /**
     * Listens for conversation changes and each conversation's read marker.
     * Message documents remain immutable; each participant owns only their read marker.
     */
    fun listenConversationSummaries(
        currentUid: String,
        callback: (List<StudentChatSummary>?, Exception?) -> Unit
    ): ListenerRegistration? {
        if (currentUid.isBlank()) {
            callback(null, IllegalArgumentException("Sign in to load recent chats."))
            return null
        }

        val summaries = mutableMapOf<String, StudentChatSummary>()
        val readStateListeners = mutableMapOf<String, ListenerRegistration>()
        val refreshGenerations = mutableMapOf<String, Int>()
        var visibleConversationIds: Set<String> = emptySet()
        var stopped = false

        fun publishSummaries() {
            if (stopped) return
            val ordered = summaries.values.sortedByDescending {
                (it.lastMessageAt ?: it.updatedAt)?.toDate()?.time ?: 0L
            }
            callback(ordered, null)
        }

        fun refreshSummary(conversation: StudentChatConversation) {
            if (stopped || conversation.id !in visibleConversationIds) return
            val generation = (refreshGenerations[conversation.id] ?: 0) + 1
            refreshGenerations[conversation.id] = generation
            loadChatSummary(conversation, currentUid) { summary, error ->
                if (stopped || conversation.id !in visibleConversationIds ||
                    refreshGenerations[conversation.id] != generation
                ) return@loadChatSummary

                if (error != null || summary == null) {
                    summaries.remove(conversation.id)
                } else {
                    summaries[conversation.id] = summary
                }
                publishSummaries()
            }
        }

        val conversationsListener = firestore.collection(CONVERSATIONS)
            .whereArrayContains("participantUids", currentUid)
            .limit(MAX_CONVERSATIONS)
            .addSnapshotListener { snapshot, error ->
                if (stopped) return@addSnapshotListener
                if (error != null || snapshot == null) {
                    callback(
                        null,
                        error ?: IllegalStateException("Could not load recent chats.")
                    )
                    return@addSnapshotListener
                }

                val conversations = snapshot.documents.mapNotNull { document ->
                    document.toStudentChatConversation()
                        ?.takeIf { currentUid in it.participantUids }
                }
                visibleConversationIds = conversations.map { it.id }.toSet()

                readStateListeners.keys.filter { it !in visibleConversationIds }.toList().forEach { id ->
                    readStateListeners.remove(id)?.remove()
                    summaries.remove(id)
                    refreshGenerations.remove(id)
                }

                conversations.forEach { conversation ->
                    if (readStateListeners.containsKey(conversation.id)) {
                        refreshSummary(conversation)
                    } else {
                        val listener = firestore.collection(CONVERSATIONS)
                            .document(conversation.id)
                            .collection(READ_STATES)
                            .document(currentUid)
                            .addSnapshotListener { _, readError ->
                                if (stopped || conversation.id !in visibleConversationIds) {
                                    return@addSnapshotListener
                                }
                                if (readError != null) {
                                    summaries.remove(conversation.id)
                                    publishSummaries()
                                } else {
                                    refreshSummary(conversation)
                                }
                            }
                        readStateListeners[conversation.id] = listener
                    }
                }

                publishSummaries()
            }

        return object : ListenerRegistration {
            override fun remove() {
                stopped = true
                conversationsListener.remove()
                readStateListeners.values.forEach { it.remove() }
                readStateListeners.clear()
                summaries.clear()
                refreshGenerations.clear()
                visibleConversationIds = emptySet()
            }
        }
    }

    private fun loadChatSummary(
        conversation: StudentChatConversation,
        currentUid: String,
        callback: (StudentChatSummary?, Exception?) -> Unit
    ) {
        val otherUid = conversation.participantUids.firstOrNull { it != currentUid }
        if (otherUid.isNullOrBlank()) {
            callback(null, IllegalStateException("The conversation has no other participant."))
            return
        }

        firestore.collection(PROFILES).document(otherUid).get()
            .addOnSuccessListener { profileSnapshot ->
                val displayName = profileSnapshot.getString("displayName")
                    ?.trim()
                    ?.takeIf { it.length in 2..60 }
                if (displayName.isNullOrBlank()) {
                    callback(null, IllegalStateException("The other student's profile is unavailable."))
                    return@addOnSuccessListener
                }

                firestore.collection(CONVERSATIONS)
                    .document(conversation.id)
                    .collection(MESSAGES)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(MAX_MESSAGES)
                    .get()
                    .addOnSuccessListener { messagesSnapshot ->
                        val messages = messagesSnapshot.documents.mapNotNull { it.toStudentChatMessage() }
                        if (messages.size != messagesSnapshot.documents.size) {
                            callback(null, IllegalStateException("A chat message record is incomplete."))
                            return@addOnSuccessListener
                        }

                        firestore.collection(CONVERSATIONS)
                            .document(conversation.id)
                            .collection(READ_STATES)
                            .document(currentUid)
                            .get()
                            .addOnSuccessListener { readStateSnapshot ->
                                val lastReadAt = readStateSnapshot.getTimestamp("lastReadAt")
                                val latest = messages.firstOrNull()
                                callback(
                                    StudentChatSummary(
                                        conversationId = conversation.id,
                                        otherUid = otherUid,
                                        otherDisplayName = displayName,
                                        lastMessageText = latest?.text,
                                        lastMessageSenderUid = latest?.senderUid,
                                        lastMessageAt = latest?.createdAt,
                                        updatedAt = conversation.updatedAt,
                                        unreadCount = StudentChatUnreadCounter.count(messages, currentUid, lastReadAt)
                                    ),
                                    null
                                )
                            }
                            .addOnFailureListener { error -> callback(null, error) }
                    }
                    .addOnFailureListener { error -> callback(null, error) }
            }
            .addOnFailureListener { error -> callback(null, error) }
    }

    /** Publishes a short-lived typing heartbeat; Firestore rules enforce active-chat access. */
    fun publishTypingState(conversationId: String, currentUid: String, otherUid: String, callback: (Exception?) -> Unit) {
        val expectedId = try { StudentChatIds.conversationId(currentUid, otherUid) }
        catch (error: IllegalArgumentException) { callback(error); return }
        if (conversationId != expectedId) {
            callback(IllegalArgumentException("The conversation does not match these two students."))
            return
        }
        firestore.collection(CONVERSATIONS).document(conversationId).collection(TYPING).document(currentUid)
            .set(
                mapOf("uid" to currentUid, "isTyping" to true, "updatedAt" to FieldValue.serverTimestamp()),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { error -> callback(error) }
    }

    /** Removes this student's ephemeral typing state on idle, send, or close. */
    fun clearTypingState(
        conversationId: String,
        currentUid: String,
        otherUid: String,
        callback: (Exception?) -> Unit = {}
    ) {
        val expectedId = try { StudentChatIds.conversationId(currentUid, otherUid) }
        catch (error: IllegalArgumentException) { callback(error); return }
        if (conversationId != expectedId) {
            callback(IllegalArgumentException("The conversation does not match these two students."))
            return
        }
        firestore.collection(CONVERSATIONS).document(conversationId).collection(TYPING).document(currentUid)
            .delete()
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { error -> callback(error) }
    }

    /** Listens only to the other participant's typing state, never message contents. */
    fun listenTypingState(
        conversationId: String,
        currentUid: String,
        otherUid: String,
        callback: (StudentChatTypingStatus?, Exception?) -> Unit
    ): ListenerRegistration? {
        val expectedId = try { StudentChatIds.conversationId(currentUid, otherUid) }
        catch (error: IllegalArgumentException) { callback(null, error); return null }
        if (conversationId != expectedId) {
            callback(null, IllegalArgumentException("The conversation does not match these two students."))
            return null
        }
        return firestore.collection(CONVERSATIONS).document(conversationId).collection(TYPING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { callback(null, error); return@addSnapshotListener }
                if (snapshot == null) {
                    callback(null, IllegalStateException("No typing state was returned."))
                    return@addSnapshotListener
                }
                val document = snapshot.documents.firstOrNull { it.id == otherUid }
                val updatedAt = document?.getTimestamp("updatedAt")
                val status = if (
                    document?.getBoolean("isTyping") == true &&
                    document.getString("uid") == otherUid &&
                    updatedAt != null
                ) StudentChatTypingStatus(uid = otherUid, updatedAt = updatedAt) else null
                callback(status, null)
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
        val validParticipants = participants.filterIsInstance<String>()
        if (validParticipants != StudentChatIds.participantUids(validParticipants[0], validParticipants[1])) return null
        return StudentChatConversation(
            id = id,
            participantUids = validParticipants,
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
        const val READ_STATES = "readStates"
        const val TYPING = "typing"
        const val PROFILES = "profiles"
        const val FRIEND_REQUESTS = "friendRequests"
        const val ACCEPTED = "accepted"
        const val MAX_MESSAGES = 100L
        const val MAX_CONVERSATIONS = 50L
        val WHITESPACE = Regex("\\s+")
    }
}
