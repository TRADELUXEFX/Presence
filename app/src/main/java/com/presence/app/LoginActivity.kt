package com.presence.app

import android.content.Intent
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

/** Login and sign up on one screen, like the website. */
class LoginActivity : BaseActivity() {
    private var signUp = false
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var heading: TextView
    private lateinit var tagline: TextView
    private lateinit var msg: TextView
    private lateinit var submit: Button
    private lateinit var spin: SpinnerRingView
    private lateinit var forgotRow: LinearLayout
    private lateinit var switchLabel: TextView
    private lateinit var switchLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        // Blue hero, running up behind the status bar
        val hero = AuthUi.HeroLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 36), Ui.dp(c, 24), Ui.dp(c, 64))
        Ui.padInsets(hero, top = true)
        val ls = Ui.dp(c, 88)
        hero.addView(AuthUi.logo(c, 88), LinearLayout.LayoutParams(ls, ls))
        val name = Ui.text(c, "PRESENCE", 30f, Ui.WHITE, Ui.BOLD)
        name.letterSpacing = 0.1f
        name.gravity = Gravity.CENTER
        hero.addTo(name, 18)
        val tag = Ui.text(c, "Presence Training", 13f, Color.parseColor("#CCE4FF"), Ui.REG)
        tag.gravity = Gravity.CENTER
        hero.addTo(tag, 6)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))

        // Form card, overlapping the hero
        val card = Ui.card(c)
        card.background = Ui.rounded(c, Ui.SURFACE, 28)
        card.elevation = Ui.dp(c, 6).toFloat()
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 24), Ui.dp(c, 22), Ui.dp(c, 20))

        heading = Ui.text(c, "Welcome Back", 24f, Ui.TEXT, Ui.BOLD)
        heading.gravity = Gravity.CENTER
        card.addTo(heading)
        tagline = Ui.text(c, "Login to get access to training", 14f, Ui.MUTED, Ui.REG)
        tagline.gravity = Gravity.CENTER
        card.addTo(tagline, 4)

        val emailRow = AuthUi.inputRow(c, AuthUi.MAIL, "Email address", false)
        email = emailRow.second
        card.addTo(emailRow.first, 22)
        val passRow = AuthUi.inputRow(c, AuthUi.LOCK, "Password", true)
        password = passRow.second
        password.imeOptions = EditorInfo.IME_ACTION_DONE
        card.addTo(passRow.first, 12)

        submit = Ui.pill(c, "Login")
        submit.textSize = 17f
        submit.elevation = Ui.dp(c, 3).toFloat()
        spin = SpinnerRingView(c, Ui.WHITE, Color.parseColor("#55FFFFFF"), 3)
        spin.visibility = View.GONE
        val submitWrap = FrameLayout(c)
        submitWrap.addView(submit, FrameLayout.LayoutParams(-1, -2))
        submitWrap.addView(spin, FrameLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26), Gravity.CENTER))
        card.addTo(submitWrap, 22)

        msg = Ui.text(c, "", 12f, Ui.RED, Ui.MED)
        msg.gravity = Gravity.CENTER
        card.addTo(msg, 12)

        forgotRow = LinearLayout(c)
        forgotRow.orientation = LinearLayout.HORIZONTAL
        forgotRow.gravity = Gravity.CENTER
        val forgotMuted = Ui.text(c, "Forgot your password? ", 14f, Ui.MUTED, Ui.REG)
        val forgotLink = Ui.text(c, "Reset it", 14f, Ui.BLUE, Ui.BOLD)
        forgotRow.addView(forgotMuted)
        forgotRow.addView(forgotLink)
        card.addTo(forgotRow, 14)

        val switchRow = LinearLayout(c)
        switchRow.orientation = LinearLayout.HORIZONTAL
        switchRow.gravity = Gravity.CENTER
        switchLabel = Ui.text(c, "Don't have an account? ", 14f, Ui.MUTED, Ui.REG)
        switchLink = Ui.text(c, "Sign up", 14f, Ui.BLUE, Ui.BOLD)
        switchRow.addView(switchLabel)
        switchRow.addView(switchLink)
        card.addTo(switchRow, 10)

        val cardLp = LinearLayout.LayoutParams(-1, -2)
        cardLp.setMargins(Ui.dp(c, 20), -Ui.dp(c, 36), Ui.dp(c, 20), 0)
        root.addView(card, cardLp)

        Ui.padInsets(root, bottom = true, ime = true)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(root)
        val scrim = Ui.statusScrim(c)
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            scrim.alpha = Math.min(1f, y / Ui.dp(c, 60).toFloat())
        }
        val frame = FrameLayout(c)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        frame.addView(scrim)
        setContentView(frame)

        submit.setOnClickListener { doSubmit() }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doSubmit()
                true
            } else {
                false
            }
        }
        forgotLink.setOnClickListener {
            val i = Intent(this, ForgotPasswordActivity::class.java)
            i.putExtra("email", email.text.toString().trim())
            startActivity(i)
        }
        switchLink.setOnClickListener { setMode(!signUp) }

        setMode(false)
    }

    private fun setMode(up: Boolean) {
        signUp = up
        msg.text = ""
        heading.text = if (up) "Create Account" else "Welcome Back"
        tagline.text = if (up) "Start your presence training journey" else "Login to get access to training"
        submit.text = if (up) "Sign Up" else "Login"
        switchLabel.text = if (up) "Already have an account? " else "Don't have an account? "
        switchLink.text = if (up) "Sign in" else "Sign up"
        forgotRow.visibility = if (up) View.GONE else View.VISIBLE
    }

    private fun showMsg(text: String, ok: Boolean) {
        msg.setTextColor(if (ok) Ui.GREEN else Ui.RED)
        msg.text = text
    }

    private fun busy(on: Boolean) {
        submit.isEnabled = !on
        submit.text = if (on) "" else (if (signUp) "Sign Up" else "Login")
        spin.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun doSubmit() {
        val e = email.text.toString().trim()
        val p = password.text.toString()
        if (e.isEmpty() || p.isEmpty()) {
            showMsg("Please fill in all fields.", false)
            return
        }
        showMsg("", true)
        busy(true)
        val up = signUp
        Thread {
            val r = if (up) Api.signUp(e, p) else Api.signIn(e, p)
            val loggedIn = r.ok && Session.loggedIn()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                busy(false)
                if (loggedIn) {
                    goHome()
                } else if (r.ok) {
                    showMsg("Check your email to confirm your account.", true)
                } else {
                    showMsg(r.message(), false)
                }
            }
        }.start()
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
