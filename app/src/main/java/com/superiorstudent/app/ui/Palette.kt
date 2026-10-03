package com.superiorstudent.app.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.android.material.R as MaterialR

/**
 * Central palette accessor so programmatic views (built in Kotlin) always use the
 * "Midnight Indigo" design tokens instead of hard-coded hex values.
 * Colors are defined in values/colors.xml and automatically swap in values-night.
 */
object Palette {
    private fun c(ctx: Context, name: String): Int =
        ContextCompat.getColor(ctx, getResId(ctx, name))

    private fun getResId(ctx: Context, name: String): Int {
        val id = ctx.resources.getIdentifier(name, "color", ctx.packageName)
        check(id != 0) { "Color resource '$name' not found" }
        return id
    }

    fun primary(ctx: Context) = c(ctx, "brand_primary")
    fun primaryDark(ctx: Context) = c(ctx, "brand_primary_dark")
    fun primaryLight(ctx: Context) = c(ctx, "brand_primary_light")
    fun accent(ctx: Context) = c(ctx, "brand_accent")
    fun accentDeep(ctx: Context) = c(ctx, "brand_accent_deep")

    fun surface(ctx: Context) = c(ctx, "surface")
    fun surfaceAlt(ctx: Context) = c(ctx, "surface_alt")
    fun stroke(ctx: Context) = c(ctx, "surface_stroke")

    fun ink900(ctx: Context) = c(ctx, "ink_900")
    fun ink700(ctx: Context) = c(ctx, "ink_700")
    fun ink500(ctx: Context) = c(ctx, "ink_500")
    fun ink400(ctx: Context) = c(ctx, "ink_400")

    fun success(ctx: Context) = c(ctx, "success")
    fun successBg(ctx: Context) = c(ctx, "success_bg")
    fun warning(ctx: Context) = c(ctx, "warning")
    fun warningBg(ctx: Context) = c(ctx, "warning_bg")
    fun danger(ctx: Context) = c(ctx, "danger")
    fun dangerBg(ctx: Context) = c(ctx, "danger_bg")
    fun info(ctx: Context) = c(ctx, "info")
    fun infoBg(ctx: Context) = c(ctx, "info_bg")
    fun violet(ctx: Context) = c(ctx, "violet")
    fun violetBg(ctx: Context) = c(ctx, "violet_bg")

    fun ringTrack(ctx: Context) = c(ctx, "ring_track")

    /** Attendance percentage → brand-consistent status color. */
    fun attendanceColor(ctx: Context, percent: Double): Int = when {
        percent >= 85.0 -> success(ctx)
        percent >= 75.0 -> warning(ctx)
        else -> danger(ctx)
    }

    /** GPA value → brand-consistent status color. */
    fun gpaColor(ctx: Context, cgpa: Double): Int = when {
        cgpa >= 3.5 -> success(ctx)
        cgpa >= 2.5 -> info(ctx)
        else -> warning(ctx)
    }
}
