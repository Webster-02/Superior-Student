package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

private enum class PostTaskIcon { BACK, SEARCH, NEXT }

private class PostTaskIconView(
    context: Context,
    private val icon: PostTaskIcon,
    private val tint: Int
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
            PostTaskIcon.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            PostTaskIcon.SEARCH -> {
                canvas.drawCircle(10f, 10f, 7f, paint)
                path.moveTo(15f, 15f); path.lineTo(21f, 21f)
                canvas.drawPath(path, paint)
            }
            PostTaskIcon.NEXT -> {
                path.moveTo(4f, 12f); path.lineTo(20f, 12f)
                path.moveTo(13f, 5f); path.lineTo(20f, 12f); path.lineTo(13f, 19f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
