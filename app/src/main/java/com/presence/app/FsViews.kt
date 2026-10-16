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

/** A filled icon from the website (SVG path data with its own view box), e.g. the Mirror icon. */
class FsFillIconView(ctx: Context, d: String, private val vw: Float, private val vh: Float, color: Int = Color.WHITE) : View(ctx) {
    private val path = PathParser.createPathFromPathData(d).also { it.fillType = android.graphics.Path.FillType.EVEN_ODD }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        p.style = Paint.Style.FILL
        p.color = color
    }

    override fun onDraw(c: Canvas) {
        val s = Math.min(width / vw, height / vh)
        c.save()
        c.translate((width - vw * s) / 2f, (height - vh * s) / 2f)
        c.scale(s, s)
        c.drawPath(path, p)
        c.restore()
    }
}

/** The icon for an eyes mode: Mirror is the filled website icon, the others are line icons. */
fun modeIcon(ctx: Context, path: String, color: Int): View =
    if (path == Icons.MIRROR) FsFillIconView(ctx, path, Icons.MIRROR_W, Icons.MIRROR_H, color)
    else FsIconView(ctx, path, color)
