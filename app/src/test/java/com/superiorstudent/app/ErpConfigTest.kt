package com.superiorstudent.app

import com.superiorstudent.app.core.config.ErpConfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErpConfigTest {
    @Test
    fun moduleUrlsUseTheUniversityErpBase() {
        assertEquals("https://erp.superior.edu.pk/web/login", ErpConfig.LOGIN_URL)
        assertEquals("https://erp.superior.edu.pk/student/dashboard", ErpConfig.DASHBOARD_URL)
        assertEquals(
            "https://erp.superior.edu.pk/student/attendance",
            ErpConfig.moduleUrl("student/attendance")
        )
    }

    @Test
    fun acceptsTheErpHostButRejectsLookalikeExternalHosts() {
        assertTrue(ErpConfig.belongsToErp("https://erp.superior.edu.pk/student/attendance"))
        assertTrue(ErpConfig.belongsToErp("https://portal.erp.superior.edu.pk/student/profile"))
        assertFalse(ErpConfig.belongsToErp("https://erp.superior.edu.pk.attacker.example/student/attendance"))
        assertFalse(ErpConfig.belongsToErp("https://attacker.example/erp.superior.edu.pk"))
        assertFalse(ErpConfig.belongsToErp("javascript:alert(1)"))
        assertFalse(ErpConfig.belongsToErp(null))
    }

    @Test
    fun detectsBothStandardAndLegacyLoginRoutes() {
        assertTrue(ErpConfig.isLoginUrl("https://erp.superior.edu.pk/web/login"))
        assertTrue(ErpConfig.isLoginUrl("https://erp.superior.edu.pk/tsg/login"))
        assertTrue(ErpConfig.isLoginUrl("https://erp.superior.edu.pk/tsg/login?next=/student/dashboard"))
        assertTrue(ErpConfig.isLoginUrl("https://erp.superior.edu.pk/student/login"))
        assertFalse(ErpConfig.isLoginUrl("https://example.com/tsg/login"))
        assertFalse(ErpConfig.isLoginUrl("https://erp.superior.edu.pk/student/dashboard"))
    }

    @Test
    fun distinguishesAuthenticatedStudentPagesFromLoginRoutes() {
        assertTrue(ErpConfig.isAuthenticatedUrl("https://erp.superior.edu.pk/student/dashboard"))
        assertFalse(ErpConfig.isAuthenticatedUrl("https://erp.superior.edu.pk/web/login"))
        assertFalse(ErpConfig.isAuthenticatedUrl("https://erp.superior.edu.pk/tsg/login"))
        assertFalse(ErpConfig.isAuthenticatedUrl("https://example.com/student/dashboard"))
    }
}
