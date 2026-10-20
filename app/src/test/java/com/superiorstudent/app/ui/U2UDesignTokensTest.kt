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
    fun surfaceDepthKeepsContentFlatAndLiftsOnlyFloatingSurfaces() {
        assertEquals(1, U2UDesignTokens.SURFACE_STROKE_WIDTH_DP)
        assertEquals(0f, U2UDesignTokens.FLAT_SURFACE_ELEVATION_DP, 0f)
        assertEquals(8f, U2UDesignTokens.COMPOSER_SURFACE_ELEVATION_DP, 0f)
        assertEquals(12f, U2UDesignTokens.FLOATING_SURFACE_ELEVATION_DP, 0f)
    }

    @Test
    fun interactionFeedbackUsesRestrainedMotionValues() {
        assertEquals(0.985f, U2UDesignTokens.PRESS_FEEDBACK_SCALE, 0f)
        assertEquals(0.92f, U2UDesignTokens.PRESS_FEEDBACK_ALPHA, 0f)
        assertEquals(85L, U2UDesignTokens.PRESS_FEEDBACK_DOWN_DURATION_MS)
        assertEquals(150L, U2UDesignTokens.PRESS_FEEDBACK_RELEASE_DURATION_MS)
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
        assertTrue(U2UDesignTokens.RADIUS_ICON_DP < U2UDesignTokens.RADIUS_CONTROL_DP)
        assertTrue(U2UDesignTokens.RADIUS_CONTROL_DP < U2UDesignTokens.RADIUS_CARD_DP)
        assertTrue(U2UDesignTokens.RADIUS_CARD_DP < U2UDesignTokens.RADIUS_HERO_DP)
    }
}
