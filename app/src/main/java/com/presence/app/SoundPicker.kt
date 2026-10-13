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

/** Pop-up list of sounds: tap one to hear it and choose it. Used for the bell style and the background sound. */
object SoundPicker {
    fun show(
        a: Activity,
        title: String,
        note: String,
        options: List<Pair<String, String>>,
        current: String,
        accent: Int,
        onHear: (String) -> Unit,
        onChoose: (String) -> Unit,
        onClose: () -> Unit = {}
    ) {
        if (a.isFinishing) return
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var chosen = current

        val card = Ui.card(a)
        card.setPadding(Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 16))
        card.addView(Ui.text(a, title, 18f, Ui.TEXT, Ui.BOLD))
        val n = Ui.text(a, note, 13f, Ui.MUTED, Ui.REG)
        n.setLineSpacing(0f, 1.25f)
        card.addTo(n, 6)

        val list = LinearLayout(a)
        list.orientation = LinearLayout.VERTICAL
        fun fill() {
            list.removeAllViews()
            for ((i, o) in options.withIndex()) {
                val sel = o.first == chosen
                val row = LinearLayout(a)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.background = Ui.rounded(a, if (sel) Ui.mix(accent, Color.WHITE, 0.7f) else Ui.SOFT, 16, if (sel) accent else 0, if (sel) 2 else 0)
                row.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14))
                val label = Ui.text(a, o.second, 15f, if (sel) Color.parseColor("#0F172A") else Ui.TEXT, if (sel) Ui.BOLD else Ui.MED)
                row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                if (sel) row.addView(FsIconView(a, Icons.CHECK, Color.parseColor("#0F172A")), LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)))
                row.setOnClickListener {
                    chosen = o.first
                    onChoose(o.first)
                    onHear(o.first)
                    fill()
                }
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = Ui.dp(a, 8) })
            }
        }
        fill()
        card.addTo(list, 14)

        val done = Ui.pill(a, "Done")
        done.setOnClickListener { d.dismiss() }
        card.addTo(done, 16)

        d.setOnDismissListener { onClose() }
        Ui.noClipDeep(card)
        d.setContentView(card)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((a.resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }
}
