package com.presence.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.PathParser

/** A white line icon (24 x 24 outline path) for the full-screen buttons. */
class FsIconView(ctx: Context, d: String, color: Int = Color.WHITE) : View(ctx) {
    private val path = PathParser.createPathFromPathData(d)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        p.style = Paint.Style.STROKE
        p.color = color
        p.strokeWidth = 2.2f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
    }

    fun setColor(color: Int) {
        p.color = color
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = Math.min(width / 24f, height / 24f)
        c.save()
        c.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        c.scale(s, s)
        c.drawPath(path, p)
        c.restore()
    }
}

