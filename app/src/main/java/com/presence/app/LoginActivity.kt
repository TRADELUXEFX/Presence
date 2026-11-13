package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
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

    override val needsLogin: Boolean get() = false

    private var signUp = false
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var msg: TextView
    private lateinit var orText: TextView
    private lateinit var googleText: TextView
    private lateinit var submit: Button
    private lateinit var spin: SpinnerRingView
    private lateinit var busyPill: FrameLayout
    private lateinit var forgotRow: LinearLayout
    private lateinit var tabLogin: TextView
    private lateinit var tabSignUp: TextView
    private lateinit var formView: FrameLayout
    private lateinit var nameInput: EditText
    private lateinit var nameBox: LinearLayout
    private lateinit var emailBox: LinearLayout
    private lateinit var confirmInput: EditText
    private lateinit var confirmBox: LinearLayout
    private var googleBusy = false

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
        hero.setPadding(Ui.dp(c, 24), Ui.dp(c, 28), Ui.dp(c, 24), Ui.dp(c, 52))
        Ui.padInsets(hero, top = true)
        val ls = Ui.dp(c, 60)
        hero.addView(AuthUi.logo(c, 60), LinearLayout.LayoutParams(ls, ls))
        val name = Ui.text(c, "PRESENCE", 22f, Ui.WHITE, Ui.BOLD)
        name.letterSpacing = 0.1f
        name.gravity = Gravity.CENTER
        hero.addTo(name, 18)
        val tag = Ui.text(c, "Train your presence, one drill at a time.", 12f, Color.parseColor("#CCE4FF"), Ui.REG)
        tag.gravity = Gravity.CENTER
        hero.addTo(tag, 6)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))

        // Form card, overlapping the hero
        val card = Ui.card(c)
        card.background = Ui.rounded(c, Ui.SURFACE, 28)
        card.elevation = Ui.dp(c, 6).toFloat()
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 24), Ui.dp(c, 22), Ui.dp(c, 20))

        // Login | Sign up switch
        val tabs = LinearLayout(c)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.background = Ui.rounded(c, Ui.FIELD, 22, Ui.LINE, 1)
        tabs.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        tabLogin = Ui.text(c, "Login", 15f, Ui.WHITE, Ui.BOLD)
        tabSignUp = Ui.text(c, "Sign up", 15f, Ui.MUTED, Ui.BOLD)
        for (t in listOf(tabLogin, tabSignUp)) {
            t.gravity = Gravity.CENTER
            t.setPadding(0, Ui.dp(c, 11), 0, Ui.dp(c, 11))
            tabs.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        tabLogin.setOnClickListener { setMode(false) }
        tabSignUp.setOnClickListener { setMode(true) }
        card.addTo(tabs)


        val nameRow = AuthUi.inputRow(c, AuthUi.USER, "Your name", false)
        nameInput = nameRow.second
        nameInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        nameBox = nameRow.first
        card.addTo(nameBox, 22)
        val emailRow = AuthUi.inputRow(c, AuthUi.MAIL, "Email address", false)
        email = emailRow.second
        emailBox = emailRow.first
        card.addTo(emailBox, 22)
        val passRow = AuthUi.inputRow(c, AuthUi.LOCK, "Password", true)
        password = passRow.second
        password.imeOptions = EditorInfo.IME_ACTION_DONE
        card.addTo(passRow.first, 12)
        val confirmRow = AuthUi.inputRow(c, AuthUi.LOCK, "Confirm password", true)
        confirmInput = confirmRow.second
        confirmInput.imeOptions = EditorInfo.IME_ACTION_DONE
        confirmBox = confirmRow.first
        card.addTo(confirmBox, 12)

        submit = Ui.pill(c, "Login")
        submit.textSize = 17f
        submit.elevation = Ui.dp(c, 3).toFloat()
        // while signing in the button is swapped for this blue pill with a spinner
        spin = SpinnerRingView(c, Ui.WHITE, Color.parseColor("#55FFFFFF"), 3)
        busyPill = FrameLayout(c)
        busyPill.background = Ui.rounded(c, Ui.BLUE, 28)
        busyPill.visibility = View.GONE
        busyPill.addView(spin, FrameLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28), Gravity.CENTER))
        val submitWrap = FrameLayout(c)
        submitWrap.addView(submit, FrameLayout.LayoutParams(-1, -2))
        submitWrap.addView(busyPill, FrameLayout.LayoutParams(-1, Ui.dp(c, 56)))
        card.addTo(submitWrap, 22)

        msg = Ui.text(c, "", 12f, Ui.RED, Ui.MED)
        msg.gravity = Gravity.CENTER
        card.addTo(msg, 12)

        orText = Ui.text(c, "or", 12f, Ui.MUTED, Ui.REG)
        orText.gravity = Gravity.CENTER
        orText.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0)
        val orRow = LinearLayout(c)
        orRow.orientation = LinearLayout.HORIZONTAL
        orRow.gravity = Gravity.CENTER_VERTICAL
        val lineL = View(c); lineL.setBackgroundColor(Ui.LINE)
        val lineR = View(c); lineR.setBackgroundColor(Ui.LINE)
        orRow.addView(lineL, LinearLayout.LayoutParams(0, Ui.dp(c, 1), 1f))
        orRow.addView(orText)
        orRow.addView(lineR, LinearLayout.LayoutParams(0, Ui.dp(c, 1), 1f))
        card.addTo(orRow, 16)
        val g = LinearLayout(c)
        g.orientation = LinearLayout.HORIZONTAL
        g.gravity = Gravity.CENTER
        g.background = Ui.rounded(c, Ui.SURFACE, 28, Ui.LINE, 1)
        g.minimumHeight = Ui.dp(c, 56)
        g.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), 0)
        g.isClickable = true
        g.setOnClickListener { doGoogle() }
        val gLp = LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22))
        gLp.rightMargin = Ui.dp(c, 12)
        g.addView(GoogleGView(c), gLp)
        googleText = Ui.text(c, "Login with Google", 16f, Ui.TEXT, Ui.BOLD)
        g.addView(googleText)
        card.addTo(g, 16)

        forgotRow = LinearLayout(c)
        forgotRow.orientation = LinearLayout.HORIZONTAL
        forgotRow.gravity = Gravity.CENTER
        val forgotMuted = Ui.text(c, "Forgot your password? ", 14f, Ui.MUTED, Ui.REG)
        val forgotLink = Ui.text(c, "Reset it", 14f, Ui.BLUE, Ui.BOLD)
        forgotRow.addView(forgotMuted)
        forgotRow.addView(forgotLink)
        card.addTo(forgotRow, 14)


        val legalRow = LinearLayout(c)
        legalRow.orientation = LinearLayout.HORIZONTAL
        legalRow.gravity = Gravity.CENTER
        val privacy = Ui.text(c, "Privacy policy", 12f, Ui.MUTED, Ui.REG)
        privacy.setOnClickListener { startActivity(Intent(this, PrivacyActivity::class.java)) }
        val legalDot = Ui.text(c, "  \u00B7  ", 12f, Ui.MUTED, Ui.REG)
        val contactUs = Ui.text(c, "Contact us", 12f, Ui.MUTED, Ui.REG)
        contactUs.setOnClickListener { startActivity(Intent(this, ContactActivity::class.java)) }
        legalRow.addView(privacy)
        legalRow.addView(legalDot)
        legalRow.addView(contactUs)
        card.addTo(legalRow, 14)
        val cardLp = LinearLayout.LayoutParams(-1, -2)
        cardLp.setMargins(Ui.dp(c, 20), -Ui.dp(c, 30), Ui.dp(c, 20), Ui.dp(c, 24))
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
        formView = FrameLayout(c)
        formView.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        formView.addView(scrim)

        val frame = FrameLayout(c)
        frame.setBackgroundColor(Ui.BG)
        frame.addView(formView, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        submit.setOnClickListener { doSubmit() }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (signUp) confirmInput.requestFocus() else doSubmit()
                true
            } else {
                false
            }
        }
        confirmInput.setOnEditorActionListener { _, actionId, _ ->
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

        setMode(false)
    }

    private fun setMode(up: Boolean) {
        signUp = up
        msg.text = ""
        submit.text = if (up) "Create account" else "Login"
        nameBox.visibility = if (up) View.VISIBLE else View.GONE
        confirmBox.visibility = if (up) View.VISIBLE else View.GONE
        (emailBox.layoutParams as? LinearLayout.LayoutParams)?.topMargin = Ui.dp(this, if (up) 12 else 22)
        emailBox.requestLayout()
        tabLogin.background = if (!up) Ui.rounded(this, Ui.BLUE, 18) else null
        tabLogin.setTextColor(if (!up) Ui.WHITE else Ui.MUTED)
        tabSignUp.background = if (up) Ui.rounded(this, Ui.BLUE, 18) else null
        tabSignUp.setTextColor(if (up) Ui.WHITE else Ui.MUTED)
        googleText.text = if (up) "Sign up with Google" else "Login with Google"
        forgotRow.visibility = if (up) View.GONE else View.VISIBLE
    }

    /** Google sign-in through the phone's browser (Supabase). Returns via AuthCallbackActivity. */
    private fun openGoogleInBrowser() {
        msg.text = ""
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Api.googleSignInUrl())))
        } catch (e: Exception) {
            msg.setTextColor(Ui.RED)
            msg.text = "Could not open the browser for Google sign-in."
        }
    }

    /** Shows Google's account picker on top of the app first. Falls back to the browser sign-in (no SHA-1 needed) if Google rejects the app. */
    private fun doGoogle() {
        if (googleBusy) return
                googleBusy = true
        msg.text = ""
        val option = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
            .Builder(Api.GOOGLE_WEB_CLIENT_ID).build()
        val request = androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(option).build()
        androidx.credentials.CredentialManager.create(this).getCredentialAsync(
            this, request, null, androidx.core.content.ContextCompat.getMainExecutor(this),
            object : androidx.credentials.CredentialManagerCallback<androidx.credentials.GetCredentialResponse, androidx.credentials.exceptions.GetCredentialException> {
                override fun onResult(result: androidx.credentials.GetCredentialResponse) {
                    val cred = result.credential
                    if (cred is androidx.credentials.CustomCredential &&
                        cred.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val idToken = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                            .createFrom(cred.data).idToken
                        Thread {
                            val r = Api.signInWithGoogleIdToken(idToken)
                            runOnUiThread {
                                if (isFinishing) return@runOnUiThread
                                if (r.ok && Session.loggedIn()) {
                                    Sync.refresh {
                                        googleBusy = false
                                        if (!isFinishing) goHome()
                                    }
                                } else {
                                    googleBusy = false
                                    showMsg("Could not finish Google sign-in. Please try again.", false)
                                }
                            }
                        }.start()
                    } else {
                        googleBusy = false
                        showMsg("Google sign-in did not work. Please try again.", false)
                    }
                }
                override fun onError(e: androidx.credentials.exceptions.GetCredentialException) {
                    googleBusy = false
                    // always stay in the app: the account picker shows again on the next tap
                    if (e is androidx.credentials.exceptions.GetCredentialCancellationException) {
                        showMsg("Google sign-in was cancelled.", false)
                    } else if (e is androidx.credentials.exceptions.NoCredentialException) {
                        showMsg("No Google account found on this phone. Add one in Settings and try again.", false)
                    } else {
                        showMsg("Google sign-in did not work. Please try again.", false)
                    }
                }
            })
    }

    private fun showMsg(text: String, ok: Boolean) {
        msg.setOnClickListener(null)
        msg.isClickable = false
        msg.setTextColor(if (ok) Ui.GREEN else Ui.RED)
        msg.text = text
    }

    private fun busy(on: Boolean) {
        submit.isEnabled = !on
        submit.visibility = if (on) View.INVISIBLE else View.VISIBLE
        busyPill.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun doSubmit() {
        val e = email.text.toString().trim()
        val p = password.text.toString()
        if (!submit.isEnabled) return   // already working (keyboard Done pressed twice)
        if (e.isEmpty() || p.isEmpty()) {
            showMsg("Please fill in all fields.", false)
            return
        }
        val up = signUp
        var who = ""
        if (up) {
            if (p.length < 6) {
                showMsg("Password must be at least 6 characters.", false)
                return
            }
            if (p != confirmInput.text.toString()) {
                showMsg("Passwords do not match.", false)
                return
            }
            who = nameInput.text.toString().trim().ifEmpty { e.substringBefore("@") }
        }
        showMsg("", true)
        busy(true)
        Thread {
            val r = if (up) Api.signUp(e, p, who) else Api.signIn(e, p)
            val loggedIn = r.ok && Session.loggedIn()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (loggedIn) {
                    // keep the spinner going inside the button until the user's data is loaded,
                    // and only then open the app
                    Sync.refresh {
                        if (!isFinishing) {
                            busy(false)
                            goHome()
                        }
                    }
                } else if (r.ok) {
                    busy(false)
                    showMsg("Check your email and tap the link on this phone. It opens Presence and logs you in.", true)
                } else {
                    busy(false)
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
