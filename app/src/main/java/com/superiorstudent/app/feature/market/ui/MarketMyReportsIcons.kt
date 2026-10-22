package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketMyReportsGlyph { BACK, FLAG, INFO }

/** Native line icons used by Screen 30. */
internal class MarketMyReportsIconView(
    context: Context,
    private val glyph: MarketMyReportsGlyph,
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
            MarketMyReportsGlyph.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketMyReportsGlyph.FLAG -> {
                path.moveTo(5f, 21f); path.lineTo(5f, 4f)
                path.lineTo(18f, 4f); path.lineTo(16f, 8f)
                path.lineTo(18f, 12f); path.lineTo(5f, 12f)
                canvas.drawPath(path, paint)
            }
            MarketMyReportsGlyph.INFO -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                canvas.drawCircle(12f, 8f, 0.7f, paint)
                path.moveTo(11f, 11f); path.lineTo(12f, 11f)
                path.lineTo(12f, 16f); path.lineTo(13f, 16f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
