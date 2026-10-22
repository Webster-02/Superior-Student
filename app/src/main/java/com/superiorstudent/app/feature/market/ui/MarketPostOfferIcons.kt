package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketPostOfferIcon { BACK, PHOTO, CHECK }

internal class MarketPostOfferIconView(
    context: Context,
    private val icon: MarketPostOfferIcon,
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
            MarketPostOfferIcon.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketPostOfferIcon.PHOTO -> {
                canvas.drawRoundRect(3f, 3f, 21f, 21f, 2f, 2f, paint)
                canvas.drawCircle(9f, 9f, 1.6f, paint)
                path.moveTo(4f, 17f); path.lineTo(9f, 12f)
                path.lineTo(12.5f, 15f); path.lineTo(16f, 10.5f)
                path.lineTo(20f, 15f); canvas.drawPath(path, paint)
            }
            MarketPostOfferIcon.CHECK -> {
                path.moveTo(4f, 12f); path.lineTo(9.5f, 17f)
                path.lineTo(20f, 6.5f); canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
