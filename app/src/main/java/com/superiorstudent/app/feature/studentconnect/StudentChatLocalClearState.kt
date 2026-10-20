package com.superiorstudent.app.feature.studentconnect

import android.content.Context
import com.superiorstudent.app.feature.studentconnect.data.StudentChatMessage

/**
 * Per-device clear boundary for U2U text history.
 *
 * This deliberately does not delete or modify Firestore message documents. A clear affects
 * only the signed-in account's local presentation and cached text messages.
 */
internal class StudentChatLocalClearState(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun cutoffMillis(uid: String, conversationId: String): Long? {
        if (uid.isBlank() || conversationId.isBlank()) return null
        val key = key(uid, conversationId)
        return if (preferences.contains(key)) preferences.getLong(key, 0L) else null
    }

    fun clearBefore(uid: String, conversationId: String, cutoffMillis: Long) {
        if (uid.isBlank() || conversationId.isBlank() || cutoffMillis < 0L) return
        preferences.edit().putLong(key(uid, conversationId), cutoffMillis).apply()
    }

    fun filterMessages(
        uid: String,
        conversationId: String,
        messages: List<StudentChatMessage>
    ): List<StudentChatMessage> {
        val cutoff = cutoffMillis(uid, conversationId)
        if (cutoff == null) return messages
        return messages.filter { message ->
            StudentChatLocalClearPolicy.isMessageVisible(
                message.createdAt?.toDate()?.time,
                cutoff
            )
        }
    }

    private fun key(uid: String, conversationId: String): String =
        "clear_before:$uid:$conversationId"

    companion object {
        private const val PREFERENCES_NAME = "student_chat_local_clear_state_v1"
    }
}

/** Pure policy used by the local message filter and JVM regression tests. */
internal object StudentChatLocalClearPolicy {
    fun isMessageVisible(messageTimestampMillis: Long?, clearBeforeMillis: Long?): Boolean {
        if (clearBeforeMillis == null) return true
        val timestamp = messageTimestampMillis ?: return false
        return timestamp > clearBeforeMillis
    }

    fun isSummaryHidden(lastMessageTimestampMillis: Long?, clearBeforeMillis: Long?): Boolean {
        if (clearBeforeMillis == null) return false
        return lastMessageTimestampMillis == null || lastMessageTimestampMillis <= clearBeforeMillis
    }
}
