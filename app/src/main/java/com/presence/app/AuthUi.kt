package com.presence.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/** Pieces shared by the login and forgot-password screens. */
object AuthUi {
    const val MAIL = "M4 6h16v12H4z M4 7l8 6 8-6"
    const val LOCK = "M6 11h12v9H6z M8 11V8a4 4 0 0 1 8 0v3"
    const val EYE = "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"
    const val CHECK = "M5 13l4 4L19 7"

    /** App logo with soft, curved corners (the raw icon image has sharp edges). */
    fun logo(c: Context, sizeDp: Int): ImageView {
        val v = ImageView(c)
        v.setImageResource(R.mipmap.ic_launcher)
        v.scaleType = ImageView.ScaleType.CENTER_CROP
        val r = Ui.dp(c, sizeDp) * 0.27f
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        v.clipToOutline = true
        v.elevation = Ui.dp(c, 6).toFloat()
        return v
    }

    /** Icon + input in one rounded box that lights up blue when focused; password gets a show/hide eye. */
    fun inputRow(c: Context, iconPath: String, hintText: String, isPassword: Boolean): Pair<LinearLayout, EditText> {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val idle = Ui.rounded(c, Ui.FIELD, 18, Ui.LINE, 1)
        val focus = Ui.rounded(c, Ui.SURFACE, 18, Ui.BLUE, 2)
        row.background = idle
        row.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 8), 0)

        val icon = FsIconView(c, iconPath, Ui.MUTED)
        row.addView(icon, LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)))

        val e = Ui.input(c, hintText, isPassword)
        e.background = null
        e.setPadding(Ui.dp(c, 12), Ui.dp(c, 17), Ui.dp(c, 8), Ui.dp(c, 17))
        if (!isPassword) e.imeOptions = EditorInfo.IME_ACTION_NEXT
        e.setOnFocusChangeListener { _, has ->
            row.background = if (has) focus else idle
            icon.invalidate()
        }
        row.addView(e, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (isPassword) {
            var shown = false
            val eye = FrameLayout(c)
            val eyeIcon = FsIconView(c, EYE, Ui.MUTED)
            eye.addView(eyeIcon, FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
            eye.contentDescription = "Show password"
            eye.setOnClickListener {
                shown = !shown
                val keep = e.selectionEnd
                e.inputType = if (shown) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                Ui.setFont(c, e, Ui.REG)
                e.setSelection(Math.max(0, keep))
                eyeIcon.alpha = if (shown) 1f else 0.7f
            }
            row.addView(eye, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        }
        return Pair(row, e)
    }

    /** Blue gradient hero with soft decorative circles, clipped to its rounded bottom. */
    class HeroLayout(c: Context) : LinearLayout(c) {
        private val clip = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val radius = Ui.dp(c, 36).toFloat()

        init {
            setWillNotDraw(false)
            val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
            bg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
            background = bg
            paint.color = android.graphics.Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            clip.reset()
            clip.addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            paint.alpha = 26
            canvas.drawCircle(w * 0.92f, h * 0.18f, w * 0.34f, paint)
            paint.alpha = 16
            canvas.drawCircle(w * 0.04f, h * 0.88f, w * 0.26f, paint)
            canvas.restore()
            super.onDraw(canvas)
        }
    }


}
