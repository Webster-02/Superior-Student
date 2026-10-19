package com.superiorstudent.app.feature.studentconnect.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.superiorstudent.app.feature.studentconnect.data.StudentNotificationTokenRepository

class StudentConnectMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"]
            ?: message.notification?.title
            ?: "U2U"
        val body = message.data["body"]
            ?: message.notification?.body
            ?: "You have a new update. Open U2U to view it."

        StudentConnectNotificationHelper.show(
            this,
            title,
            body,
            message.messageId?.hashCode() ?: System.currentTimeMillis().toInt()
        )
    }

    override fun onNewToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (!user.isEmailVerified) return

        StudentNotificationTokenRepository().saveToken(user.uid, token) { error ->
            if (error != null) {
                Log.w(TAG, "Could not refresh this device's notification registration.", error)
                return@saveToken
            }
            getSharedPreferences(
                StudentConnectNotificationHelper.PREFERENCES_NAME,
                MODE_PRIVATE
            ).edit()
                .putString(StudentConnectNotificationHelper.KEY_REGISTERED_UID, user.uid)
                .apply()
        }
    }

    private companion object {
        const val TAG = "StudentConnectPush"
    }
}
