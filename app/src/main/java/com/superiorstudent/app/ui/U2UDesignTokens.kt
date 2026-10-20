package com.superiorstudent.app.ui

import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.superiorstudent.app.R

/** Shared typography and geometry tokens for U2U. Colour values remain in Palette/XML resources. */
enum class U2UTextRole { TITLE, SECTION, BODY, CAPTION, LABEL }

data class U2UTypographyToken(
    val sizeSp: Float,
    val lineHeightSp: Float,
    val medium: Boolean,
    val letterSpacingEm: Float
)

object U2UDesignTokens {
    const val GRID_UNIT_DP = 4
    const val ICON_SIZE_DP = 24
    const val ICON_STROKE_DP = 1.8f
    const val RADIUS_ICON_DP = 12
    const val RADIUS_CONTROL_DP = 16
    const val RADIUS_CARD_DP = 24
    const val RADIUS_HERO_DP = 28

    val title = U2UTypographyToken(22f, 28f, true, -0.02f)
    val section = U2UTypographyToken(18f, 24f, true, -0.01f)
    val body = U2UTypographyToken(14f, 20f, false, 0f)
    val caption = U2UTypographyToken(12f, 17f, false, 0f)
    val label = U2UTypographyToken(11f, 16f, true, 0.01f)

    fun token(role: U2UTextRole): U2UTypographyToken = when (role) {
        U2UTextRole.TITLE -> title
        U2UTextRole.SECTION -> section
        U2UTextRole.BODY -> body
        U2UTextRole.CAPTION -> caption
        U2UTextRole.LABEL -> label
    }

    /** Deterministic migration rule for views that previously had individually hard-coded sizes. */
    fun roleForSize(sizeSp: Float): U2UTextRole = when {
        sizeSp >= 20f -> U2UTextRole.TITLE
        sizeSp >= 16f -> U2UTextRole.SECTION
        sizeSp >= 13f -> U2UTextRole.BODY
        sizeSp >= 10.5f -> U2UTextRole.CAPTION
        else -> U2UTextRole.LABEL
    }

    fun applyTextRole(view: TextView, role: U2UTextRole, mediumOverride: Boolean? = null) {
        val spec = token(role)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, spec.sizeSp)
        view.includeFontPadding = false
        val medium = mediumOverride ?: spec.medium
        view.typeface = ResourcesCompat.getFont(
            view.context,
            if (medium) R.font.poppins_medium else R.font.poppins_regular
        ) ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        view.letterSpacing = spec.letterSpacingEm
        view.setLineSpacing(
            ((spec.lineHeightSp - spec.sizeSp) * view.resources.displayMetrics.scaledDensity).coerceAtLeast(0f),
            1f
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && view.maxLines > 1) {
            view.breakStrategy = Layout.BREAK_STRATEGY_BALANCED
            view.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
        }
    }

    /** Applies bundled fonts and semantic roles to existing XML and runtime-created text views. */
    fun applyTypographyTree(root: View) {
        if (root is TextView) applyLegacyTextStyle(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) applyTypographyTree(root.getChildAt(index))
        }
    }

    fun applyLegacyTextStyle(view: TextView, sourceSizeSp: Float? = null, mediumOverride: Boolean? = null) {
        val currentSize = sourceSizeSp ?: (view.textSize / view.resources.displayMetrics.scaledDensity)
        val value = view.text?.toString().orEmpty()
        val params = view.layoutParams
        val density = view.resources.displayMetrics.density
        val compactBadge = currentSize < 10.5f && params != null &&
            params.width > 0 && params.height > 0 &&
            params.width / density <= 30f && params.height / density <= 24f
        val oneOrTwoSymbol = value.length in 1..2 && value.none { it.isLetterOrDigit() }
        val medium = mediumOverride ?: (view.typeface?.isBold == true)

        if (view is EditText) {
            view.includeFontPadding = false
            view.typeface = ResourcesCompat.getFont(
                view.context, if (medium) R.font.poppins_medium else R.font.poppins_regular
            )
            return
        }
        // Preserve large numerical displays and compact badge sizing, while still using Poppins.
        if (currentSize >= 30f || compactBadge || oneOrTwoSymbol) {
            view.includeFontPadding = false
            view.typeface = ResourcesCompat.getFont(
                view.context, if (medium) R.font.poppins_medium else R.font.poppins_regular
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && view.maxLines > 1) {
                view.breakStrategy = Layout.BREAK_STRATEGY_BALANCED
                view.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
            }
            return
        }
        applyTextRole(view, roleForSize(currentSize), mediumOverride = medium)
    }

    /** Keeps existing Home cards/view IDs and prevents long tile headings wrapping into three lines. */
    fun applyHomeTileRules(root: View) {
        val cardIds = listOf(
            R.id.studentConnectHomeFindCard,
            R.id.studentConnectHomeRequestsCard,
            R.id.studentConnectHomeConnectionsCard,
            R.id.studentConnectHomeProfileCard
        )
        cardIds.forEach { id ->
            val card = root.findViewById<ViewGroup>(id) ?: return@forEach
            val descendants = mutableListOf<TextView>()
            collectTextViews(card, descendants)
            val title = descendants.firstOrNull { it.text?.isNotBlank() == true } ?: return@forEach
            applyTextRole(title, U2UTextRole.CAPTION, mediumOverride = true)
            title.maxLines = 1
            title.isSingleLine = true
            title.ellipsize = TextUtils.TruncateAt.END
            descendants.dropWhile { it !== title }.drop(1)
                .firstOrNull { it.text?.isNotBlank() == true }?.let { subtitle ->
                    applyTextRole(subtitle, U2UTextRole.LABEL)
                    subtitle.maxLines = 2
                    subtitle.isSingleLine = false
                    subtitle.ellipsize = TextUtils.TruncateAt.END
                }
        }
    }

    private fun collectTextViews(root: View, into: MutableList<TextView>) {
        if (root is TextView) into += root
        if (root is ViewGroup) for (index in 0 until root.childCount) collectTextViews(root.getChildAt(index), into)
    }
}
