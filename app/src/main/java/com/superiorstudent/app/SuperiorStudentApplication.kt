package com.superiorstudent.app

import android.app.Application
import com.superiorstudent.app.feature.studentconnect.notifications.StudentConnectNotificationHelper

class SuperiorStudentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StudentConnectNotificationHelper.createNotificationChannel(this)
    }
}
