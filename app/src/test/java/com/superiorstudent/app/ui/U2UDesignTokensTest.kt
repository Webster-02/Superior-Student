package com.superiorstudent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class U2UDesignTokensTest {
    @Test
    fun typographyScaleUsesFiveRoles() {
        assertEquals(22f, U2UDesignTokens.title.sizeSp, 0f)
        assertEquals(28f, U2UDesignTokens.title.lineHeightSp, 0f)
        assertEquals(18f, U2UDesignTokens.section.sizeSp, 0f)
        assertEquals(14f, U2UDesignTokens.body.sizeSp, 0f)
        assertEquals(12f, U2UDesignTokens.caption.sizeSp, 0f)
        assertEquals(11f, U2UDesignTokens.label.sizeSp, 0f)
        assertTrue(U2UDesignTokens.title.medium)
        assertTrue(U2UDesignTokens.section.medium)
        assertTrue(U2UDesignTokens.label.medium)
    }

    @Test
    fun legacySizesMapToStableSemanticRoles() {
        assertEquals(U2UTextRole.LABEL, U2UDesignTokens.roleForSize(9f))
        assertEquals(U2UTextRole.CAPTION, U2UDesignTokens.roleForSize(11f))
        assertEquals(U2UTextRole.BODY, U2UDesignTokens.roleForSize(14f))
        assertEquals(U2UTextRole.SECTION, U2UDesignTokens.roleForSize(18f))
        assertEquals(U2UTextRole.TITLE, U2UDesignTokens.roleForSize(22f))
    }

    @Test
    fun geometryUsesFourDpGridAndSharedRadiusScale() {
        assertEquals(4, U2UDesignTokens.GRID_UNIT_DP)
        assertEquals(24, U2UDesignTokens.ICON_SIZE_DP)
        assertEquals(12, U2UDesignTokens.RADIUS_ICON_DP)
        assertEquals(16, U2UDesignTokens.RADIUS_CONTROL_DP)
        assertEquals(24, U2UDesignTokens.RADIUS_CARD_DP)
        assertEquals(28, U2UDesignTokens.RADIUS_HERO_DP)
        assertEquals(1.8f, U2UDesignTokens.ICON_STROKE_DP, 0f)
    }
}
