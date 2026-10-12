package com.presence.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Circular countdown ring. fraction 1 = full, 0 = empty. */
class RingView(ctx: Context) : View(ctx) {
    var fraction: Float = 1f
        set(v) {
            field = v
            invalidate()
        }
    var ringColor: Int = Ui.BLUE
        set(v) {
            field = v
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init {
        track.style = Paint.Style.STROKE
        arc.style = Paint.Style.STROKE
        arc.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = Ui.dp(context, 16).toFloat()
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        track.color = Ui.SOFT
        arc.color = ringColor
        val half = stroke / 2f + 2f
        box.set(half, half, width.toFloat() - half, height.toFloat() - half)
        canvas.drawOval(box, track)
        val f = Math.max(0f, Math.min(1f, fraction))
        if (f > 0f) canvas.drawArc(box, -90f, 360f * f, false, arc)
    }
}
