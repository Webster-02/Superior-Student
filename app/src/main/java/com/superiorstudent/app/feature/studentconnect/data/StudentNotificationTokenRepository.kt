package com.superiorstudent.app.feature.studentconnect.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class StudentNotificationTokenRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun saveToken(uid: String, token: String, callback: (Exception?) -> Unit) {
        if (uid.isBlank() || token.isBlank() || token.length > MAX_TOKEN_LENGTH) {
            callback(IllegalArgumentException("The device notification token is invalid."))
            return
        }

        firestore.collection(TOKENS)
            .document(uid)
            .set(
                mapOf(
                    "uid" to uid,
                    "token" to token,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it) }
    }

    fun deleteToken(uid: String, callback: (Exception?) -> Unit) {
        if (uid.isBlank()) {
            callback(IllegalArgumentException("A signed-in student is required."))
            return
        }

        firestore.collection(TOKENS)
            .document(uid)
            .delete()
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it) }
    }

    private companion object {
        const val TOKENS = "studentNotificationTokens"
        const val MAX_TOKEN_LENGTH = 4096
    }
}
