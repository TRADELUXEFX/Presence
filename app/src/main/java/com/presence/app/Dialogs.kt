package com.presence.app

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.presence.app.Ui.addTo

/** Pop-up message in the kit's style: a white card, a title, a line of text, and one or two pill buttons. */
object Dialogs {
    fun show(
        a: Activity,
        title: String,
        message: String,
        primary: String,
        secondary: String? = null,
        onPrimary: () -> Unit = {},
        onSecondary: () -> Unit = {},
        accent: Int = Ui.BLUE
    ) {
        if (a.isFinishing) return
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = Ui.card(a)
        card.setPadding(Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 18))
        card.addView(Ui.text(a, title, 18f, Ui.TEXT, Ui.BOLD))
        val msg = Ui.text(a, message, 14f, Ui.MUTED, Ui.REG)
        msg.setLineSpacing(0f, 1.25f)
        card.addTo(msg, 8)

        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        if (secondary != null) {
            val b2 = Ui.pill(a, secondary, Ui.SOFT, Ui.TEXT)
            b2.setOnClickListener {
                d.dismiss()
                onSecondary()
            }
            val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp2.rightMargin = Ui.dp(a, 6)
            row.addView(b2, lp2)
        }
        // on a training's own screen the main button wears that training's colour
        val b1 = if (accent == Ui.BLUE) Ui.pill(a, primary) else Ui.pill(a, primary, accent, Ui.onColor(accent))
        b1.setOnClickListener {
            d.dismiss()
            onPrimary()
        }
        val lp1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        if (secondary != null) lp1.leftMargin = Ui.dp(a, 6)
        row.addView(b1, lp1)
        card.addTo(row, 20)

        Ui.noClipDeep(card)
        d.setContentView(card)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout(
            (a.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        d.show()
    }

    /**
     * The ? pop-up on a training's settings screen: what each of the four cue switches does.
     * [voiceText] says what the voice does in this training. Colours follow the training's own colour.
     */
    fun cuesHelp(a: Activity, key: String, voiceText: String) {
        if (a.isFinishing) return
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val accent = Trainings.accent(key)

        val card = Ui.card(a)
        card.setPadding(Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 18))

        val head = LinearLayout(a)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(Ui.text(a, "Cues and sounds", 19f, Ui.TEXT, Ui.BOLD), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = FrameLayout(a)
        close.background = Ui.rounded(a, Ui.SOFT, 14)
        close.contentDescription = "Close"
        close.addView(FsIconView(a, Icons.CLOSE, Ui.TEXT), FrameLayout.LayoutParams(Ui.dp(a, 14), Ui.dp(a, 14), Gravity.CENTER))
        close.setOnClickListener { d.dismiss() }
        head.addView(close, LinearLayout.LayoutParams(Ui.dp(a, 28), Ui.dp(a, 28)))
        card.addView(head)

        val intro = Ui.text(a, "Four helpers that guide you through the training. Switch on the ones you like.", 13f, Ui.MUTED, Ui.REG)
        intro.setLineSpacing(0f, 1.25f)
        card.addTo(intro, 4)

        /** The phrase [mark] in [text] wears the training's colour, so the hold gesture stands out. */
        fun marked(text: String, mark: String): CharSequence {
            val sp = SpannableString(text)
            val i = text.indexOf(mark)
            if (i >= 0) sp.setSpan(ForegroundColorSpan(accent), i, i + mark.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sp
        }

        fun row(icon: String, name: String, text: CharSequence, first: Boolean) {
            if (!first) {
                val line = View(a)
                line.setBackgroundColor(Ui.LINE)
                card.addTo(line, 0)
                (line.layoutParams as LinearLayout.LayoutParams).height = Ui.dp(a, 1)
            }
            val r = LinearLayout(a)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.TOP
            r.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, if (first) 4 else 12))
            val circle = FrameLayout(a)
            circle.background = Ui.rounded(a, Trainings.soft(key), 20)
            circle.addView(FsIconView(a, icon, Trainings.ink(key)), FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER))
            r.addView(circle, LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)))
            val col = LinearLayout(a)
            col.orientation = LinearLayout.VERTICAL
            col.addView(Ui.text(a, name, 15f, Ui.TEXT, Ui.MED))
            val body = Ui.text(a, "", 13f, Ui.MUTED, Ui.REG)
            body.text = text
            body.setLineSpacing(0f, 1.25f)
            col.addTo(body, 3)
            r.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(a, 12) })
            card.addView(r, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (first) topMargin = Ui.dp(a, 8) })
        }
        row(Icons.VIBRATE, "Vibrate", "A short buzz with each cue, so you can feel when something changes without looking.", true)
        row(Icons.BELL, "Bell", marked("A soft bell rings when the training starts and again when it ends. Hold the icon to pick a different bell.", "Hold the icon"), false)
        row(Icons.VOICE, "Voice", voiceText, false)
        row(Icons.MUSIC, "Music", marked("A quiet sound plays under the training. Your voice and bell stay clear on top. Hold the icon to choose the sound.", "Hold the icon"), false)

        val tip = LinearLayout(a)
        tip.orientation = LinearLayout.HORIZONTAL
        tip.gravity = Gravity.CENTER_VERTICAL
        tip.background = Ui.rounded(a, Ui.SOFT, 14)
        tip.setPadding(Ui.dp(a, 14), Ui.dp(a, 11), Ui.dp(a, 14), Ui.dp(a, 11))
        val tipText = Ui.text(a, "Tap an icon to switch it on or off. A lit icon is on, a grey one is off.", 13f, Ui.TEXT, Ui.REG)
        tipText.setLineSpacing(0f, 1.25f)
        tip.addView(FsIconView(a, Icons.HAND, accent), LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)))
        tip.addView(tipText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(a, 12) })
        card.addTo(tip, 6)

        val ok = Ui.pill(a, "Got it", accent, Ui.onColor(accent))
        ok.setOnClickListener { d.dismiss() }
        card.addTo(ok, 16)

        Ui.noClipDeep(card)
        val scroll = ScrollView(a)
        scroll.addView(card)
        d.setContentView(scroll)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout(
            (a.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        d.show()
    }
}
