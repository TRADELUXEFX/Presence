package com.presence.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout

/** Base for views that run one endless 0..1 animation, only while they are on screen. */
abstract class LoopView(ctx: Context, private val ms: Long) : View(ctx) {
    protected var t = 0f
    private var anim: ValueAnimator? = null

    private fun start() {
        if (anim != null) return
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = ms
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { t = it.animatedValue as Float; invalidate() }
        a.start()
        anim = a
    }

    protected fun stop() {
        anim?.cancel()
        anim = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changed: View, visibility: Int) {
        super.onVisibilityChanged(changed, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) start() else stop()
    }
}

/** Three dots that pop one after another (used by the training screen, not a loader). */
class PopDotsView(ctx: Context, private val color: Int = Ui.TEXT, private val dotDp: Int = 10) : LoopView(ctx, 1100) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        p.color = color
        val r = Ui.dp(context, dotDp) / 2f
        val gap = r * 3.4f
        val cx = width / 2f
        val cy = height / 2f
        for (i in 0 until 3) {
            var x = t - i * 0.16f
            if (x < 0f) x += 1f
            val pop = if (x < 0.42f) Math.sin(Math.PI * x / 0.42).toFloat() else 0f
            val scale = 0.62f + 0.68f * pop
            p.alpha = (110 + 145 * pop).toInt()
            canvas.drawCircle(cx + (i - 1) * gap, cy - r * 1.3f * pop, r * scale, p)
        }
    }
}

/** Indeterminate ring: a blue arc that sweeps around a soft track. */
class SpinnerRingView(
    ctx: Context,
    private val color: Int = Ui.BLUE,
    private val trackColor: Int = Ui.SOFT,
    private val strokeDp: Int = 7
) : LoopView(ctx, 1300) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init {
        track.style = Paint.Style.STROKE
        arc.style = Paint.Style.STROKE
        arc.strokeCap = Paint.Cap.ROUND
    }

    /**
     * Data has arrived: finish the lap the ring is on, then fade it out and call onDone.
     * The ring is never cut off in the middle of a lap.
     */
    fun finish(onDone: () -> Unit) {
        stop()
        val remaining = (1f - t).coerceAtLeast(0f)
        val lap = ValueAnimator.ofFloat(t, 1f)
        lap.duration = (remaining * 1300f).toLong().coerceAtLeast(150L)
        lap.interpolator = LinearInterpolator()
        lap.addUpdateListener { t = it.animatedValue as Float; invalidate() }
        lap.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animate().alpha(0f).setDuration(220L).withEndAction { onDone() }.start()
            }
        })
        lap.start()
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = Ui.dp(context, strokeDp).toFloat()
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        track.color = trackColor
        arc.color = color
        val half = stroke / 2f + 1f
        box.set(half, half, width - half, height - half)
        canvas.drawOval(box, track)
        // the arc grows and shrinks while it travels
        val grow = (Math.sin(t * 2 * Math.PI - Math.PI / 2).toFloat() + 1f) / 2f
        val sweep = 50f + 190f * grow
        canvas.drawArc(box, t * 360f * 1.6f - 90f, sweep, false, arc)
    }
}

object Loading {
    /** True when a sweeping ring is on screen somewhere inside this view. */
    fun has(root: View): Boolean {
        if (root is SpinnerRingView) return true
        if (root is ViewGroup) for (i in 0 until root.childCount) if (has(root.getChildAt(i))) return true
        return false
    }

    /** Finishes every ring inside root, then calls onDone once they have all faded. */
    fun finish(root: View, onDone: () -> Unit) {
        val rings = ArrayList<SpinnerRingView>()
        collect(root, rings)
        if (rings.isEmpty()) { onDone(); return }
        var left = rings.size
        for (r in rings) r.finish { left--; if (left == 0) onDone() }
    }

    private fun collect(v: View, out: MutableList<SpinnerRingView>) {
        if (v is SpinnerRingView) out.add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i), out)
    }

    /** The loading animation for list-style screens: just the sweeping ring, centered. */
    fun page(c: Context): LinearLayout {
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = android.view.Gravity.CENTER
        col.minimumHeight = Ui.dp(c, 360)
        col.addView(SpinnerRingView(c), LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)))
        return col
    }

    /** A small centered sweeping ring for use inside a card while its data loads. */
    fun inline(c: Context, sizeDp: Int = 40): LinearLayout {
        val row = LinearLayout(c)
        row.gravity = android.view.Gravity.CENTER
        row.addView(SpinnerRingView(c, strokeDp = 4), LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)))
        return row
    }

}
