package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class ProposalScreenIcon { BACK, SEND, INFO }

internal class ProposalScreenIconView(
    context: Context,
    private val icon: ProposalScreenIcon,
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
            ProposalScreenIcon.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            ProposalScreenIcon.SEND -> {
                path.moveTo(10f, 14f); path.lineTo(21f, 3f)
                path.lineTo(14.5f, 21f)
                path.quadTo(14f, 22f, 13.4f, 21f)
                path.lineTo(10f, 14f)
                path.lineTo(3f, 10.5f)
                path.quadTo(2f, 10f, 3f, 9.5f)
                path.lineTo(21f, 3f)
                canvas.drawPath(path, paint)
            }
            ProposalScreenIcon.INFO -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                canvas.drawCircle(12f, 7.5f, 0.75f, paint)
                path.moveTo(11f, 11f); path.lineTo(12f, 11f); path.lineTo(12f, 16.5f)
                path.moveTo(11f, 16.5f); path.lineTo(13f, 16.5f)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
