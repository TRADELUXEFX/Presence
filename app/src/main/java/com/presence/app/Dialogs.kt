package com.presence.app

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
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
}
