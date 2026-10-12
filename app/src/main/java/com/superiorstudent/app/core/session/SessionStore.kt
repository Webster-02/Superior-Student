package com.superiorstudent.app.core.session

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores only the session flag and username. Every read/write is guarded so a
 * damaged Keystore or preferences file cannot prevent the native login UI from
 * opening or crash a successful sign-in callback.
 */
class SessionStore(context: Context) {

    private val appContext = context.applicationContext
    private val fallbackPrefs by lazy {
        appContext.getSharedPreferences(FALLBACK_NAME, Context.MODE_PRIVATE)
    }
    private val prefs by lazy { openEncryptedPrefs(appContext) }
    private val legacyPrefs =
        appContext.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)

    var sessionActive: Boolean
        get() = safeReadBoolean(KEY_SESSION_ACTIVE, false)
        set(value) {
            safeWrite(KEY_SESSION_ACTIVE, value)
        }

    var username: String
        get() = safeReadString(KEY_USERNAME, "")
        set(value) {
            safeWrite(KEY_USERNAME, value)
        }

    /** One-time migration from the old unencrypted preferences file. */
    fun migrateFromLegacy() {
        try {
            if (legacyPrefs.all.isEmpty()) return
            if (!sessionActive) {
                val active = legacyPrefs.getBoolean(KEY_SESSION_ACTIVE, false)
                val name = legacyPrefs.getString(KEY_USERNAME, "")
                if (active && !name.isNullOrEmpty()) {
                    sessionActive = true
                    username = name
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Ignoring unreadable legacy session; user can sign in again", t)
        } finally {
            try {
                // The old preferences may contain outdated personal information.
                legacyPrefs.edit().clear().apply()
            } catch (t: Throwable) {
                Log.w(TAG, "Could not clear legacy preferences", t)
            }
        }
    }

    fun clear() {
        try {
            prefs.edit().clear().apply()
        } catch (t: Throwable) {
            Log.w(TAG, "Could not clear encrypted session preferences", t)
        }
        try {
            fallbackPrefs.edit().clear().apply()
        } catch (t: Throwable) {
            Log.w(TAG, "Could not clear fallback session preferences", t)
        }
    }

    private fun safeReadBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            prefs.getBoolean(key, defaultValue)
        } catch (t: Throwable) {
            Log.w(TAG, "Encrypted session read failed for $key; using fallback", t)
            try { fallbackPrefs.getBoolean(key, defaultValue) } catch (_: Throwable) { defaultValue }
        }
    }

    private fun safeReadString(key: String, defaultValue: String): String {
        return try {
            prefs.getString(key, defaultValue) ?: defaultValue
        } catch (t: Throwable) {
            Log.w(TAG, "Encrypted session read failed for $key; using fallback", t)
            try { fallbackPrefs.getString(key, defaultValue) ?: defaultValue } catch (_: Throwable) { defaultValue }
        }
    }

    private fun safeWrite(key: String, value: Any) {
        try {
            val editor = prefs.edit()
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
            }
            editor.apply()
        } catch (t: Throwable) {
            Log.w(TAG, "Encrypted session write failed for $key; using fallback", t)
            try {
                val editor = fallbackPrefs.edit()
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                }
                editor.apply()
            } catch (fallbackError: Throwable) {
                Log.e(TAG, "Fallback session write also failed for $key", fallbackError)
            }
        }
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
    } catch (t: Throwable) {
        Log.w(TAG, "Encrypted preferences unavailable; using fallback store", t)
        context.getSharedPreferences(FALLBACK_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "SessionStore"
        private const val ENCRYPTED_NAME = "superior_session_encrypted"
        private const val FALLBACK_NAME = "superior_session_fallback"
        private const val LEGACY_NAME = "superior_student_preferences"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_USERNAME = "username"
    }
}
