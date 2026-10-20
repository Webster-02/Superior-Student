package com.superiorstudent.app.feature.transfer

import org.junit.Assert.assertTrue
import org.junit.Test

class P2pNearbyConnectionDiagnosisTest {
    @Test
    fun missingPermissionIsExplained() {
        val advice = P2pNearbyConnectionDiagnosis.advice(true, true, true, false)
        assertTrue(advice.contains("permission", ignoreCase = true))
        assertTrue(advice.contains("retry", ignoreCase = true))
    }

    @Test
    fun missingLocalNetworkSuggestsWifiOrHotspot() {
        val advice = P2pNearbyConnectionDiagnosis.advice(false, false, false, true)
        assertTrue(advice.contains("Wi-Fi", ignoreCase = true))
        assertTrue(advice.contains("hotspot", ignoreCase = true))
    }

    @Test
    fun availableLocalWifiWarnsAboutClientIsolation() {
        val advice = P2pNearbyConnectionDiagnosis.advice(true, true, true, true)
        assertTrue(advice.contains("client isolation", ignoreCase = true))
        assertTrue(advice.contains("hotspot", ignoreCase = true))
    }
}
