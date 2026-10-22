package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketSuspendedGlyph { BACK, WARNING, CHECK, CROSS, RULES, FLAG }

internal class MarketSuspendedIconView(
    context: Context,
    private val glyph: MarketSuspendedGlyph,
    tint: Int
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = 1.9f
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
            MarketSuspendedGlyph.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketSuspendedGlyph.WARNING -> {
                path.moveTo(12f, 3f); path.lineTo(22f, 20f); path.lineTo(2f, 20f); path.close()
                path.moveTo(12f, 9f); path.lineTo(12f, 14f)
                canvas.drawPath(path, paint)
                canvas.drawCircle(12f, 17f, 0.65f, paint)
            }
            MarketSuspendedGlyph.CHECK -> {
                path.moveTo(4f, 12f); path.lineTo(9f, 17f); path.lineTo(20f, 6f)
                canvas.drawPath(path, paint)
            }
            MarketSuspendedGlyph.CROSS -> {
                path.moveTo(6f, 6f); path.lineTo(18f, 18f)
                path.moveTo(18f, 6f); path.lineTo(6f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketSuspendedGlyph.RULES -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = 15f
                canvas.drawText("?", 8.4f, 17f, paint)
                paint.style = Paint.Style.STROKE
            }
            MarketSuspendedGlyph.FLAG -> {
                path.moveTo(5f, 21f); path.lineTo(5f, 3f)
                path.lineTo(19f, 3f); path.lineTo(16f, 8f)
                path.lineTo(19f, 13f); path.lineTo(5f, 13f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
