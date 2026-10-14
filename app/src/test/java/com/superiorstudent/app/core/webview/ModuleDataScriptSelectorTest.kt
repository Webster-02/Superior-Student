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
    fun attendanceDetailUsesDedicatedExtractorAndKeepsLegacyFallback() {
        val kind = ModuleDataScriptSelector.select(
            StudentModule.ATTENDANCE,
            "https://erp.superior.edu.pk/student/attendancedetail/id/204263"
        )
        assertEquals(ModuleDataScriptKind.ATTENDANCE_DETAIL, kind)
        org.junit.Assert.assertTrue(ModuleDataScriptSelector.shouldFallbackToGeneral(kind))
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
    fun profileUsesDedicatedExtractorWithLegacyFallbackAvailable() {
        val kind = ModuleDataScriptSelector.select(
            StudentModule.PROFILE,
            "https://erp.superior.edu.pk/student/profile"
        )
        assertEquals(ModuleDataScriptKind.PROFILE, kind)
        org.junit.Assert.assertTrue(ModuleDataScriptSelector.shouldFallbackToGeneral(kind))
    }

    @Test
    fun enrolledCoursesUseDedicatedExtractorWithLegacyFallbackAndSupportRouteAlias() {
        val kind = ModuleDataScriptSelector.select(
            StudentModule.ENROLLED_COURSES,
            "https://erp.superior.edu.pk/student/enrolled/courses"
        )
        assertEquals(ModuleDataScriptKind.ENROLLED_COURSES, kind)
        org.junit.Assert.assertTrue(ModuleDataScriptSelector.shouldFallbackToGeneral(kind))
        org.junit.Assert.assertTrue(StudentModule.ENROLLED_COURSES.acceptsPath("/student/enroll/courses"))
        org.junit.Assert.assertTrue(StudentModule.ENROLLED_COURSES.acceptsPath("/student/enrolled/courses"))
    }

    @Test
    fun attendanceDetailRouteRemainsAcceptedByTheModuleRouter() {
        assertEquals(
            true,
            StudentModule.ATTENDANCE.acceptsPath("/student/attendancedetail/id/204263")
        )
    }

    @Test
    fun resultsUseDedicatedExtractorWithLegacyFallbackAvailable() {
        val kind = ModuleDataScriptSelector.select(
            StudentModule.RESULTS,
            "https://erp.superior.edu.pk/student/results/"
        )
        assertEquals(ModuleDataScriptKind.RESULTS, kind)
        org.junit.Assert.assertTrue(ModuleDataScriptSelector.shouldFallbackToGeneral(kind))
    }

}
