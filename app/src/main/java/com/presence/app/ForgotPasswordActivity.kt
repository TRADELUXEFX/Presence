package com.presence.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.presence.app.Ui.addTo

/** Type your email, tap Send reset link. Supabase emails the link. */
class ForgotPasswordActivity : BaseActivity() {
    override val needsLogin: Boolean get() = false

    private lateinit var email: EditText
    private lateinit var msg: TextView
    private lateinit var send: Button
    private lateinit var spin: SpinnerRingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.header(c, "Reset password") { finish() }, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 24))

        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22))
        val intro = Ui.text(c, "Enter the email you signed up with. We'll send you a link to choose a new password.", 14f, Ui.MUTED, Ui.REG)
        intro.setLineSpacing(0f, 1.25f)
        card.addView(intro)

        val row = AuthUi.inputRow(c, AuthUi.MAIL, "Email address", false)
        email = row.second
        email.setText(intent.getStringExtra("email") ?: "")
        email.imeOptions = EditorInfo.IME_ACTION_DONE
        card.addTo(row.first, 18)

        send = Ui.pill(c, "Send reset link")
        spin = SpinnerRingView(c, Ui.WHITE, Color.parseColor("#55FFFFFF"), 3)
        spin.visibility = View.GONE
        val wrap = FrameLayout(c)
        wrap.addView(send, FrameLayout.LayoutParams(-1, -2))
        wrap.addView(spin, FrameLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER))
        card.addTo(wrap, 18)

        msg = Ui.text(c, "", 13f, Ui.RED, Ui.MED)
        msg.gravity = Gravity.CENTER
        card.addTo(msg, 14)

        body.addView(card)
        root.addView(body, LinearLayout.LayoutParams(-1, -2))
        Ui.padInsets(root, bottom = true, ime = true)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(root)
        setContentView(scroll)

        send.setOnClickListener { doSend() }
        email.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doSend()
                true
            } else {
                false
            }
        }
    }

    private fun showMsg(text: String, ok: Boolean) {
        msg.setTextColor(if (ok) Ui.GREEN else Ui.RED)
        msg.text = text
    }

    private fun busy(on: Boolean) {
        send.isEnabled = !on
        send.text = if (on) "" else "Send reset link"
        spin.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun doSend() {
        val e = email.text.toString().trim()
        if (e.isEmpty()) {
            showMsg("Please enter your email.", false)
            return
        }
        busy(true)
        showMsg("", true)
        Thread {
            val r = Api.sendReset(e)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                busy(false)
                if (r.ok) showMsg("Reset link sent! Check your email.", true) else showMsg(r.message(), false)
            }
        }.start()
    }
}
