package com.superiorstudent.app.core.webview

import com.superiorstudent.app.data.model.StudentModule
import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleDataScriptSelectorTest {
    @Test
    fun attendanceSummaryUsesDedicatedExtractor() {
        assertEquals(
            ModuleDataScriptKind.ATTENDANCE_SUMMARY,
            ModuleDataScriptSelector.select(
                StudentModule.ATTENDANCE,
                "https://erp.superior.edu.pk/student/attendance"
            )
        )
    }

    @Test
    fun timetableUsesDedicatedExtractorWithLegacyFallbackAvailable() {
        val kind = ModuleDataScriptSelector.select(
            StudentModule.TIMETABLE,
            "https://erp.superior.edu.pk/student/class/schedule"
        )
        assertEquals(ModuleDataScriptKind.TIMETABLE, kind)
        org.junit.Assert.assertTrue(ModuleDataScriptSelector.shouldFallbackToGeneral(kind))
    }

    @Test
    fun attendanceDetailKeepsGeneralExtractorForClassHistory() {
        assertEquals(
            ModuleDataScriptKind.GENERAL,
            ModuleDataScriptSelector.select(
                StudentModule.ATTENDANCE,
                "https://erp.superior.edu.pk/student/attendancedetail/id/204263"
            )
        )
    }

    @Test
    fun invoicesUseDedicatedExtractorWithLegacyFallbackAvailable() {
        val kind = ModuleDataScriptSelector.select(
            StudentModule.FEE,
            "https://erp.superior.edu.pk/student/invoices"
        )
        assertEquals(ModuleDataScriptKind.INVOICES, kind)
        org.junit.Assert.assertTrue(ModuleDataScriptSelector.shouldFallbackToGeneral(kind))
    }

    @Test
    fun otherFeaturesRemainOnLegacyExtractorUntilMigrated() {
        val urlByModule = mapOf(
            StudentModule.PROFILE to "https://erp.superior.edu.pk/student/profile",
            StudentModule.RESULTS to "https://erp.superior.edu.pk/student/results"
        )
        urlByModule.forEach { (module, url) ->
            assertEquals(ModuleDataScriptKind.GENERAL, ModuleDataScriptSelector.select(module, url))
        }
    }
}
