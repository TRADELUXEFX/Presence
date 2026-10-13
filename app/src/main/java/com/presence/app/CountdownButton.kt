package com.presence.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.widget.Button

/**
 * The "Starts in 5 ... 1" button between two trainings of a session.
 * A light colour fills from left to right with the full accent colour as the seconds pass.
 * With [upNext], the button shows two centred lines: the next training in smaller type, "Starts in 3" below it.
 * It follows the Engine's own clock, so it ends exactly when the next training starts. It is not tappable.
 */
class CountdownButton(ctx: Context, private val accent: Int, ink: Int, private val upNext: String? = null) : Button(ctx) {
    private val base = blend(accent, Color.WHITE, 0.55f)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = base }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val clip = Path()
    private val rect = RectF()
    private val total = Engine.NEXT_DELAY_MS
    private var endsAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            update()
            if (remaining() > 0L) postOnAnimation(this)
        }
    }

    init {
        isAllCaps = false
        setTextColor(ink)
        textSize = 17f
        Ui.setFont(ctx, this, Ui.BOLD)
        background = null
        stateListAnimator = null
        val tall = if (upNext != null) 72 else 56
        minHeight = Ui.dp(ctx, tall)
        minimumHeight = Ui.dp(ctx, tall)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.1f)
        isClickable = false
        isFocusable = false
        text = label(total.toInt() / 1000)
        contentDescription = text
    }

    /** "Starts in 3", with the next training above it in smaller type when there is one. */
    private fun label(secs: Int): CharSequence {
        val main = "Starts in $secs"
        if (upNext == null) return main
        val sp = SpannableString("$upNext\n$main")
        sp.setSpan(RelativeSizeSpan(0.76f), 0, upNext.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
    }

    private fun remaining(): Long = (endsAt - SystemClock.uptimeMillis()).coerceIn(0L, total)

    private fun update() {
        val secs = ((remaining() + 999L) / 1000L).toInt().coerceIn(1, (total / 1000).toInt())
        val next = label(secs)
        if (text.toString() != next.toString()) {
            text = next
            contentDescription = next.toString().replace("\n", ". ")
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val at = Engine.nextAt
        endsAt = if (at > SystemClock.uptimeMillis()) at else SystemClock.uptimeMillis() + total
        removeCallbacks(tick)
        postOnAnimation(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, basePaint)
        val progress = 1f - remaining().toFloat() / total
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(0f, 0f, width * progress, height.toFloat(), fillPaint)
        canvas.restore()
        super.onDraw(canvas)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun mix(x: Int, y: Int) = (x + (y - x) * t).toInt()
        return Color.rgb(mix(Color.red(a), Color.red(b)), mix(Color.green(a), Color.green(b)), mix(Color.blue(a), Color.blue(b)))
    }
}
