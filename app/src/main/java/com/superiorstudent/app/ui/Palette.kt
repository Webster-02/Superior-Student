package com.superiorstudent.app.ui

import android.content.Context
import android.graphics.Color

/**
 * Pachattar% semantic colour tokens, mapped to tokens.css.
 *
 * Paper/Night selection is resolved in Kotlin so it works independently of device appearance.
 * XML color resources mirror these values for layouts and framework components.
 * Existing accessor names stay available during the screen-by-screen redesign.
 */
object Palette {
    private data class Tokens(
        val bg: Int,
        val card: Int,
        val ink: Int,
        val ink2: Int,
        val ink3: Int,
        val line: Int,
        val hero: Int,
        val heroInk: Int,
        val heroFill: Int,
        val heroTrack: Int,
        val lime: Int,
        val limeInk: Int,
        val lav: Int,
        val peach: Int,
        val sky: Int,
        val mint: Int,
        val nav: Int,
        val navIc: Int,
        val ok: Int,
        val okBg: Int,
        val okFill: Int,
        val warn: Int,
        val warnBg: Int,
        val warnFill: Int,
        val bad: Int,
        val badBg: Int,
        val badFill: Int,
        val seg: Int,
        val segInk: Int
    )

    private fun hex(value: String): Int = Color.parseColor(value)

    private val paper = Tokens(
        bg = hex("#F4F1EA"),
        card = hex("#FFFFFF"),
        ink = hex("#14201A"),
        ink2 = hex("#5F6B64"),
        ink3 = hex("#8A948D"),
        line = hex("#1714201A"),
        hero = hex("#0B3D2E"),
        heroInk = hex("#E8EFE3"),
        heroFill = hex("#D4F34A"),
        heroTrack = hex("#2EFFFFFF"),
        lime = hex("#D4F34A"),
        limeInk = hex("#14201A"),
        lav = hex("#E4DDFB"),
        peach = hex("#FFD9C7"),
        sky = hex("#D3E7FB"),
        mint = hex("#D8F0DE"),
        nav = hex("#14201A"),
        navIc = hex("#9AA89F"),
        ok = hex("#1F7A45"),
        okBg = hex("#DDF3E4"),
        okFill = hex("#2FB36A"),
        warn = hex("#8A5A00"),
        warnBg = hex("#FBEBC8"),
        warnFill = hex("#F0A92A"),
        bad = hex("#B42F2F"),
        badBg = hex("#FBDEDE"),
        badFill = hex("#E25555"),
        seg = hex("#14201A"),
        segInk = hex("#D4F34A")
    )

    private val night = Tokens(
        bg = hex("#0D1410"),
        card = hex("#18211C"),
        ink = hex("#F1F5F0"),
        ink2 = hex("#93A096"),
        ink3 = hex("#6E7B72"),
        line = hex("#14FFFFFF"),
        hero = hex("#D4F34A"),
        heroInk = hex("#1A2A10"),
        heroFill = hex("#14201A"),
        heroTrack = hex("#3314201A"),
        lime = hex("#D4F34A"),
        limeInk = hex("#14201A"),
        lav = hex("#2A2542"),
        peach = hex("#3A2A22"),
        sky = hex("#1E2F40"),
        mint = hex("#1D3326"),
        nav = hex("#1B2620"),
        navIc = hex("#7E8C83"),
        ok = hex("#4ADE80"),
        okBg = hex("#244ADE80"),
        okFill = hex("#4ADE80"),
        warn = hex("#FBBF24"),
        warnBg = hex("#24FBBF24"),
        warnFill = hex("#FBBF24"),
        bad = hex("#F87171"),
        badBg = hex("#26F87171"),
        badFill = hex("#F87171"),
        seg = hex("#D4F34A"),
        segInk = hex("#14201A")
    )

    private fun tokens(context: Context): Tokens =
        if (AppThemePreferences.isNight(context)) night else paper

    fun isNight(context: Context): Boolean = AppThemePreferences.isNight(context)

    // Exact semantic token accessors for new native screens.
    fun bg(context: Context) = tokens(context).bg
    fun card(context: Context) = tokens(context).card
    fun ink(context: Context) = tokens(context).ink
    fun ink2(context: Context) = tokens(context).ink2
    fun ink3(context: Context) = tokens(context).ink3
    fun line(context: Context) = tokens(context).line
    fun hero(context: Context) = tokens(context).hero
    fun heroInk(context: Context) = tokens(context).heroInk
    fun heroFill(context: Context) = tokens(context).heroFill
    fun heroTrack(context: Context) = tokens(context).heroTrack
    fun lime(context: Context) = tokens(context).lime
    fun limeInk(context: Context) = tokens(context).limeInk
    fun lav(context: Context) = tokens(context).lav
    fun peach(context: Context) = tokens(context).peach
    fun sky(context: Context) = tokens(context).sky
    fun mint(context: Context) = tokens(context).mint
    fun nav(context: Context) = tokens(context).nav
    fun navIcon(context: Context) = tokens(context).navIc
    fun ok(context: Context) = tokens(context).ok
    fun okBg(context: Context) = tokens(context).okBg
    fun okFill(context: Context) = tokens(context).okFill
    fun warn(context: Context) = tokens(context).warn
    fun warnBg(context: Context) = tokens(context).warnBg
    fun warnFill(context: Context) = tokens(context).warnFill
    fun bad(context: Context) = tokens(context).bad
    fun badBg(context: Context) = tokens(context).badBg
    fun badFill(context: Context) = tokens(context).badFill
    fun segment(context: Context) = tokens(context).seg
    fun segmentInk(context: Context) = tokens(context).segInk

    // Compatibility aliases used by existing renderers until each screen is restyled.
    fun primary(context: Context) = lime(context)
    fun primaryDark(context: Context) = hero(context)
    fun primaryLight(context: Context) = lime(context)
    fun accent(context: Context) = lime(context)
    fun accentDeep(context: Context) = hero(context)
    fun surface(context: Context) = card(context)
    fun surfaceAlt(context: Context) = bg(context)
    fun stroke(context: Context) = line(context)
    fun ink900(context: Context) = ink(context)
    fun ink700(context: Context) = ink2(context)
    fun ink500(context: Context) = ink2(context)
    fun ink400(context: Context) = ink3(context)
    fun success(context: Context) = ok(context)
    fun successBg(context: Context) = okBg(context)
    fun warning(context: Context) = warn(context)
    fun warningBg(context: Context) = warnBg(context)
    fun danger(context: Context) = bad(context)
    fun dangerBg(context: Context) = badBg(context)
    fun info(context: Context) = hero(context)
    fun infoBg(context: Context) = sky(context)
    fun violet(context: Context) = lav(context)
    fun violetBg(context: Context) = lav(context)
    fun ringTrack(context: Context) = line(context)

    /** Visual-only attendance status thresholds. Attendance calculations remain unchanged. */
    fun attendanceColor(context: Context, percent: Double): Int = when {
        percent >= 80.0 -> ok(context)
        percent >= 75.0 -> warn(context)
        else -> bad(context)
    }

    fun gpaColor(context: Context, cgpa: Double): Int = when {
        cgpa >= 3.5 -> ok(context)
        cgpa >= 2.5 -> hero(context)
        else -> warn(context)
    }
}
