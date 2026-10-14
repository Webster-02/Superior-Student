package com.superiorstudent.app.core.webview

import com.superiorstudent.app.data.model.StudentModule

/**
 * Pure routing policy for ERP DOM extraction.
 *
 * Keep screen-specific script selection out of MainActivity so each feature can
 * be migrated independently and covered by JVM unit tests.
 */
enum class ModuleDataScriptKind {
    ATTENDANCE_SUMMARY,
    GENERAL
}

object ModuleDataScriptSelector {
    fun select(module: StudentModule, currentUrl: String): ModuleDataScriptKind {
        val isAttendanceDetail = currentUrl.contains(
            "/student/attendancedetail",
            ignoreCase = true
        )
        return if (module == StudentModule.ATTENDANCE && !isAttendanceDetail) {
            ModuleDataScriptKind.ATTENDANCE_SUMMARY
        } else {
            ModuleDataScriptKind.GENERAL
        }
    }
}
