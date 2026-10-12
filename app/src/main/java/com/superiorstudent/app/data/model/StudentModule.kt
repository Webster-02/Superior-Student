package com.superiorstudent.app.data.model

/**
 * University ERP modules exposed by the app. Keeping canonical paths in one place
 * prevents screens and preloading from constructing inconsistent URLs.
 */
enum class StudentModule(val path: String) {
    ATTENDANCE("student/attendance"),
    TIMETABLE("student/class/schedule"),
    FEE("student/invoices"),
    PROFILE("student/profile"),
    RESULTS("student/results")
}
