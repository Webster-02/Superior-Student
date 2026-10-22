package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketRulesGlyph {
    BACK, CHECK, CROSS
}

/** Small native vector icons used by the rules screen. */
internal class MarketRulesIconView(
    context: Context,
    private val glyph: MarketRulesGlyph,
    tint: Int
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = if (glyph == MarketRulesGlyph.CHECK || glyph == MarketRulesGlyph.CROSS) 2.2f else 1.8f
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
            MarketRulesGlyph.BACK -> {
                path.moveTo(19f, 12f)
                path.lineTo(5f, 12f)
                path.moveTo(5f, 12f)
                path.lineTo(11f, 6f)
                path.moveTo(5f, 12f)
                path.lineTo(11f, 18f)
            }
            MarketRulesGlyph.CHECK -> {
                path.moveTo(5f, 12f)
                path.lineTo(10f, 17f)
                path.lineTo(20f, 7f)
            }
            MarketRulesGlyph.CROSS -> {
                path.moveTo(6f, 6f)
                path.lineTo(18f, 18f)
                path.moveTo(18f, 6f)
                path.lineTo(6f, 18f)
            }
        }
        canvas.drawPath(path, paint)
        canvas.restoreToCount(saved)
    }
}
