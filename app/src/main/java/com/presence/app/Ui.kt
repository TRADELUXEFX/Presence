package com.presence.app

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/** Design kit: Poppins, soft cards, pill buttons. Every screen is built from these. */
object Ui {
    /** Dark mode switch. Set from Settings at app start and when the toggle changes. */
    @JvmStatic var dark: Boolean = false

    /** Pick a colour for the current theme: c("#light", "#dark"). */
    fun c(light: String, darkHex: String): Int = Color.parseColor(if (dark) darkHex else light)

    val BLUE: Int = Color.parseColor("#1E90FF")
    val BLUE_DARK: Int = Color.parseColor("#0B5ED7")
    val BLUE_SOFT: Int get() = c("#E6F1FF", "#1B3558")
    val BG: Int get() = c("#F4F7FB", "#0B1220")
    /** Cards, dialogs and bottom bars. */
    val SURFACE: Int get() = c("#FFFFFF", "#162032")
    /** Text fields and tinted rows inside cards. */
    val FIELD: Int get() = c("#F8FAFC", "#0F1A2B")
    val TEXT: Int get() = c("#0F172A", "#F1F5F9")
    val MUTED: Int get() = c("#64748B", "#94A3B8")
    val LINE: Int get() = c("#E2E8F0", "#2A3A52")
    val SOFT: Int get() = c("#E8EEF6", "#223049")
    val CHECK_LINE: Int get() = c("#CBD5E1", "#475569")
    val RED: Int = Color.parseColor("#EF4444")
    val GREEN: Int = Color.parseColor("#16A34A")
    val AMBER: Int = Color.parseColor("#F59E0B")
    val WHITE: Int = Color.WHITE

    const val REG = 0
    const val MED = 1
    const val BOLD = 2

