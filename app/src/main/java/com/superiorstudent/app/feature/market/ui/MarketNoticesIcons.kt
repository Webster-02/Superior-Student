package com.superiorstudent.app.feature.market.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

/** Small native line icons tuned to the Screen 22 notice rows. */
internal class MarketNoticesIconView(context: Context, private val glyph: MarketNoticesGlyph, tint: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint; style = Paint.Style.STROKE; strokeWidth = 1.9f
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
        when (glyph) {
            MarketNoticesGlyph.BACK -> {
                path.moveTo(19f,12f); path.lineTo(5f,12f)
                path.moveTo(5f,12f); path.lineTo(11f,6f)
                path.moveTo(5f,12f); path.lineTo(11f,18f); canvas.drawPath(path,paint)
            }
            MarketNoticesGlyph.ALERT -> {
                path.moveTo(12f,3f); path.lineTo(22f,20f); path.lineTo(2f,20f); path.close()
                path.moveTo(12f,9f); path.lineTo(12f,14f)
                canvas.drawPath(path,paint); canvas.drawCircle(12f,17f,0.65f,paint)
            }
            MarketNoticesGlyph.MAIL -> {
                path.moveTo(4f,6f); path.lineTo(20f,6f); path.quadTo(21f,6f,21f,7f)
                path.lineTo(21f,18f); path.quadTo(21f,19f,20f,19f); path.lineTo(4f,19f)
                path.quadTo(3f,19f,3f,18f); path.lineTo(3f,7f); path.quadTo(3f,6f,4f,6f)
                path.moveTo(3.5f,7f); path.lineTo(12f,13f); path.lineTo(20.5f,7f); canvas.drawPath(path,paint)
            }
            MarketNoticesGlyph.SPEAKER -> {
                path.moveTo(3f,10f); path.lineTo(8f,10f); path.lineTo(16f,6f)
                path.lineTo(16f,18f); path.lineTo(8f,14f); path.lineTo(3f,14f); path.close()
                path.moveTo(18f,9f); path.quadTo(22f,12f,18f,15f); canvas.drawPath(path,paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
