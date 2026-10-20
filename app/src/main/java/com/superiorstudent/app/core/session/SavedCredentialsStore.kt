package com.superiorstudent.app.core.session

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Optional credentials for one silent retry after an explicit ERP session rejection. */
class SavedCredentialsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext, MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (error: Exception) {
            Log.w(TAG, "Encrypted credential storage unavailable; credentials will not be persisted", error)
            null
        }
    }

    data class Credentials(val username: String, val password: String)

    fun save(username: String, password: String): Boolean {
        if (username.isBlank() || password.isBlank()) return false
        val store = prefs ?: return false
        return try {
            store.edit().putString(KEY_USERNAME, username).putString(KEY_PASSWORD, password).commit()
        } catch (error: Exception) {
            Log.w(TAG, "Could not save encrypted university credentials", error)
            false
        }
    }

    fun load(): Credentials? {
        val store = prefs ?: return null
        return try {
            val username = store.getString(KEY_USERNAME, "").orEmpty()
            val password = store.getString(KEY_PASSWORD, "").orEmpty()
            if (username.isBlank() || password.isBlank()) null else Credentials(username, password)
        } catch (error: Exception) {
            Log.w(TAG, "Could not read encrypted university credentials", error)
            null
        }
    }

    fun clear() {
        try {
            prefs?.edit()?.clear()?.commit()
        } catch (error: Exception) {
            Log.w(TAG, "Could not clear encrypted university credentials", error)
        }
    }

    private companion object {
        const val TAG = "SavedCredentials"
        const val PREFS_NAME = "superior_saved_credentials_encrypted"
        const val MASTER_KEY_ALIAS = "superior_saved_credentials_master"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}
