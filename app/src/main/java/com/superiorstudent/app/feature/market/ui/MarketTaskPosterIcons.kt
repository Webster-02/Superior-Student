package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class PosterDetailIcon { EDIT, USERS, CHEVRON, CLOSE }

internal class PosterDetailIconView(
    context: Context,
    private val icon: PosterDetailIcon,
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
            PosterDetailIcon.EDIT -> {
                path.moveTo(14f, 5f); path.lineTo(19f, 10f)
                path.lineTo(8f, 21f); path.lineTo(3f, 22f); path.lineTo(4f, 17f)
                path.close()
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(12f, 7f); path.lineTo(17f, 12f)
                canvas.drawPath(path, paint)
            }
            PosterDetailIcon.USERS -> {
                canvas.drawCircle(9f, 8f, 3.5f, paint)
                canvas.drawCircle(17f, 9f, 2.7f, paint)
                path.moveTo(2f, 21f); path.quadTo(2f, 14f, 9f, 14f); path.quadTo(16f, 14f, 16f, 21f)
                path.moveTo(15f, 15f); path.quadTo(22f, 15f, 22f, 21f)
                canvas.drawPath(path, paint)
            }
            PosterDetailIcon.CHEVRON -> {
                path.moveTo(9f, 5f); path.lineTo(16f, 12f); path.lineTo(9f, 19f)
                canvas.drawPath(path, paint)
            }
            PosterDetailIcon.CLOSE -> {
                path.moveTo(5f, 5f); path.lineTo(19f, 19f)
                path.moveTo(19f, 5f); path.lineTo(5f, 19f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
