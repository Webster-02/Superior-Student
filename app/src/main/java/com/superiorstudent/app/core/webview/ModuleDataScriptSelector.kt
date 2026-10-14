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
    ATTENDANCE_DETAIL,
    TIMETABLE,
    INVOICES,
    PROFILE,
    RESULTS,
    GENERAL
}

object ModuleDataScriptSelector {
    fun select(module: StudentModule, currentUrl: String): ModuleDataScriptKind {
        val isAttendanceDetail = currentUrl.contains(
            "/student/attendancedetail",
            ignoreCase = true
        )
        return when {
            module == StudentModule.ATTENDANCE && !isAttendanceDetail ->
                ModuleDataScriptKind.ATTENDANCE_SUMMARY
            module == StudentModule.ATTENDANCE && isAttendanceDetail ->
                ModuleDataScriptKind.ATTENDANCE_DETAIL
            module == StudentModule.TIMETABLE ->
                ModuleDataScriptKind.TIMETABLE
            module == StudentModule.FEE ->
                ModuleDataScriptKind.INVOICES
            module == StudentModule.PROFILE ->
                ModuleDataScriptKind.PROFILE
            module == StudentModule.RESULTS ->
                ModuleDataScriptKind.RESULTS
            else -> ModuleDataScriptKind.GENERAL
        }
    }

    fun shouldFallbackToGeneral(kind: ModuleDataScriptKind): Boolean =
        kind == ModuleDataScriptKind.ATTENDANCE_DETAIL ||
            kind == ModuleDataScriptKind.TIMETABLE ||
            kind == ModuleDataScriptKind.INVOICES ||
            kind == ModuleDataScriptKind.PROFILE ||
            kind == ModuleDataScriptKind.RESULTS
}
