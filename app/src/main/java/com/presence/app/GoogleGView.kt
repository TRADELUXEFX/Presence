package com.presence.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** The Google "G", drawn in code: four colored arcs and the blue bar. */
class GoogleGView(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val stroke = size * 0.2f
        val r = (size - stroke) / 2f
        val cx = width / 2f
        val cy = height / 2f
        val box = RectF(cx - r, cy - r, cx + r, cy + r)
        paint.strokeWidth = stroke
        paint.color = Color.parseColor("#EA4335")   // red, top
        canvas.drawArc(box, 200f, 100f, false, paint)
        paint.color = Color.parseColor("#4285F4")   // blue, top right
        canvas.drawArc(box, 300f, 60f, false, paint)
        paint.color = Color.parseColor("#34A853")   // green, bottom
        canvas.drawArc(box, 0f, 90f, false, paint)
        paint.color = Color.parseColor("#FBBC05")   // yellow, left
        canvas.drawArc(box, 90f, 110f, false, paint)
        paint.color = Color.parseColor("#4285F4")   // blue bar
        canvas.drawLine(cx, cy, cx + r, cy, paint)
    }
}
