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
        val attendanceDetailLinks: List<AttendanceDetailLink> = emptyList()
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