    /**
     * Applies "don't clip" to a whole screen: every layout inside it, now and any added later
     * (rows and cards are built after the screen opens). Scroll views keep their own clipping so
     * scrolling still works. This is what keeps every curved container drawing in full.
     */
    fun noClipDeep(v: View) {
        if (v is ViewGroup) {
            // A vertical scroll area must clip itself to its own bounds, otherwise a scrolled page draws over the header
            // or the bottom bar (the parent has clipChildren off, and a ScrollView with no padding does not clip by itself).
            // clipBounds does this on the scroll view itself, without touching the parent's clipping.
            if (v is android.widget.ScrollView) {
                v.clipChildren = true
                v.clipToPadding = true
                v.addOnLayoutChangeListener { sv, l, t, r, b, _, _, _, _ ->
                    sv.clipBounds = android.graphics.Rect(0, 0, r - l, b - t)
                }
            } else {
                v.clipChildren = false
                if (v !is android.widget.HorizontalScrollView) v.clipToPadding = false
            }
            v.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) { if (child != null) noClipDeep(child) }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })
            for (i in 0 until v.childCount) noClipDeep(v.getChildAt(i))
        }
    }

    /** Let card shadows draw into the padding instead of being cut off at the edges. */
    fun noClip(vararg groups: ViewGroup) {
        for (g in groups) {
            g.clipToPadding = false
            g.clipChildren = false
        }
    }


    /** Draw behind the status + navigation bars so the app's blue flows up under the clock/battery. */
    fun edgeToEdge(a: Activity) {
        val w = a.window
        WindowCompat.setDecorFitsSystemWindows(w, false)
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            w.isStatusBarContrastEnforced = false
            w.isNavigationBarContrastEnforced = false
        }
        val ic = WindowCompat.getInsetsController(w, w.decorView)
        ic.isAppearanceLightStatusBars = false      // white icons on the blue
        ic.isAppearanceLightNavigationBars = !dark  // dark icons on the light bottom
    }

    /** Colours the navigation bar area (the strip under the bottom bar) and keeps its icons light. */
    fun setNavColor(a: Activity, color: Int) {
        val w = a.window
        w.navigationBarColor = color
        WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars = false
    }

    /** Adds the system-bar (and optionally keyboard) size on top of the view's own padding. */
    fun padInsets(v: View, top: Boolean = false, bottom: Boolean = false, ime: Boolean = false) {
        val l = v.paddingLeft; val t = v.paddingTop; val r = v.paddingRight; val b = v.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val kb = if (ime) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
            view.setPadding(l, t + (if (top) bars.top else 0), r, b + (if (bottom) Math.max(bars.bottom, kb) else 0))
            insets
        }
        ViewCompat.requestApplyInsets(v)
    }

    /** Solid blue strip over the status bar that fades in as content scrolls under it. */
    fun statusScrim(c: Context): View {
        val v = View(c)
        v.setBackgroundColor(BLUE)
        v.alpha = 0f
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val h = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).top
            view.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
            insets
        }
        return v
    }

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    /** Blend two colours. t = 0 gives a, t = 1 gives b. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val r = Color.red(a) + (Color.red(b) - Color.red(a)) * t
        val g = Color.green(a) + (Color.green(b) - Color.green(a)) * t
        val bl = Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t
        return Color.rgb(r.toInt(), g.toInt(), bl.toInt())
    }

    /** Text colour that reads best on [bg]: dark ink or white, whichever has more contrast. */
    /** The one text colour for anything sitting on a training colour: dark ink, in light and dark mode alike. */
    fun onColor(bg: Int): Int = Color.parseColor("#0F172A")

    fun rounded(c: Context, color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(c, radiusDp).toFloat()
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), strokeColor)
        return g
    }

    fun setFont(c: Context, t: TextView, weight: Int) {
        t.typeface = when (weight) {
            BOLD -> Fonts.bold(c)
            MED -> Fonts.medium(c)
            else -> Fonts.regular(c)
        }
    }

    fun text(c: Context, s: String, sizeSp: Float, color: Int = TEXT, weight: Int = REG): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = sizeSp
        t.setTextColor(color)
        setFont(c, t, weight)
        t.includeFontPadding = false
        return t
    }

    /** Full-width pill button. */
    fun pill(c: Context, s: String, bg: Int = BLUE, fg: Int = WHITE): Button {
        val b = Button(c)
        b.text = s
        b.isAllCaps = false
        b.setTextColor(fg)
        b.textSize = 16f
        setFont(c, b, BOLD)
        b.background = rounded(c, bg, 28)
        b.stateListAnimator = null
        b.minHeight = dp(c, 56)
        b.minimumHeight = dp(c, 56)
        b.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), dp(c, 8))
        return b
    }

    /** Light-blue card fill (Training History), so cards stand out from the near-white page. */
    val CARD_TINT: Int get() = c("#D6E8FF", "#1B3558")

    /** Grey-blue text that stays readable on the light-blue cards. */
    val MUTED_TINT: Int get() = c("#475569", "#94A3B8")

    fun card(c: Context, fill: Int = SURFACE): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.background = rounded(c, fill, 22)
        l.elevation = dp(c, 2).toFloat()
        l.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16))
        return l
    }

    fun sectionTitle(c: Context, s: String): TextView {
        val t = text(c, s.uppercase(), 11f, MUTED, BOLD)
        t.letterSpacing = 0.12f
        return t
    }

    /** Offline or error notice, option 1: a wifi-off drawing in a soft circle, centred text, and Try again. */
    fun offlineCard(c: Context, title: String, body: String, onRetry: () -> Unit): LinearLayout {
        val card = card(c)
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.setPadding(dp(c, 22), dp(c, 24), dp(c, 22), dp(c, 22))
        val circle = android.widget.FrameLayout(c)
        circle.background = rounded(c, BLUE_SOFT, 48)
        circle.addView(
            FsIconView(c, "M5 12.5a10 10 0 0 1 14 0 M8.5 16a5 5 0 0 1 7 0 M12 20h.01 M3 3l18 18", BLUE),
            android.widget.FrameLayout.LayoutParams(dp(c, 44), dp(c, 44), Gravity.CENTER)
        )
        card.addView(circle, LinearLayout.LayoutParams(dp(c, 96), dp(c, 96)))
        val head = text(c, title, 16f, TEXT, BOLD)
        head.gravity = Gravity.CENTER
        card.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 16) })
        val sub = text(c, body, 13f, MUTED, REG)
        sub.gravity = Gravity.CENTER
        sub.setLineSpacing(0f, 1.25f)
        card.addView(sub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 6) })
        val btn = pill(c, "Try again")
        btn.setOnClickListener { onRetry() }
        card.addView(btn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 18) })
        return card
    }

    /** The small centred offline note used on every screen that needs the connection: a wifi-off icon and You're offline. */
    fun offlineRow(c: Context): LinearLayout {
        val pill = LinearLayout(c)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER
        pill.background = rounded(c, BLUE_SOFT, 14)
        pill.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6))
        pill.addView(FsIconView(c, "M5 12.5a10 10 0 0 1 14 0 M8.5 16a5 5 0 0 1 7 0 M12 20h.01 M3 3l18 18", BLUE),
            LinearLayout.LayoutParams(dp(c, 12), dp(c, 12)))
        val label = text(c, "You're offline", 12f, BLUE, BOLD)
        label.setPadding(dp(c, 6), 0, 0, 0)
        pill.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_HORIZONTAL
        row.addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
    }

    /** Soft amber notice bar (hidden until needed). */
    fun banner(c: Context, s: String): TextView {
        val t = text(c, s, 12f, Ui.c("#92400E", "#FCD34D"), MED)
        t.gravity = Gravity.CENTER
        t.setLineSpacing(0f, 1.2f)
        t.background = rounded(c, Ui.c("#FEF3C7", "#3A2F10"), 14)
        t.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10))
        t.visibility = View.GONE
        return t
    }

    /** Centered helper message (empty states, errors, tips). */
    fun note(c: Context, s: String): TextView {
        val t = text(c, s, 14f, MUTED, MED)
        t.gravity = Gravity.CENTER
        t.setLineSpacing(0f, 1.25f)
        t.setPadding(dp(c, 24), dp(c, 32), dp(c, 24), dp(c, 32))
        return t
    }

    fun lp(c: Context, topDp: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(c, topDp)
        return p
    }

    fun LinearLayout.addTo(v: View, topDp: Int = 0) {
        this.addView(v, lp(this.context, topDp))
    }

    fun input(c: Context, hint: String, password: Boolean): EditText {
        val e = EditText(c)
        e.hint = hint
        e.setHintTextColor(Color.parseColor("#94A3B8"))
        e.setTextColor(TEXT)
        e.textSize = 15f
        setFont(c, e, REG)
        e.setSingleLine(true)
        e.inputType = if (password) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        e.background = rounded(c, Ui.FIELD, 16, LINE, 1)
        e.setPadding(dp(c, 18), dp(c, 15), dp(c, 18), dp(c, 15))
        return e
    }

    private fun circleButton(c: Context, label: String, tint: Int = BLUE): TextView {
        val t = text(c, label, 20f, tint, BOLD)
        t.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(if (tint == BLUE) BLUE_SOFT else mix(tint, SURFACE, 0.85f))
        t.background = g
        return t
    }

    /** "Label            (-)  value  (+)" */
    fun stepper(
        c: Context, label: String, get: () -> Int, set: (Int) -> Unit,
        min: Int, max: Int, step: Int,
        fastFrom: Int = Int.MAX_VALUE, fastStep: Int = step,
        tint: Int = BLUE,
        show: (Int) -> String
    ): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val name = text(c, label, 14f, TEXT, MED)
        row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val minus = circleButton(c, "−", tint)
        val plus = circleButton(c, "+", tint)
        val value = text(c, show(get()), 16f, TEXT, BOLD)
        value.gravity = Gravity.CENTER
        value.maxLines = 1
        val size = dp(c, 38)
        row.addView(minus, LinearLayout.LayoutParams(size, size))
        // fixed width wide enough for "120 min", so the + button never moves when the value changes
        row.addView(value, LinearLayout.LayoutParams(dp(c, 92), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(plus, LinearLayout.LayoutParams(size, size))
        minus.setOnClickListener {
            val v = Math.max(min, get() - (if (get() > fastFrom) fastStep else step))
            set(v)
            value.text = show(v)
        }
        plus.setOnClickListener {
            val v = Math.min(max, get() + (if (get() >= fastFrom) fastStep else step))
            set(v)
            value.text = show(v)
        }
        return row
    }

    /** Switch colors that stay visible: blue knob on a light-blue track when on, grey knob on a pale track when off. */
    fun styleSwitch(sw: SwitchCompat, tint: Int = BLUE) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = android.content.res.ColorStateList(states, intArrayOf(tint, c("#94A3B8", "#94A3B8")))
        val trackOn = if (tint == BLUE) c("#B3D7FF", "#2F5F9E") else mix(tint, SURFACE, 0.6f)
        sw.trackTintList = android.content.res.ColorStateList(states, intArrayOf(trackOn, c("#D5DEEA", "#3A4A63")))
    }

    /** "Label + small description            [switch]" */
    fun switchRow(c: Context, label: String, sub: String?, initial: Boolean, tint: Int = BLUE, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.addView(text(c, label, 14f, TEXT, MED))
        if (sub != null) {
            val s = text(c, sub, 12f, MUTED, REG)
            col.addView(s, lp(c, 2))
        }
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchCompat(c)
        styleSwitch(sw, tint)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        row.addView(sw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
    }

    /** Blue top bar with a round arrow-only Back button and the title centered. */
    fun header(c: Context, title: String, tint: Int = 0, action: View? = null, titleExtra: View? = null, showBack: Boolean = true, onBack: () -> Unit): LinearLayout {
        val h = LinearLayout(c)
        h.orientation = LinearLayout.HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        val fgHead = if (tint == 0) WHITE else onColor(tint)
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (tint == 0) intArrayOf(BLUE, BLUE_DARK) else intArrayOf(tint, mix(tint, Color.BLACK, 0.2f)))
        val r = dp(c, 24).toFloat()
        bg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        h.background = bg
        h.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 18))
        padInsets(h, top = true)

        val size = dp(c, 40)
        val back = FrameLayout(c)
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Color.parseColor("#33FFFFFF"))
        back.background = circle
        back.contentDescription = "Back"
        back.setOnClickListener { onBack() }
        val arrow = FsIconView(c, "M15 18l-6-6 6-6", fgHead)
        back.addView(arrow, FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), Gravity.CENTER))

        // Left and right sides share the leftover width equally, so the title is always in the true centre,
        // whether the right side holds an action (Select, Save) or nothing.
        val left = LinearLayout(c)
        left.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        // tab screens have no back arrow; the space stays so the title is still centred
        left.addView(if (showBack) back else View(c), LinearLayout.LayoutParams(size, size))
        h.addView(left, LinearLayout.LayoutParams(0, -2, 1f))

        val t = text(c, title, 20f, fgHead, BOLD)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.ellipsize = android.text.TextUtils.TruncateAt.END
        if (titleExtra == null) {
            h.addView(t, LinearLayout.LayoutParams(-2, -2))
        } else {
            // the title with its extra button (the "i") right beside it, kept in the centre
            val titleRow = LinearLayout(c)
            titleRow.orientation = LinearLayout.HORIZONTAL
            titleRow.gravity = Gravity.CENTER_VERTICAL
            titleRow.addView(t, LinearLayout.LayoutParams(-2, -2))
            val extraLp = LinearLayout.LayoutParams(dp(c, 30), dp(c, 30))
            extraLp.leftMargin = dp(c, 8)
            titleRow.addView(titleExtra, extraLp)
            h.addView(titleRow, LinearLayout.LayoutParams(-2, -2))
        }

        val right = LinearLayout(c)
        right.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        if (action != null) right.addView(action, LinearLayout.LayoutParams(-2, -2))
        h.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        return h
    }

    /** "Tip" label for a header: tap to read what the training is about. */
    fun infoButton(c: Context, fg: Int, onClick: () -> Unit): View {
        val tip = text(c, "Tips", 14f, fg, BOLD)
        tip.gravity = Gravity.CENTER
        tip.maxLines = 1
        tip.background = rounded(c, Color.parseColor("#33FFFFFF"), 20)
        tip.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
        tip.minHeight = dp(c, 40)
        tip.contentDescription = "About this training"
        tip.setOnClickListener { onClick() }
        return tip
    }

    /** Small rounded tap target used for list actions. */
    /**
     * Keeps a label on ONE line. If the text is too wide for its button or chip, it shrinks a little
     * (down to minSp) instead of dropping to a second line. Call it after the text size is set.
     */
    fun fit(t: TextView, maxSp: Int, minSp: Int = 9) {
        t.maxLines = 1
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            t, minSp, maxSp, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
    }

    /**
     * Round selection marker: empty ring when off, solid blue circle with a drawn white tick when on.
     * The tick is a vector line, centered exactly (the old "✓" text character sat off-center because
     * the app font has no tick, so the phone drew it from another font).
     */
    fun tick(c: Context, on: Boolean, sizeDp: Int): FrameLayout {
        val f = FrameLayout(c)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        if (on) g.setColor(BLUE) else { g.setColor(SURFACE); g.setStroke(dp(c, 2), CHECK_LINE) }
        f.background = g
        if (on) {
            val s = dp(c, (sizeDp * 0.64f).toInt())
            f.addView(FsIconView(c, Icons.CHECK, WHITE), FrameLayout.LayoutParams(s, s, Gravity.CENTER))
        }
        return f
    }

    /** Bottom bar in a colour (the training's, or blue), with the same gradient and curved top as the header. */
    fun tintedBottomBar(c: Context, bar: View, tint: Int) {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(tint, mix(tint, Color.BLACK, 0.2f)))
        val r = dp(c, 24).toFloat()
        g.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        bar.background = g
    }

    /** Bottom action bar with curved top corners, the mirror image of the header's curved bottom corners. */
    fun bottomBarBg(c: Context, bar: View) {
        val g = GradientDrawable()
        g.setColor(SURFACE)
        val r = dp(c, 24).toFloat()
        g.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        bar.background = g
    }

    fun chip(c: Context, label: String, fg: Int, bg: Int): TextView {
        val t = text(c, label, 13f, fg, BOLD)
        t.gravity = Gravity.CENTER
        t.background = rounded(c, bg, 20)
        t.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10))
        fit(t, 13)
        return t
    }
}
