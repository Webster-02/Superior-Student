package com.superiorstudent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeMigrationTest {
    @Test fun legacyPinkAndNavySelectionsMigrateToAuto() {
        assertEquals(AppTheme.AUTO, AppTheme.fromStoredKey("pink_neon"))
        assertEquals(AppTheme.AUTO, AppTheme.fromStoredKey("navy_blue"))
    }

    @Test fun missingAndUnknownSelectionsSafelyDefaultToAuto() {
        assertEquals(AppTheme.AUTO, AppTheme.fromStoredKey(null))
        assertEquals(AppTheme.AUTO, AppTheme.fromStoredKey("unexpected"))
    }

    @Test fun validNewSelectionsRemainStable() {
        assertEquals(AppTheme.PAPER, AppTheme.fromStoredKey("paper"))
        assertEquals(AppTheme.NIGHT, AppTheme.fromStoredKey("night"))
        assertEquals(AppTheme.AUTO, AppTheme.fromStoredKey("auto"))
    }
}
