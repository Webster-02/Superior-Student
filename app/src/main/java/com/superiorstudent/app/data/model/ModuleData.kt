package com.superiorstudent.app.data.model

/**
 * Shared transport models for ERP page extraction. They intentionally contain
 * no Android UI dependencies so parsers, view models and tests can reuse them.
 */
data class TableData(
        val title: String,
        val headers: List<String>,
        val rows: List<List<String>>
    )

data class ModuleRecord(val headers: List<String>, val values: List<String>)

data class ModuleData(
        val cards: List<Pair<String, String>>,
        val tables: List<TableData>,
        val lines: List<String>,
        val records: List<ModuleRecord>,
        val overallAttendance: Double?,
        val semesterOptions: List<String> = emptyList(),
        val attendanceDetail: AttendanceDetailData? = null,
        val attendanceDetailLinks: List<AttendanceDetailLink> = emptyList(),
        val invoices: List<InvoiceData> = emptyList(),
        val academicResults: List<AcademicTermResult> = emptyList(),
        val enrolledCourses: List<EnrolledCourse> = emptyList()
    )


data class EnrolledCourse(
    val course: String,
    val code: String,
    val credits: String,
    val courseType: String,
    val term: String,
    val faculty: String,
    val department: String,
    val detailUrl: String
)

data class InvoiceData(
    val number: String,
    val invoiceDate: String,
    val dueDate: String,
    val term: String,
    val semester: String,
    val receiptFor: String,
    val barcode: String,
    val totalAmount: String,
    val payable: String,
    val status: String,
    val downloadUrl: String
)

data class AttendanceDetailLink(
        val course: String,
        val code: String,
        val url: String,
        val percentage: Double?
    )

data class AttendanceSession(val date: String, val status: String)

data class AttendanceDetailData(
        val course: String,
        val code: String,
        val conducted: Int?,
        val attended: Int?,
        val percentage: Double?,
        val sessions: List<AttendanceSession>
    )

data class AcademicTermResult(
    val term: String,
    val gradePoints: String,
    val totalCredits: String,
    val earnedCredits: String,
    val gpa: String,
    val cgpa: String,
    val courses: List<AcademicCourseResult>
)

data class AcademicCourseResult(
    val course: String,
    val credits: String,
    val marksObtained: String,
    val gradePoints: String,
    val midGrade: String,
    val finalGrade: String
)
