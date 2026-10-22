package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketFaqGlyph {
    BACK, CHEVRON_RIGHT, CHEVRON_DOWN
}

/** Compact native vector icons sized to match the FAQ reference without raster assets. */
internal class MarketFaqIconView(
    context: Context,
    private val glyph: MarketFaqGlyph,
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
            MarketFaqGlyph.BACK -> {
                path.moveTo(19f, 12f)
                path.lineTo(5f, 12f)
                path.moveTo(5f, 12f)
                path.lineTo(11f, 6f)
                path.moveTo(5f, 12f)
                path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketFaqGlyph.CHEVRON_RIGHT -> {
                path.moveTo(9f, 6f)
                path.lineTo(15f, 12f)
                path.lineTo(9f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketFaqGlyph.CHEVRON_DOWN -> {
                path.moveTo(6f, 9f)
                path.lineTo(12f, 15f)
                path.lineTo(18f, 9f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
