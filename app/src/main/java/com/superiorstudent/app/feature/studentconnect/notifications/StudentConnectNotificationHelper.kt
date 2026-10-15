package com.superiorstudent.app.feature.studentconnect.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.superiorstudent.app.MainActivity
import com.superiorstudent.app.R

object StudentConnectNotificationHelper {
    const val PREFERENCES_NAME = "student_connect_notifications"
    const val KEY_REGISTERED_UID = "registered_uid"
    const val KEY_PERMISSION_REQUESTED = "permission_requested"
    const val CHANNEL_ID = "student_connect_updates"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Student Connect",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Friend requests and connection updates"
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, title: String, body: String, notificationId: Int) {
        createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_student_connect_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_primary))
            .setContentTitle(title.ifBlank { "Student Connect" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // The user may revoke notification permission while the app is running.
        }
    }
}
