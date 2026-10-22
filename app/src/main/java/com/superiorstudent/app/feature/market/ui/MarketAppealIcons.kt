package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketAppealGlyph { BACK, INFO, SEND }

internal class MarketAppealIconView(
    context: Context,
    private val glyph: MarketAppealGlyph,
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
            MarketAppealGlyph.BACK -> {
                path.moveTo(19f,12f); path.lineTo(5f,12f)
                path.moveTo(5f,12f); path.lineTo(11f,6f)
                path.moveTo(5f,12f); path.lineTo(11f,18f)
                canvas.drawPath(path, paint)
            }
            MarketAppealGlyph.INFO -> {
                canvas.drawCircle(12f,12f,9f,paint)
                paint.style=Paint.Style.FILL
                canvas.drawCircle(12f,7.5f,0.7f,paint)
                canvas.drawRoundRect(11.1f,10f,12.9f,17f,0.8f,0.8f,paint)
                paint.style=Paint.Style.STROKE
            }
            MarketAppealGlyph.SEND -> {
                path.moveTo(10f,14f); path.lineTo(21f,3f)
                path.lineTo(14.5f,21f); path.quadTo(14f,22f,13.5f,21f)
                path.lineTo(10f,14f); path.lineTo(3f,10.5f)
                path.quadTo(2f,10f,3f,9.5f); path.lineTo(21f,3f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
