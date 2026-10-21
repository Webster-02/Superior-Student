package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class OfferIcon { BACK, SEARCH, MORE, FILTER, PLUS, STAR, MESSAGE }

internal class OfferIconView(
    context: Context,
    private val icon: OfferIcon,
    private val tint: Int
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint; style = Paint.Style.STROKE; strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
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
            OfferIcon.BACK -> {
                path.moveTo(19f,12f); path.lineTo(5f,12f)
                path.moveTo(5f,12f); path.lineTo(11f,6f)
                path.moveTo(5f,12f); path.lineTo(11f,18f)
                canvas.drawPath(path,paint)
            }
            OfferIcon.SEARCH -> {
                canvas.drawCircle(10f,10f,7f,paint)
                path.moveTo(15f,15f); path.lineTo(21f,21f); canvas.drawPath(path,paint)
            }
            OfferIcon.MORE -> {
                paint.style=Paint.Style.FILL
                canvas.drawCircle(5f,12f,1.45f,paint); canvas.drawCircle(12f,12f,1.45f,paint)
                canvas.drawCircle(19f,12f,1.45f,paint); paint.style=Paint.Style.STROKE
            }
            OfferIcon.FILTER -> {
                path.moveTo(4f,6f); path.lineTo(20f,6f)
                path.moveTo(7f,12f); path.lineTo(17f,12f)
                path.moveTo(10f,18f); path.lineTo(14f,18f); canvas.drawPath(path,paint)
            }
            OfferIcon.PLUS -> {
                path.moveTo(12f,5f); path.lineTo(12f,19f)
                path.moveTo(5f,12f); path.lineTo(19f,12f); canvas.drawPath(path,paint)
            }
            OfferIcon.STAR -> {
                path.moveTo(12f,3f); path.lineTo(14.8f,9f); path.lineTo(21f,9.8f)
                path.lineTo(16.5f,14f); path.lineTo(17.7f,21f); path.lineTo(12f,17.5f)
                path.lineTo(6.3f,21f); path.lineTo(7.5f,14f); path.lineTo(3f,9.8f)
                path.lineTo(9.2f,9f); path.close(); canvas.drawPath(path,paint)
            }
            OfferIcon.MESSAGE -> {
                canvas.drawRoundRect(3f,3f,21f,17f,6f,6f,paint)
                path.moveTo(7f,17f); path.lineTo(4f,21f); path.lineTo(4.5f,16.5f)
                canvas.drawPath(path,paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
