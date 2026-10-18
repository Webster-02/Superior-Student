package com.superiorstudent.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * Visual appearance choices for Pachattar%. Appearance preferences remain separate from ERP
 * credentials and are not cleared when the student signs out.
 */
enum class AppTheme(
    val key: String,
    val title: String,
    val description: String,
    val previewStartHex: String,
    val previewEndHex: String
) {
    PAPER(
        "paper",
        "Paper",
        "Warm paper surfaces with a dark forest-green and lime palette.",
        "#F4F1EA",
        "#D4F34A"
    ),
    NIGHT(
        "night",
        "Night",
        "Deep forest surfaces with bright lime highlights.",
        "#0D1410",
        "#D4F34A"
    ),
    AUTO(
        "auto",
        "Auto",
        "Follow the appearance selected in Android system settings.",
        "#F4F1EA",
        "#0D1410"
    );

    companion object {
        /**
         * Unknown and legacy keys safely fall back to Auto.
         * pink_neon and navy_blue are deliberately not mapped to new fixed modes.
         */
        fun fromStoredKey(key: String?): AppTheme = when (key) {
            "paper" -> PAPER
            "night" -> NIGHT
            "auto", "pink_neon", "navy_blue", null -> AUTO
            else -> AUTO
        }
    }
}

/**
 * The selected appearance is saved independently from student login/session state.
 * AppCompat's DayNight mode keeps XML resources aligned with Paper, Night or system Auto.
 */
object AppThemePreferences {
    private const val PREFS_NAME = "superior_student_appearance"
    private const val KEY_THEME = "selected_theme"

    fun current(context: Context): AppTheme {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedKey = preferences.getString(KEY_THEME, null)
        val theme = AppTheme.fromStoredKey(storedKey)

        // Migrate old pink/navy values, missing preferences and corrupt keys to a valid key.
        if (storedKey != theme.key) {
            preferences.edit().putString(KEY_THEME, theme.key).apply()
        }
        return theme
    }

    fun save(context: Context, theme: AppTheme) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.key)
            .apply()
    }

    /** Call before Activity super.onCreate, and after a student changes the selector. */
    fun applyToApp(context: Context) {
        val desiredMode = when (current(context)) {
            AppTheme.PAPER -> AppCompatDelegate.MODE_NIGHT_NO
            AppTheme.NIGHT -> AppCompatDelegate.MODE_NIGHT_YES
            AppTheme.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode)
        }
    }

    fun isNight(context: Context): Boolean = when (current(context)) {
        AppTheme.PAPER -> false
        AppTheme.NIGHT -> true
        AppTheme.AUTO -> {
            val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            mask == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
