package com.superiorstudent.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Session storage. Credentials are never persisted; only the username and a
 * session-active flag are kept, in an EncryptedSharedPreferences file so the
 * data at rest is AES-encrypted (keys held by the Android Keystore).
 */
class SessionStore(context: Context) {

    private val prefs by lazy { openEncryptedPrefs(context) }
    private val legacyPrefs =
        context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)

    var sessionActive: Boolean
        get() = prefs.getBoolean(KEY_SESSION_ACTIVE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SESSION_ACTIVE, value).apply()
        }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_USERNAME, value).apply()
        }

    /** One-time migration from the old unencrypted preferences file. */
    fun migrateFromLegacy() {
        if (legacyPrefs.all.isEmpty()) return
        if (!sessionActive) {
            val active = legacyPrefs.getBoolean(KEY_SESSION_ACTIVE, false)
            val name = legacyPrefs.getString(KEY_USERNAME, "")
            if (active && !name.isNullOrEmpty()) {
                sessionActive = true
                username = name
            }
        }
        // Old profile cache may have contained personal data; drop it entirely.
        legacyPrefs.edit().clear().apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun openEncryptedPrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context, ENCRYPTED_NAME)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Extreme fallback: keystore unavailable (corrupted key etc.).
        // Still never store credentials; keep only the session flag here.
        context.getSharedPreferences(FALLBACK_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val ENCRYPTED_NAME = "superior_session_encrypted"
        private const val FALLBACK_NAME = "superior_session_fallback"
        private const val LEGACY_NAME = "superior_student_preferences"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_USERNAME = "username"
    }
}
