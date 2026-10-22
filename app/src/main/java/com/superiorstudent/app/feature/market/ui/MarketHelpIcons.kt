package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketHelpGlyph {
    BACK, SEARCH, POSTING_RULES, SAFETY, INFO, FLAG, CHEVRON
}

/** Small vector icons drawn natively so the screen uses no remote or raster icon assets. */
internal class MarketHelpIconView(
    context: Context,
    private val glyph: MarketHelpGlyph,
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
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        val path = Path()
        when (glyph) {
            MarketHelpGlyph.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketHelpGlyph.SEARCH -> {
                canvas.drawCircle(10f, 10f, 7f, paint)
                path.moveTo(15f, 15f); path.lineTo(21f, 21f)
                canvas.drawPath(path, paint)
            }
            MarketHelpGlyph.POSTING_RULES -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = 13f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("?", 12f, 16.5f, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.style = Paint.Style.STROKE
            }
            MarketHelpGlyph.SAFETY -> {
                path.moveTo(12f, 3f)
                path.lineTo(20f, 6f)
                path.lineTo(19f, 13f)
                path.quadTo(18f, 18.5f, 12f, 21f)
                path.quadTo(6f, 18.5f, 5f, 13f)
                path.lineTo(4f, 6f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawCircle(12f, 11f, 1.2f, paint)
                path.reset()
                path.moveTo(12f, 12.4f); path.lineTo(12f, 16f)
                canvas.drawPath(path, paint)
            }
            MarketHelpGlyph.INFO -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(12f, 7.5f, 0.8f, paint)
                paint.style = Paint.Style.STROKE
                path.moveTo(12f, 10.5f); path.lineTo(12f, 16.5f)
                canvas.drawPath(path, paint)
            }
            MarketHelpGlyph.FLAG -> {
                path.moveTo(5f, 3f); path.lineTo(5f, 21f)
                path.moveTo(6f, 4f); path.lineTo(19f, 4f)
                path.lineTo(16f, 8f); path.lineTo(19f, 12f)
                path.lineTo(6f, 12f)
                canvas.drawPath(path, paint)
            }
            MarketHelpGlyph.CHEVRON -> {
                path.moveTo(9f, 6f); path.lineTo(15f, 12f); path.lineTo(9f, 18f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
