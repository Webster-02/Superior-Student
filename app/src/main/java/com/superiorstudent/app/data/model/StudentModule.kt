package com.superiorstudent.app.data.model

/**
 * University ERP modules exposed by the app. Keeping canonical paths in one place
 * prevents screens and preloading from constructing inconsistent URLs.
 */
enum class StudentModule(val path: String) {
    ATTENDANCE("student/attendance"),
    TIMETABLE("student/class/schedule"),
    ENROLLED_COURSES("student/enrolled/courses"),
    FEE("student/invoices"),
    PROFILE("student/profile"),
    RESULTS("student/results");

    fun acceptsPath(candidatePath: String): Boolean {
        val candidate = candidatePath.trim('/').lowercase()
        val expected = path.trim('/').lowercase()
        if (candidate == expected || candidate.startsWith("$expected/")) return true
        // Attendance detail is a legacy sibling route in the ERP, not a child
        // of /student/attendance. Preserve the pre-existing attendance flow.
        if (this == ATTENDANCE && candidate.startsWith("student/attendancedetail")) return true
        return this == ENROLLED_COURSES && candidate == "student/enroll/courses"
    }
}
