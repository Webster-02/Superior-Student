package com.superiorstudent.app.feature.studentconnect.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.security.SecureRandom

data class StudentProfile(
    val uid: String,
    val publicId: String,
    val displayName: String
)

/**
 * Reserves a public student ID and creates the profile in one Firestore transaction.
 * Security rules require both documents to be created together, which prevents an
 * ID from being partially reserved if profile creation fails.
 */
class StudentProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun getOwnProfile(uid: String, callback: (StudentProfile?, Exception?) -> Unit) {
        firestore.collection(PROFILES).document(uid)
            .get()
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    callback(null, task.exception ?: IllegalStateException("Could not load profile."))
                    return@addOnCompleteListener
                }

                val snapshot = task.result
                if (!snapshot.exists()) {
                    callback(null, null)
                    return@addOnCompleteListener
                }

                val profile = snapshot.toStudentProfile()
                if (profile == null || profile.uid != uid) {
                    callback(null, IllegalStateException("The saved student profile is incomplete."))
                } else {
                    callback(profile, null)
                }
            }
    }

    fun createProfile(
        uid: String,
        displayName: String,
        callback: (StudentProfile?, Exception?) -> Unit
    ) {
        val normalizedName = displayName.trim().replace(WHITESPACE, " ")
        if (normalizedName.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) {
            callback(null, IllegalArgumentException("Enter a display name between 2 and 60 characters."))
            return
        }
        tryCandidate(uid, normalizedName, 0, callback)
    }

    private fun tryCandidate(
        uid: String,
        displayName: String,
        attempt: Int,
        callback: (StudentProfile?, Exception?) -> Unit
    ) {
        if (attempt >= MAX_ID_ATTEMPTS) {
            callback(null, IllegalStateException("Could not reserve a unique student ID. Please try again."))
            return
        }

        val publicId = StudentPublicIdGenerator.generate()
        val profileRef = firestore.collection(PROFILES).document(uid)
        val idRef = firestore.collection(STUDENT_IDS).document(publicId)

        firestore.runTransaction { transaction ->
            val existingProfile = transaction.get(profileRef)
            if (existingProfile.exists()) {
                val saved = existingProfile.toStudentProfile()
                    ?: throw IllegalStateException("The saved student profile is incomplete.")
                return@runTransaction ReservationResult.Reserved(saved)
            }

            val existingId = transaction.get(idRef)
            if (existingId.exists()) {
                return@runTransaction ReservationResult.Collision
            }

            transaction.set(
                idRef,
                mapOf(
                    "uid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                profileRef,
                mapOf(
                    "uid" to uid,
                    "publicId" to publicId,
                    "displayName" to displayName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            ReservationResult.Reserved(StudentProfile(uid, publicId, displayName))
        }.addOnSuccessListener { result ->
            when (result) {
                is ReservationResult.Reserved -> callback(result.profile, null)
                ReservationResult.Collision -> tryCandidate(uid, displayName, attempt + 1, callback)
            }
        }.addOnFailureListener { error ->
            callback(null, error)
        }
    }

    private fun DocumentSnapshot.toStudentProfile(): StudentProfile? {
        val uid = getString("uid") ?: return null
        val publicId = getString("publicId") ?: return null
        val displayName = getString("displayName") ?: return null
        if (uid.isBlank() || publicId.isBlank() || displayName.isBlank()) return null
        return StudentProfile(uid, publicId, displayName)
    }

    private sealed class ReservationResult {
        data class Reserved(val profile: StudentProfile) : ReservationResult()
        object Collision : ReservationResult()
    }

    private companion object {
        const val PROFILES = "profiles"
        const val STUDENT_IDS = "studentIds"
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 60
        const val MAX_ID_ATTEMPTS = 8
        val WHITESPACE = Regex("""\s+""")
    }
}

/** IDs are human-shareable; ambiguous characters are excluded to reduce typing errors. */
internal object StudentPublicIdGenerator {
    private const val PREFIX = "UNI-"
    private const val SUFFIX_LENGTH = 7
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val secureRandom = SecureRandom()

    fun generate(): String = buildString(PREFIX.length + SUFFIX_LENGTH) {
        append(PREFIX)
        repeat(SUFFIX_LENGTH) {
            append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
        }
    }
}
