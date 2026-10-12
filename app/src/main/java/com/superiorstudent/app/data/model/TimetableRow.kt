package com.superiorstudent.app.data.model

/** Normalized class schedule row parsed from the university ERP. */
data class TimetableRow(
    val day: String,
    val time: String,
    val course: String,
    val room: String,
    val code: String
)
