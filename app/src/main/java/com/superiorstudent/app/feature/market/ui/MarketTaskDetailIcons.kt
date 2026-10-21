package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class TaskDetailIcon { BACK, BOOKMARK, FLAG, STAR, LOCK, SEND }

internal class TaskDetailIconView(
    context: Context,
    private val icon: TaskDetailIcon,
    private val tint: Int
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
        val save = canvas.save()
        canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
        canvas.scale(scale, scale)
        val path = Path()
        when (icon) {
            TaskDetailIcon.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            TaskDetailIcon.BOOKMARK -> {
                path.moveTo(9f, 4f); path.lineTo(15f, 4f)
                path.quadTo(17f, 4f, 17f, 6f); path.lineTo(17f, 20f)
                path.lineTo(12f, 17f); path.lineTo(7f, 20f); path.lineTo(7f, 6f)
                path.quadTo(7f, 4f, 9f, 4f); path.close()
                canvas.drawPath(path, paint)
            }
            TaskDetailIcon.FLAG -> {
                path.moveTo(5f, 21f); path.lineTo(5f, 4f)
                path.lineTo(19f, 4f); path.lineTo(16f, 8f)
                path.lineTo(19f, 12f); path.lineTo(5f, 12f)
                canvas.drawPath(path, paint)
            }
            TaskDetailIcon.STAR -> {
                path.moveTo(12f, 3f); path.lineTo(15f, 9f); path.lineTo(21f, 10f)
                path.lineTo(16.5f, 14f); path.lineTo(17.5f, 21f); path.lineTo(12f, 17.5f)
                path.lineTo(6.5f, 21f); path.lineTo(7.5f, 14f); path.lineTo(3f, 10f)
                path.lineTo(9f, 9f); path.close()
                canvas.drawPath(path, paint)
            }
            TaskDetailIcon.LOCK -> {
                canvas.drawRoundRect(5f, 11f, 19f, 21f, 2f, 2f, paint)
                path.moveTo(8f, 11f); path.lineTo(8f, 7f)
                path.quadTo(8f, 3f, 12f, 3f); path.quadTo(16f, 3f, 16f, 7f)
                path.lineTo(16f, 11f); canvas.drawPath(path, paint)
                canvas.drawCircle(12f, 15f, 1f, paint)
            }
            TaskDetailIcon.SEND -> {
                path.moveTo(3f, 11f); path.lineTo(21f, 3f); path.lineTo(14f, 21f)
                path.lineTo(10f, 14f); path.close()
                path.moveTo(10f, 14f); path.lineTo(21f, 3f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(save)
    }
}
