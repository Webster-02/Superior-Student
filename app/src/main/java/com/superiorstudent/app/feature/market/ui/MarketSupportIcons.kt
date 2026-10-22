package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

internal enum class MarketSupportGlyph {
    BACK, BRIEFCASE, CHEVRON_RIGHT, SHIELD, SEND
}

/** Native vector icons for the Contact support screen. */
internal class MarketSupportIconView(
    context: Context,
    private val glyph: MarketSupportGlyph,
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
            MarketSupportGlyph.BACK -> {
                path.moveTo(19f, 12f)
                path.lineTo(5f, 12f)
                path.moveTo(5f, 12f)
                path.lineTo(11f, 6f)
                path.moveTo(5f, 12f)
                path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketSupportGlyph.BRIEFCASE -> {
                canvas.drawRoundRect(RectF(3f, 7f, 21f, 20f), 2f, 2f, paint)
                path.moveTo(8f, 7f)
                path.lineTo(8f, 5f)
                path.quadTo(8f, 3.5f, 10f, 3.5f)
                path.lineTo(14f, 3.5f)
                path.quadTo(16f, 3.5f, 16f, 5f)
                path.lineTo(16f, 7f)
                path.moveTo(3f, 13f)
                path.lineTo(21f, 13f)
                canvas.drawPath(path, paint)
            }
            MarketSupportGlyph.CHEVRON_RIGHT -> {
                path.moveTo(9f, 6f)
                path.lineTo(15f, 12f)
                path.lineTo(9f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketSupportGlyph.SHIELD -> {
                path.moveTo(12f, 3f)
                path.cubicTo(15f, 5f, 17f, 6f, 20f, 6f)
                path.cubicTo(20f, 13f, 17f, 19f, 12f, 21f)
                path.cubicTo(7f, 19f, 4f, 13f, 4f, 6f)
                path.cubicTo(7f, 6f, 9f, 5f, 12f, 3f)
                canvas.drawPath(path, paint)
                canvas.drawCircle(12f, 10.5f, 1f, paint)
                path.moveTo(12f, 12f)
                path.lineTo(12f, 14.5f)
                canvas.drawPath(path, paint)
            }
            MarketSupportGlyph.SEND -> {
                path.moveTo(10f, 14f)
                path.lineTo(21f, 3f)
                path.lineTo(14.5f, 20.5f)
                path.quadTo(14f, 21.5f, 13.4f, 20.5f)
                path.lineTo(10f, 14f)
                path.lineTo(3f, 10.5f)
                path.quadTo(2f, 10f, 3f, 9.5f)
                path.lineTo(21f, 3f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
