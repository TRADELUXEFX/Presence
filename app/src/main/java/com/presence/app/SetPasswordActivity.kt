package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.presence.app.Ui.addTo

/** Shown after an invite or reset link: the person chooses a password, then goes into the app. */
class SetPasswordActivity : BaseActivity() {
    private lateinit var pass: EditText
    private lateinit var again: EditText
    private lateinit var msg: TextView
    private lateinit var save: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.header(c, "Choose a password") { finish() }, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 24))

        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22))
        val intro = Ui.text(c, "Choose a password for your account. You will use it to sign in next time.", 14f, Ui.MUTED, Ui.REG)
        intro.setLineSpacing(0f, 1.25f)
        card.addView(intro)

        val p1 = AuthUi.inputRow(c, AuthUi.LOCK, "New password (6 or more)", true)
        pass = p1.second
        card.addTo(p1.first, 18)

        val p2 = AuthUi.inputRow(c, AuthUi.LOCK, "Type it again", true)
        again = p2.second
        again.imeOptions = EditorInfo.IME_ACTION_DONE
        card.addTo(p2.first, 14)

        save = Ui.pill(c, "Save password")
        card.addTo(save, 18)

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

        save.setOnClickListener { doSave() }
        again.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doSave()
                true
            } else {
                false
            }
        }
    }

    private fun showMsg(text: String) {
        msg.setTextColor(Ui.RED)
        msg.text = text
    }

    private fun doSave() {
        val p = pass.text.toString()
        if (p.length < 6) {
            showMsg("Use at least 6 characters.")
            return
        }
        if (p != again.text.toString()) {
            showMsg("The two passwords do not match.")
            return
        }
        save.isEnabled = false
        save.text = "Saving\u2026"
        showMsg("")
        Thread {
            val r = Api.setPassword(p)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (r.ok) {
                    startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                } else {
                    save.isEnabled = true
                    save.text = "Save password"
                    showMsg(r.message())
                }
            }
        }.start()
    }
}
