package com.superiorstudent.app.feature.profile.data

import android.content.Context
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.data.repository.ErpRepository
import org.json.JSONObject
import java.io.File

/** Profile/GPA accessor backed by the same durable repository as every ERP module. */
class ProfileCache(
    context: Context,
    private val repository: ErpRepository = ErpRepository(context)
) {
    private val legacyFile = File(context.filesDir, FILE_NAME)
    var studentName: String = ""
        private set
    var cgpa: String = ""
        private set
    var sgpa: String = ""
        private set
    var lastUpdated: Long = 0L
        private set

    init { reload() }

    fun isStale(now: Long = System.currentTimeMillis()): Boolean =
        lastUpdated <= 0L || now - lastUpdated >= ErpRepository.CACHE_TTL_MS

    fun save(name: String?, cgpaValue: String?, sgpaValue: String?) {
        val validName = name?.takeIf { ParsingUtils.isValidStudentName(it) }
        val validCgpa = cgpaValue?.takeIf { ParsingUtils.validGpa(it).isNotBlank() }
        val validSgpa = sgpaValue?.takeIf { ParsingUtils.validGpa(it).isNotBlank() }
        if (validName == null && validCgpa == null && validSgpa == null) return
        repository.saveProfileSnapshot(validName, validCgpa, validSgpa)
        reload()
    }

    fun clear() {
        studentName = ""
        cgpa = ""
        sgpa = ""
        lastUpdated = 0L
        repository.clearProfileSnapshot()
        runCatching { legacyFile.delete() }
    }

    fun reload() {
        repository.profileSnapshot()?.let { cached ->
            studentName = cached.name
            cgpa = cached.cgpa
            sgpa = cached.sgpa
            lastUpdated = cached.lastUpdated
            return
        }
        try {
            if (!legacyFile.isFile) return
            val json = JSONObject(legacyFile.readText(Charsets.UTF_8))
            studentName = json.optString("name", "").takeIf { ParsingUtils.isValidStudentName(it) }.orEmpty()
            cgpa = ParsingUtils.validGpa(json.optString("cgpa", ""))
            sgpa = ParsingUtils.validGpa(json.optString("sgpa", ""))
            lastUpdated = legacyFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
            if (studentName.isNotBlank() || cgpa.isNotBlank() || sgpa.isNotBlank()) {
                repository.saveProfileSnapshot(studentName, cgpa, sgpa, lastUpdated)
            }
        } catch (_: Exception) {
            // A corrupt legacy snapshot is ignored without affecting login.
        }
    }

    private companion object {
        const val FILE_NAME = "student_profile_cache.json"
    }
}
