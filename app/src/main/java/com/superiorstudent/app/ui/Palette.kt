package com.superiorstudent.app.ui

import android.content.Context
import android.graphics.Color

/**
 * Central design tokens for the two dark appearance modes. Both themes keep the same
 * contrast and surface hierarchy; only the brand accent changes from neon pink to navy blue.
 */
object Palette {
    private data class Tokens(
        val primary: Int,
        val primaryDark: Int,
        val primaryLight: Int,
        val accent: Int,
        val accentDeep: Int,
        val surface: Int,
        val surfaceAlt: Int,
        val stroke: Int,
        val ink900: Int,
        val ink700: Int,
        val ink500: Int,
        val ink400: Int,
        val success: Int,
        val successBg: Int,
        val warning: Int,
        val warningBg: Int,
        val danger: Int,
        val dangerBg: Int,
        val info: Int,
        val infoBg: Int,
        val violet: Int,
        val violetBg: Int,
        val ringTrack: Int
    )

    private fun hex(value: String): Int = Color.parseColor(value)

    private val pink = Tokens(
        primary = hex("#F72585"),
        primaryDark = hex("#FF69B4"),
        primaryLight = hex("#FFADD2"),
        accent = hex("#F472B6"),
        accentDeep = hex("#C81E67"),
        surface = hex("#17121E"),
        surfaceAlt = hex("#080A13"),
        stroke = hex("#382336"),
        ink900 = hex("#FFF7FC"),
        ink700 = hex("#E7D9E5"),
        ink500 = hex("#B7A5B5"),
        ink400 = hex("#8F7C8D"),
        success = hex("#34D399"),
        successBg = hex("#073B32"),
        warning = hex("#FBBF24"),
        warningBg = hex("#4C310E"),
        danger = hex("#FB7185"),
        dangerBg = hex("#4B1827"),
        info = hex("#93C5FD"),
        infoBg = hex("#172A45"),
        violet = hex("#C4B5FD"),
        violetBg = hex("#302344"),
        ringTrack = hex("#3A2638")
    )

    private val navy = Tokens(
        primary = hex("#2678FF"),
        primaryDark = hex("#75A8FF"),
        primaryLight = hex("#B3D0FF"),
        accent = hex("#38BDF8"),
        accentDeep = hex("#0EA5E9"),
        surface = hex("#101C34"),
        surfaceAlt = hex("#061126"),
        stroke = hex("#20385F"),
        ink900 = hex("#F5F8FF"),
        ink700 = hex("#D2DDF2"),
        ink500 = hex("#9BADC9"),
        ink400 = hex("#7286AA"),
        success = hex("#34D399"),
        successBg = hex("#073B32"),
        warning = hex("#FBBF24"),
        warningBg = hex("#4C310E"),
        danger = hex("#FB7185"),
        dangerBg = hex("#4B1827"),
        info = hex("#9AC7FF"),
        infoBg = hex("#152846"),
        violet = hex("#B6A2FF"),
        violetBg = hex("#28224E"),
        ringTrack = hex("#22385D")
    )

    private fun tokens(ctx: Context): Tokens =
        if (AppThemePreferences.current(ctx) == AppTheme.PINK_NEON) pink else navy

    fun primary(ctx: Context) = tokens(ctx).primary
    fun primaryDark(ctx: Context) = tokens(ctx).primaryDark
    fun primaryLight(ctx: Context) = tokens(ctx).primaryLight
    fun accent(ctx: Context) = tokens(ctx).accent
    fun accentDeep(ctx: Context) = tokens(ctx).accentDeep

    fun surface(ctx: Context) = tokens(ctx).surface
    fun surfaceAlt(ctx: Context) = tokens(ctx).surfaceAlt
    fun stroke(ctx: Context) = tokens(ctx).stroke

    fun ink900(ctx: Context) = tokens(ctx).ink900
    fun ink700(ctx: Context) = tokens(ctx).ink700
    fun ink500(ctx: Context) = tokens(ctx).ink500
    fun ink400(ctx: Context) = tokens(ctx).ink400

    fun success(ctx: Context) = tokens(ctx).success
    fun successBg(ctx: Context) = tokens(ctx).successBg
    fun warning(ctx: Context) = tokens(ctx).warning
    fun warningBg(ctx: Context) = tokens(ctx).warningBg
    fun danger(ctx: Context) = tokens(ctx).danger
    fun dangerBg(ctx: Context) = tokens(ctx).dangerBg
    fun info(ctx: Context) = tokens(ctx).info
    fun infoBg(ctx: Context) = tokens(ctx).infoBg
    fun violet(ctx: Context) = tokens(ctx).violet
    fun violetBg(ctx: Context) = tokens(ctx).violetBg
    fun ringTrack(ctx: Context) = tokens(ctx).ringTrack

    /** Attendance percentage → status color, consistent in both themes. */
    fun attendanceColor(ctx: Context, percent: Double): Int = when {
        percent >= 85.0 -> success(ctx)
        percent >= 75.0 -> warning(ctx)
        else -> danger(ctx)
    }

    /** GPA value → status color, consistent in both themes. */
    fun gpaColor(ctx: Context, cgpa: Double): Int = when {
        cgpa >= 3.5 -> success(ctx)
        cgpa >= 2.5 -> info(ctx)
        else -> warning(ctx)
    }
}
