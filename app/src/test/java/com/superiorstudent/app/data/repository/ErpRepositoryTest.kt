package com.superiorstudent.app.data.repository

import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.ModuleRecord
import com.superiorstudent.app.data.model.StudentModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErpRepositoryTest {
    private fun emptyData(
        cards: List<Pair<String, String>> = emptyList(),
        records: List<ModuleRecord> = emptyList(),
        lines: List<String> = emptyList(),
        overallAttendance: Double? = null
    ) = ModuleData(
        cards = cards,
        tables = emptyList(),
        lines = lines,
        records = records,
        overallAttendance = overallAttendance
    )

    @Test
    fun cachesAndInvalidatesPayloadPerModule() {
        val repository = ErpRepository()
        repository.cachePayload(StudentModule.ATTENDANCE, """{"overallAttendance":90}""")
        repository.cachePayload(StudentModule.FEE, """{"cards":[]}""")

        assertEquals("""{"overallAttendance":90}""", repository.cachedPayload(StudentModule.ATTENDANCE))
        assertEquals("""{"cards":[]}""", repository.cachedPayload(StudentModule.FEE))

        repository.clearCachedPayload(StudentModule.ATTENDANCE)
        assertNull(repository.cachedPayload(StudentModule.ATTENDANCE))
        assertEquals("""{"cards":[]}""", repository.cachedPayload(StudentModule.FEE))

        repository.clearCache()
        assertNull(repository.cachedPayload(StudentModule.FEE))
    }

    @Test
    fun checksWhetherEachModuleHasUsefulData() {
        val repository = ErpRepository()
        val attendance = emptyData(overallAttendance = 88.5)
        val timetable = emptyData(
            records = listOf(
                ModuleRecord(
                    headers = listOf("Day", "Time", "Course"),
                    values = listOf("Monday", "09:00-10:00", "Functional English")
                )
            )
        )
        val fee = emptyData(cards = listOf("Outstanding balance" to "0"))
        val profile = emptyData(
            records = listOf(ModuleRecord(listOf("Field", "Value"), listOf("Program", "BS Optometry")))
        )
        val results = emptyData(lines = listOf("Grade report available"))

        assertTrue(repository.hasUsableData(StudentModule.ATTENDANCE, attendance))
        assertTrue(repository.hasUsableData(StudentModule.TIMETABLE, timetable))
        assertTrue(repository.hasUsableData(StudentModule.FEE, fee))
        assertTrue(repository.hasUsableData(StudentModule.PROFILE, profile))
        assertTrue(repository.hasUsableData(StudentModule.RESULTS, results))
        assertFalse(repository.hasUsableData(StudentModule.ATTENDANCE, emptyData()))
    }
}
