package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketSettingsGlyph { BACK, BLOCKED, HELP, FLAG, CHEVRON }

/** Small vector icons drawn locally so the screen needs no additional image assets. */
internal class MarketSettingsIconView(
    context: Context,
    private val glyph: MarketSettingsGlyph,
    tint: Int
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width, height) / 24f
        if (scale <= 0f) return
        val saved = canvas.save()
        canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
        canvas.scale(scale, scale)
        val path = Path()
        when (glyph) {
            MarketSettingsGlyph.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketSettingsGlyph.BLOCKED -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                path.moveTo(5.7f, 5.7f); path.lineTo(18.3f, 18.3f)
                canvas.drawPath(path, paint)
            }
            MarketSettingsGlyph.HELP -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                path.moveTo(9.5f, 9f)
                path.cubicTo(9.5f, 6.3f, 14.8f, 6.3f, 14.8f, 9.5f)
                path.cubicTo(14.8f, 11.4f, 12f, 11.4f, 12f, 14f)
                canvas.drawPath(path, paint)
                canvas.drawCircle(12f, 17.3f, 0.55f, paint)
            }
            MarketSettingsGlyph.FLAG -> {
                path.moveTo(5f, 21f); path.lineTo(5f, 4f)
                path.lineTo(18f, 4f); path.lineTo(16f, 8f)
                path.lineTo(18f, 12f); path.lineTo(5f, 12f)
                canvas.drawPath(path, paint)
            }
            MarketSettingsGlyph.CHEVRON -> {
                path.moveTo(9f, 6f); path.lineTo(15f, 12f); path.lineTo(9f, 18f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
