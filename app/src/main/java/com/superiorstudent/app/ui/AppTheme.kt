package com.superiorstudent.app.ui

import android.content.Context

/** Visual theme selected by the student. Kept separate from ERP login/session storage. */
enum class AppTheme(
    val key: String,
    val title: String,
    val description: String,
    val previewStartHex: String,
    val previewEndHex: String
) {
    PINK_NEON(
        "pink_neon",
        "Neon Pink",
        "Vibrant pink highlights on a deep, calm background.",
        "#FF2389",
        "#9B1458"
    ),
    NAVY_BLUE(
        "navy_blue",
        "Navy Blue",
        "Electric blue highlights on a midnight navy background.",
        "#237BFF",
        "#092A70"
    )
}

/**
 * Appearance preferences are intentionally stored in their own file. Logging out clears the
 * student session, not the theme a student chose for the app.
 */
object AppThemePreferences {
    private const val PREFS_NAME = "superior_student_appearance"
    private const val KEY_THEME = "selected_theme"

    fun current(context: Context): AppTheme {
        val key = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, AppTheme.NAVY_BLUE.key)
        return AppTheme.values().firstOrNull { it.key == key } ?: AppTheme.NAVY_BLUE
    }

    fun save(context: Context, theme: AppTheme) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.key)
            .apply()
    }
}
