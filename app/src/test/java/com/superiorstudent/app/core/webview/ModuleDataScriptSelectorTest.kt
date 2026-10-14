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
    fun otherFeaturesRemainOnLegacyExtractorUntilMigrated() {
        val urlByModule = mapOf(
            StudentModule.TIMETABLE to "https://erp.superior.edu.pk/student/class/schedule",
            StudentModule.FEE to "https://erp.superior.edu.pk/student/invoices",
            StudentModule.PROFILE to "https://erp.superior.edu.pk/student/profile",
            StudentModule.RESULTS to "https://erp.superior.edu.pk/student/results"
        )
        urlByModule.forEach { (module, url) ->
            assertEquals(ModuleDataScriptKind.GENERAL, ModuleDataScriptSelector.select(module, url))
        }
    }
}
