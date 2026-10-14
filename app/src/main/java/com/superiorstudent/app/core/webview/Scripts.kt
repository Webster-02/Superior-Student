package com.superiorstudent.app.core.webview

import android.content.Context
import android.util.Base64

/**
 * Loads the ERP page-interaction JavaScript from assets (js folder) instead of
 * embedding huge string literals in Kotlin. Keeping the scripts as plain JS
 * files makes them editable, syntax-checkable and diffable independently.
 *
 * Dynamic values (username, password, semester label) are injected through
 * base64-encoded JSON placeholders so arbitrary user input can never break
 * out of the script or be executed as code.
 */
object Scripts {
    var sessionHeartbeat: String = ""
        private set
    var loginErrorCheck: String = ""
        private set
    var loginPageProbe: String = ""
        private set
    var moduleData: String = ""
        private set
    var attendanceSummaryData: String = ""
        private set
    var attendanceDetailData: String = ""
        private set
    var timetableData: String = ""
        private set
    var invoiceData: String = ""
        private set
    var profileData: String = ""
        private set
    var resultsData: String = ""
        private set
    var enrolledCoursesData: String = ""
        private set
    var studentProfileText: String = ""
        private set
    private var attendanceSubjectResolverTemplate: String = ""

    private var loginTemplate: String = ""
    private var semesterSelectTemplate: String = ""

    fun loadAll(context: Context) {
        val assets = context.applicationContext.assets
        sessionHeartbeat = assets.read("js/session_heartbeat.js")
        loginErrorCheck = assets.read("js/login_error_check.js")
        loginPageProbe = assets.read("js/login_page_probe.js")
        moduleData = assets.read("js/module_data.js")
        attendanceSummaryData = assets.read("js/attendance_summary.js")
        attendanceDetailData = assets.read("js/attendance_detail.js")
        timetableData = assets.read("js/timetable_data.js")
        invoiceData = assets.read("js/invoice_data.js")
        profileData = assets.read("js/profile_data.js")
        resultsData = assets.read("js/results_data.js")
        enrolledCoursesData = assets.read("js/enrolled_courses.js")
        studentProfileText = assets.read("js/student_profile_text.js")
        attendanceSubjectResolverTemplate = assets.read("js/resolve_attendance_subject.js")
        loginTemplate = assets.read("js/login.js")
        semesterSelectTemplate = assets.read("js/semester_select.js")
    }

    fun moduleDataFor(module: com.superiorstudent.app.data.model.StudentModule, currentUrl: String): String =
        when (ModuleDataScriptSelector.select(module, currentUrl)) {
            ModuleDataScriptKind.ATTENDANCE_SUMMARY -> attendanceSummaryData
            ModuleDataScriptKind.ATTENDANCE_DETAIL -> attendanceDetailData
            ModuleDataScriptKind.TIMETABLE -> timetableData
            ModuleDataScriptKind.INVOICES -> invoiceData
            ModuleDataScriptKind.PROFILE -> profileData
            ModuleDataScriptKind.RESULTS -> resultsData
            ModuleDataScriptKind.ENROLLED_COURSES -> enrolledCoursesData
            ModuleDataScriptKind.GENERAL -> moduleData
        }

    fun loginScript(username: String, password: String): String =
        loginTemplate
            .replace("%USERNAME_B64%", b64JsonString(username))
            .replace("%PASSWORD_B64%", b64JsonString(password))

    fun semesterSelectScript(label: String): String =
        semesterSelectTemplate.replace("%SEMESTER_B64%", b64JsonString(label))

    fun attendanceSubjectResolverScript(course: String, code: String): String =
        attendanceSubjectResolverTemplate
            .replace("%COURSE_B64%", b64JsonString(course))
            .replace("%CODE_B64%", b64JsonString(code))

    /**
     * Encodes [value] as Base64 of its JSON string representation so arbitrary
     * user input can never break out of the injected script or be executed.
     */
    private fun b64JsonString(value: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> { sb.append('\\'); sb.append('"') }
                '\\' -> { sb.append('\\'); sb.append('\\') }
                '\n' -> { sb.append('\\'); sb.append('n') }
                '\r' -> { sb.append('\\'); sb.append('r') }
                '\t' -> { sb.append('\\'); sb.append('t') }
                else -> if (ch.code < 0x20) sb.append(String.format("\\u%04x", ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return Base64.encodeToString(sb.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

}

private fun android.content.res.AssetManager.read(path: String): String =
    open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
