package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketStatesIcon { OFFLINE, INBOX, REFRESH }

internal class MarketStatesIconView(
    context: Context,
    private val icon: MarketStatesIcon,
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
            MarketStatesIcon.OFFLINE -> {
                path.moveTo(3f, 3f); path.lineTo(21f, 21f)
                path.moveTo(9.2f, 15.2f); path.quadTo(12f, 13f, 14.8f, 15.2f)
                path.moveTo(6.5f, 11.5f); path.quadTo(7.8f, 10.5f, 9f, 10f)
                path.moveTo(15f, 10f); path.quadTo(16.5f, 10.5f, 17.5f, 11.5f)
                path.moveTo(3.5f, 8.5f); path.quadTo(5f, 7f, 6.3f, 6.5f)
                path.moveTo(17.7f, 6.5f); path.quadTo(19f, 7f, 20.5f, 8.5f)
                canvas.drawPath(path, paint)
                canvas.drawCircle(12f, 19f, 0.7f, paint)
            }
            MarketStatesIcon.INBOX -> {
                path.moveTo(4f, 13f); path.lineTo(6f, 5f); path.lineTo(18f, 5f)
                path.lineTo(20f, 13f); path.lineTo(20f, 19f); path.lineTo(4f, 19f); path.close()
                path.moveTo(4f, 13f); path.lineTo(9f, 13f); path.lineTo(10f, 15f)
                path.lineTo(14f, 15f); path.lineTo(15f, 13f); path.lineTo(20f, 13f)
                canvas.drawPath(path, paint)
            }
            MarketStatesIcon.REFRESH -> {
                path.moveTo(20f, 11f); path.quadTo(19f, 4f, 12f, 4f)
                path.quadTo(7f, 4f, 5f, 8f); path.moveTo(4f, 4f); path.lineTo(5f, 8f); path.lineTo(9f, 8f)
                path.moveTo(4f, 13f); path.quadTo(5f, 20f, 12f, 20f)
                path.quadTo(17f, 20f, 19f, 16f); path.moveTo(20f, 20f); path.lineTo(19f, 16f); path.lineTo(15f, 16f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
