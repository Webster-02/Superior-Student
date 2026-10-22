package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketMyOffersIcon { BACK, EDIT, PAUSE, RESUME, RENEW, DELETE, PLUS }

internal class MarketMyOffersIconView(
    context: Context,
    private val icon: MarketMyOffersIcon,
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
        when (icon) {
            MarketMyOffersIcon.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketMyOffersIcon.EDIT -> {
                path.moveTo(4f, 17.5f); path.lineTo(3f, 21f); path.lineTo(6.5f, 20f)
                path.lineTo(19f, 7.5f)
                canvas.drawPath(path, paint)
                canvas.drawRoundRect(13f, 3f, 20.5f, 10.5f, 1.6f, 1.6f, paint)
            }
            MarketMyOffersIcon.PAUSE -> {
                canvas.drawRoundRect(5f, 5f, 9f, 19f, 1f, 1f, paint)
                canvas.drawRoundRect(15f, 5f, 19f, 19f, 1f, 1f, paint)
            }
            MarketMyOffersIcon.RESUME -> {
                path.moveTo(7f, 4f); path.lineTo(19f, 12f); path.lineTo(7f, 20f)
                path.close(); canvas.drawPath(path, paint)
            }
            MarketMyOffersIcon.RENEW -> {
                path.moveTo(20f, 11f); path.cubicTo(19f, 5f, 14f, 3f, 9f, 5f)
                path.cubicTo(7f, 5.8f, 6f, 7f, 5f, 9f)
                path.moveTo(4f, 5f); path.lineTo(4f, 9f); path.lineTo(8f, 9f)
                path.moveTo(4f, 13f); path.cubicTo(5f, 19f, 10f, 21f, 15f, 19f)
                path.cubicTo(17f, 18.2f, 18f, 17f, 19f, 15f)
                path.moveTo(20f, 19f); path.lineTo(20f, 15f); path.lineTo(16f, 15f)
                canvas.drawPath(path, paint)
            }
            MarketMyOffersIcon.DELETE -> {
                path.moveTo(4f, 7f); path.lineTo(20f, 7f)
                path.moveTo(9f, 7f); path.lineTo(9f, 4f); path.lineTo(15f, 4f); path.lineTo(15f, 7f)
                path.moveTo(6f, 7f); path.lineTo(7f, 20f); path.lineTo(17f, 20f); path.lineTo(18f, 7f)
                path.moveTo(10f, 11f); path.lineTo(10f, 17f)
                path.moveTo(14f, 11f); path.lineTo(14f, 17f)
                canvas.drawPath(path, paint)
            }
            MarketMyOffersIcon.PLUS -> {
                path.moveTo(12f, 5f); path.lineTo(12f, 19f)
                path.moveTo(5f, 12f); path.lineTo(19f, 12f); canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
