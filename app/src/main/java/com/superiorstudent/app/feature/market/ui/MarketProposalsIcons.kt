package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketProposalsIcon { BACK, STAR }

internal class MarketProposalsIconView(
    context: Context,
    private val icon: MarketProposalsIcon,
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
        when (icon) {
            MarketProposalsIcon.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketProposalsIcon.STAR -> {
                path.moveTo(12f, 3f); path.lineTo(14.8f, 8.7f)
                path.lineTo(21f, 9.6f); path.lineTo(16.5f, 14f)
                path.lineTo(17.6f, 20.4f); path.lineTo(12f, 17.4f)
                path.lineTo(6.4f, 20.4f); path.lineTo(7.5f, 14f)
                path.lineTo(3f, 9.6f); path.lineTo(9.2f, 8.7f)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
