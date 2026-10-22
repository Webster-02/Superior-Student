package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

internal enum class MarketOfferDetailIcon { BACK, FLAG, STAR, SAVE, MESSAGE, INFO }

internal class MarketOfferDetailIconView(
    context: Context,
    private val icon: MarketOfferDetailIcon,
    tint: Int
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
            MarketOfferDetailIcon.BACK -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f)
                path.moveTo(5f, 12f); path.lineTo(11f, 6f)
                path.moveTo(5f, 12f); path.lineTo(11f, 18f)
                canvas.drawPath(path, paint)
            }
            MarketOfferDetailIcon.FLAG -> {
                path.moveTo(5f, 21f); path.lineTo(5f, 4f)
                path.moveTo(5f, 4f); path.lineTo(19f, 4f)
                path.lineTo(16.5f, 9f); path.lineTo(19f, 14f); path.lineTo(5f, 14f)
                canvas.drawPath(path, paint)
            }
            MarketOfferDetailIcon.STAR -> {
                path.moveTo(12f, 3f); path.lineTo(14.8f, 8.7f)
                path.lineTo(21f, 9.6f); path.lineTo(16.5f, 14f)
                path.lineTo(17.6f, 20.4f); path.lineTo(12f, 17.4f)
                path.lineTo(6.4f, 20.4f); path.lineTo(7.5f, 14f)
                path.lineTo(3f, 9.6f); path.lineTo(9.2f, 8.7f); path.close()
                canvas.drawPath(path, paint)
            }
            MarketOfferDetailIcon.SAVE -> {
                path.moveTo(7f, 3f); path.lineTo(17f, 3f)
                path.quadTo(19f, 3f, 19f, 5f); path.lineTo(19f, 21f)
                path.lineTo(12f, 16.5f); path.lineTo(5f, 21f); path.lineTo(5f, 5f)
                path.quadTo(5f, 3f, 7f, 3f); path.close()
                canvas.drawPath(path, paint)
            }
            MarketOfferDetailIcon.MESSAGE -> {
                path.addRoundRect(3f, 3f, 21f, 17f, 6f, 6f, Path.Direction.CW)
                path.moveTo(8f, 17f); path.lineTo(4f, 21f); path.lineTo(5f, 16f)
                canvas.drawPath(path, paint)
            }
            MarketOfferDetailIcon.INFO -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                paint.style = Paint.Style.FILL; canvas.drawCircle(12f, 7.5f, 1.1f, paint)
                paint.style = Paint.Style.STROKE
                path.moveTo(12f, 11f); path.lineTo(12f, 16f); canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
