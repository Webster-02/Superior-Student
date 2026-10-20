package com.superiorstudent.app.data.repository
import android.content.Context

import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.ModuleRecord
import com.superiorstudent.app.data.model.StudentModule
import com.superiorstudent.app.data.parser.ErpPageParser
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Central data boundary for ERP payload parsing, cache invalidation and
 * per-feature readiness checks. WebView authentication/navigation remains
 * owned by MainActivity so the university session flow is not hidden here.
 */
class ErpRepository private constructor(private val cacheFile: File?) {
    constructor() : this(null)
    constructor(context: Context) : this(File(context.applicationContext.filesDir, CACHE_FILE_NAME))

    data class ProfileSnapshot(
        val name: String,
        val cgpa: String,
        val sgpa: String,
        val lastUpdated: Long
    )

    private val lock = Any()
    private val cachedPayloads = mutableMapOf<StudentModule, String>()
    private val moduleLastUpdated = mutableMapOf<StudentModule, Long>()
    private val latestFetchGeneration = mutableMapOf<StudentModule, Long>()
    private val appliedFetchGeneration = mutableMapOf<StudentModule, Long>()
    private var profileSnapshot: ProfileSnapshot? = null
    private var cacheOwnerUsername: String = ""
    private val persistenceExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ErpDataStore").apply { isDaemon = true }
    }

    init { loadSnapshot() }

    fun parse(payload: String): ModuleData? = ErpPageParser.parse(payload)

    fun cachedPayload(module: StudentModule): String? = synchronized(lock) { cachedPayloads[module] }

    fun lastUpdated(module: StudentModule): Long? = synchronized(lock) { moduleLastUpdated[module] }

    fun needsRefresh(module: StudentModule, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            val updated = moduleLastUpdated[module]
            cachedPayloads[module].isNullOrBlank() || updated == null || now - updated >= CACHE_TTL_MS
        }

    fun beginFetch(module: StudentModule): Long = synchronized(lock) {
        val next = maxOf((latestFetchGeneration[module] ?: 0L) + 1L, (appliedFetchGeneration[module] ?: 0L) + 1L)
        latestFetchGeneration[module] = next
        next
    }

    /** Failed, empty, unusable, or superseded data cannot overwrite the last known good snapshot. */
    @JvmOverloads
    fun cachePayload(module: StudentModule, payload: String, fetchGeneration: Long? = null): Boolean {
        if (payload.isBlank()) return false
        val parsed = parse(payload) ?: return false
        if (!hasUsableData(module, parsed)) return false
        synchronized(lock) {
            if (fetchGeneration != null) {
                val latest = latestFetchGeneration[module] ?: fetchGeneration
                if (fetchGeneration != latest) return false
                if (fetchGeneration < (appliedFetchGeneration[module] ?: Long.MIN_VALUE)) return false
                appliedFetchGeneration[module] = fetchGeneration
            }
            cachedPayloads[module] = payload
            moduleLastUpdated[module] = System.currentTimeMillis()
        }
        persistSnapshotAsync()
        return true
    }

    fun clearCachedPayload(module: StudentModule) {
        synchronized(lock) {
            cachedPayloads.remove(module)
            moduleLastUpdated.remove(module)
            latestFetchGeneration.remove(module)
            appliedFetchGeneration.remove(module)
        }
        persistSnapshotAsync()
    }

    fun clearCache() {
        synchronized(lock) {
            cachedPayloads.clear()
            moduleLastUpdated.clear()
            latestFetchGeneration.clear()
            appliedFetchGeneration.clear()
        }
        persistSnapshotAsync()
    }

    fun profileSnapshot(): ProfileSnapshot? = synchronized(lock) { profileSnapshot }

    fun saveProfileSnapshot(
        name: String?,
        cgpa: String?,
        sgpa: String?,
        lastUpdated: Long = System.currentTimeMillis()
    ) {
        if (name.isNullOrBlank() && cgpa.isNullOrBlank() && sgpa.isNullOrBlank()) return
        synchronized(lock) {
            val old = profileSnapshot
            profileSnapshot = ProfileSnapshot(
                name = name?.takeIf { it.isNotBlank() } ?: old?.name.orEmpty(),
                cgpa = cgpa?.takeIf { it.isNotBlank() } ?: old?.cgpa.orEmpty(),
                sgpa = sgpa?.takeIf { it.isNotBlank() } ?: old?.sgpa.orEmpty(),
                lastUpdated = lastUpdated
            )
        }
        persistSnapshotAsync()
    }

    fun clearProfileSnapshot() {
        synchronized(lock) { profileSnapshot = null }
        persistSnapshotAsync()
    }

    fun activateAccount(username: String): Boolean {
        if (username.isBlank()) return false
        var changed = false
        synchronized(lock) {
            val oldUsername = cacheOwnerUsername
            if (oldUsername.isNotBlank() && !oldUsername.equals(username, ignoreCase = true)) {
                cachedPayloads.clear()
                moduleLastUpdated.clear()
                latestFetchGeneration.clear()
                appliedFetchGeneration.clear()
                profileSnapshot = null
                changed = true
            }
            cacheOwnerUsername = username
        }
        persistSnapshotAsync()
        return changed
    }

    fun clearAccountIdentity() {
        synchronized(lock) { cacheOwnerUsername = "" }
        persistSnapshotAsync()
    }

    fun hasUsableData(module: StudentModule, data: ModuleData): Boolean =
        when (module) {
            StudentModule.ATTENDANCE -> {
                if (data.attendanceDetail != null || data.overallAttendance != null) {
                    true
                } else if (data.attendanceDetailLinks.isNotEmpty()) {
                    data.attendanceDetailLinks.any { link ->
                        isCleanAttendanceLabel(link.course) && link.code.isNotBlank() &&
                            link.url.contains("/student/attendancedetail", ignoreCase = true)
                    }
                } else {
                    data.records.any { record ->
                        val joined = record.values.joinToString(" ")
                        record.values.size >= 2 && isCleanAttendanceLabel(joined) &&
                            Regex("""\d{1,3}(?:\.\d+)?\s*%""").containsMatchIn(joined)
                    } || data.tables.any { table ->
                        table.rows.any { row -> row.size >= 2 && isCleanAttendanceLabel(row.joinToString(" ")) }
                    } || data.lines.any { it.contains("%") && isCleanAttendanceLabel(it) }
                }
            }

            StudentModule.TIMETABLE -> data.records.any(::looksLikeScheduleRecord) ||
                data.records.any { record ->
                    record.headers.any { header ->
                        header.contains("time", true) ||
                            header.contains("day", true) ||
                            header.contains("course", true)
                    } && record.values.any { value ->
                        Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(value)
                    }
                } ||
                data.lines.any { Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(it) }

            StudentModule.FEE -> data.invoices.isNotEmpty() ||
                data.tables.any { it.rows.isNotEmpty() } ||
                data.cards.isNotEmpty() ||
                data.records.any { it.values.size >= 2 }

            StudentModule.PROFILE -> data.cards.isNotEmpty() ||
                data.records.any { it.headers.any { header -> header.equals("Field", true) } } ||
                data.tables.any { it.rows.isNotEmpty() }

            StudentModule.RESULTS -> data.academicResults.isNotEmpty() ||
                data.semesterOptions.isNotEmpty() ||
                data.tables.any { it.rows.isNotEmpty() } ||
                data.records.any { it.values.size >= 2 } ||
                data.lines.any { it.contains("result", true) || it.contains("grade", true) }

            StudentModule.ENROLLED_COURSES -> data.enrolledCourses.any {
                it.course.isNotBlank() && it.code.isNotBlank()
            } || data.records.any { record ->
                val values = record.values.joinToString(" ")
                Regex("""\b[A-Z]{2,8}[- ]?\d{3,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(values) &&
                    (values.contains("credit", true) || values.contains("compulsory", true))
            }
        }

    private fun loadSnapshot() {
        val file = cacheFile ?: return
        try {
            if (!file.isFile) return
            val root = JSONObject(file.readText(Charsets.UTF_8))
            synchronized(lock) {
                cacheOwnerUsername = root.optString("ownerUsername", "")
                root.optJSONObject("profile")?.let { profile ->
                    profileSnapshot = ProfileSnapshot(
                        name = profile.optString("name", ""),
                        cgpa = profile.optString("cgpa", ""),
                        sgpa = profile.optString("sgpa", ""),
                        lastUpdated = profile.optLong("lastUpdated", 0L)
                    ).takeIf { it.name.isNotBlank() || it.cgpa.isNotBlank() || it.sgpa.isNotBlank() }
                }
                val modules = root.optJSONObject("modules")
                StudentModule.values().forEach { module ->
                    val item = modules?.optJSONObject(module.name) ?: return@forEach
                    val payload = item.optString("payload", "")
                    val parsed = payload.takeIf { it.isNotBlank() }?.let(::parse)
                    if (parsed != null && hasUsableData(module, parsed)) {
                        cachedPayloads[module] = payload
                        moduleLastUpdated[module] = item.optLong("lastUpdated", 0L)
                    }
                }
            }
        } catch (error: Exception) {
            android.util.Log.w(TAG, "Ignoring unreadable ERP data snapshot", error)
        }
    }

    private fun persistSnapshotAsync() {
        val file = cacheFile ?: return
        persistenceExecutor.execute {
            try {
                val serialized = synchronized(lock) {
                    val root = JSONObject().put("schemaVersion", 1).put("ownerUsername", cacheOwnerUsername)
                    val modules = JSONObject()
                    cachedPayloads.forEach { (module, payload) ->
                        modules.put(
                            module.name,
                            JSONObject().put("payload", payload).put("lastUpdated", moduleLastUpdated[module] ?: 0L)
                        )
                    }
                    root.put("modules", modules)
                    profileSnapshot?.let { profile ->
                        root.put("profile", JSONObject().put("name", profile.name)
                            .put("cgpa", profile.cgpa).put("sgpa", profile.sgpa)
                            .put("lastUpdated", profile.lastUpdated))
                    }
                    root.toString()
                }
                file.parentFile?.mkdirs()
                val temporary = File(file.absolutePath + ".tmp")
                temporary.writeText(serialized, Charsets.UTF_8)
                if (!temporary.renameTo(file)) {
                    file.writeText(serialized, Charsets.UTF_8)
                    temporary.delete()
                }
            } catch (error: Exception) {
                android.util.Log.w(TAG, "Could not persist ERP data snapshot", error)
            }
        }
    }

    private fun isCleanAttendanceLabel(value: String): Boolean {
        val text = value.trim()
        if (text.length < 3 || text.length > 220) return false
        val percentCount = Regex("""\d{1,3}(?:\.\d+)?\s*%""").findAll(text).count()
        val codeCount = Regex("""\b[A-Z]{2,8}[- ]?\d{4,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE)
            .findAll(text).count()
        return percentCount <= 1 && codeCount <= 1 &&
            !Regex("""Academic Progress|Credits Completed""", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private fun looksLikeScheduleRecord(record: ModuleRecord): Boolean {
        val joined = record.values.joinToString(" ")
        return Regex("""\b(?:[01]?\d|2[0-3]):[0-5]\d\b""").containsMatchIn(joined) ||
            Regex(
                """\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(joined)
    }

    companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val CACHE_FILE_NAME = "erp_student_data_store.json"
        private const val TAG = "ErpRepository"
    }
}
