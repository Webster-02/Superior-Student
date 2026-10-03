package com.superiorstudent.app

import android.content.Context

/**
 * Lightweight cache for the student's display profile (name + GPA summary).
 * Replaces the old plaintext SharedPreferences storage of personal data.
 * Values are kept in memory and mirrored to a private file so the dashboard
 * can render instantly after process death without persisting PII in prefs.
 */
class ProfileCache(context: Context) {

    private val file = java.io.File(context.filesDir, FILE_NAME)

    var studentName: String = ""
        private set
    var cgpa: String = ""
        private set
    var sgpa: String = ""
        private set

    init {
        load()
    }

    fun save(name: String?, cgpaValue: String?, sgpaValue: String?) {
        if (!name.isNullOrBlank() && ParsingUtils.isValidStudentName(name)) studentName = name
        if (!cgpaValue.isNullOrBlank()) cgpa = cgpaValue
        if (!sgpaValue.isNullOrBlank()) sgpa = sgpaValue
        persist()
    }

    fun clear() {
        studentName = ""
        cgpa = ""
        sgpa = ""
        file.delete()
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val json = org.json.JSONObject(file.readText(Charsets.UTF_8))
            studentName = json.optString("name", "")
            cgpa = json.optString("cgpa", "")
            sgpa = json.optString("sgpa", "")
        } catch (_: Exception) {
            // Corrupt cache is simply ignored; it will be refreshed on next login.
        }
    }

    private fun persist() {
        try {
            val json = org.json.JSONObject()
                .put("name", studentName)
                .put("cgpa", cgpa)
                .put("sgpa", sgpa)
            file.writeText(json.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
            // Non-critical: cache write failures only cost a slower first paint.
        }
    }

    private companion object {
        const val FILE_NAME = "student_profile_cache.json"
    }
}
