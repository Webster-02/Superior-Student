package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketTaskChatIcon { BACK, FLAG, BRIEFCASE, PLUS, SEND, DOUBLE_CHECK }

internal class MarketTaskChatIconView(
    context: Context,
    private val icon: MarketTaskChatIcon,
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
            MarketTaskChatIcon.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketTaskChatIcon.FLAG -> {
                path.moveTo(5f, 21f); path.lineTo(5f, 4f)
                path.moveTo(5f, 4f); path.lineTo(19f, 4f)
                path.lineTo(16.5f, 9f); path.lineTo(19f, 14f)
                path.lineTo(5f, 14f)
                canvas.drawPath(path, paint)
            }
            MarketTaskChatIcon.BRIEFCASE -> {
                path.addRoundRect(3f, 7f, 21f, 20f, 2f, 2f, Path.Direction.CW)
                path.moveTo(8f, 7f); path.lineTo(8f, 5f)
                path.quadTo(8f, 3f, 10f, 3f); path.lineTo(14f, 3f)
                path.quadTo(16f, 3f, 16f, 5f); path.lineTo(16f, 7f)
                path.moveTo(3f, 12f); path.lineTo(21f, 12f)
                canvas.drawPath(path, paint)
            }
            MarketTaskChatIcon.PLUS -> {
                path.moveTo(12f, 5f); path.lineTo(12f, 19f)
                path.moveTo(5f, 12f); path.lineTo(19f, 12f)
                canvas.drawPath(path, paint)
            }
            MarketTaskChatIcon.SEND -> {
                path.moveTo(21f, 3f); path.lineTo(10f, 14f)
                path.moveTo(21f, 3f); path.lineTo(14f, 21f)
                path.quadTo(13.5f, 22f, 13f, 21f)
                path.lineTo(10f, 14f); path.lineTo(3f, 11f)
                path.quadTo(2f, 10.5f, 3f, 10f); path.close()
                canvas.drawPath(path, paint)
            }
            MarketTaskChatIcon.DOUBLE_CHECK -> {
                path.moveTo(2f, 12f); path.lineTo(7f, 17f); path.lineTo(16f, 7f)
                path.moveTo(9f, 12f); path.lineTo(12f, 15f); path.lineTo(22f, 5f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
