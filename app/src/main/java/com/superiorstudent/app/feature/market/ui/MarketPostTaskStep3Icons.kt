package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class ReviewStepIcon { BACK, CHECK, CLOCK }

internal class ReviewStepIconView(
    context: Context,
    private val icon: ReviewStepIcon,
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
            ReviewStepIcon.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            ReviewStepIcon.CHECK -> {
                path.moveTo(5f, 12f); path.lineTo(10f, 17f); path.lineTo(20f, 7f)
                canvas.drawPath(path, paint)
            }
            ReviewStepIcon.CLOCK -> {
                canvas.drawCircle(12f, 12f, 8.5f, paint)
                path.moveTo(12f, 7f); path.lineTo(12f, 12f); path.lineTo(16f, 14f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
