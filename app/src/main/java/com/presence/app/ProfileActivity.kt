package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.text.InputType
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.presence.app.Ui.addTo

/** Profile: opened by tapping the app logo on Home. Name, email and account actions. */
class ProfileActivity : BaseActivity() {
    private lateinit var nameView: TextView
    private lateinit var emailView: TextView
    private lateinit var methodView: TextView
    private lateinit var passRow: TextView
    private lateinit var avatarView: android.widget.ImageView
    private var currentName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        val c = this
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        // ---------- hero: user icon, name, email ----------
        val hero = AuthUi.HeroLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER_HORIZONTAL
        hero.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 32))
        Ui.padInsets(hero, top = true)
        val back = FrameLayout(c)
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Color.parseColor("#33FFFFFF"))
        back.background = circle
        back.contentDescription = "Back"
        back.setOnClickListener { finish() }
        back.addView(FsIconView(c, "M15 18l-6-6 6-6", Ui.WHITE), FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER))
        val bl = LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40))
        bl.gravity = Gravity.START
        hero.addView(back, bl)

        val av = FrameLayout(c)
        val avBg = GradientDrawable()
        avBg.shape = GradientDrawable.OVAL
        avBg.setColor(Ui.WHITE)
        av.background = avBg
        av.addView(FsIconView(c, AuthUi.USER, Ui.BLUE), FrameLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46), Gravity.CENTER))
        avatarView = Avatar.view(c)
        av.addView(avatarView, FrameLayout.LayoutParams(-1, -1))
        val al = LinearLayout.LayoutParams(Ui.dp(c, 88), Ui.dp(c, 88))
        al.gravity = Gravity.CENTER_HORIZONTAL
        al.topMargin = Ui.dp(c, 4)
        hero.addView(av, al)
        nameView = Ui.text(c, "", 24f, Ui.WHITE, Ui.BOLD)
        nameView.gravity = Gravity.CENTER
        hero.addTo(nameView, 12)
        emailView = Ui.text(c, "", 14f, Color.parseColor("#CCE4FF"), Ui.REG)
        emailView.gravity = Gravity.CENTER
        hero.addTo(emailView, 2)
        methodView = Ui.text(c, "", 12f, Color.parseColor("#CCE4FF"), Ui.REG)
        methodView.gravity = Gravity.CENTER
        hero.addTo(methodView, 6)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))

        val column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 20), Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 24))

        val editName = row("Edit name")
        editName.setOnClickListener { editNameDialog() }
        column.addTo(editName, 0)
        passRow = row("Change password")
        passRow.setOnClickListener { changePasswordDialog() }
        column.addTo(passRow, 12)

        // Log out and Delete account: both centered, full-width pills
        val logout = Ui.pill(c, "Log out", Ui.BLUE_SOFT, Ui.BLUE)
        logout.setOnClickListener { signOut() }
        column.addTo(logout, 36)
        val delete = Ui.pill(c, "Delete account", Ui.RED)
        delete.setOnClickListener { confirmDelete() }
        column.addTo(delete, 14)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        Ui.padInsets(root, bottom = true)
        setContentView(root)
        Avatar.show(c, avatarView, Session.prefs.getString("u_avatar", "") ?: "")
        loadUser()
    }

    private fun row(label: String): TextView {
        val c = this
        val t = Ui.text(c, label, 15f, Ui.TEXT, Ui.MED)
        t.background = Ui.rounded(c, Ui.SURFACE, 18, Ui.LINE, 1)
        t.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16))
        return t
    }

    /** Name and email come from the account (Google gives both; email sign-ups give the name they typed). */
    private fun loadUser() {
        Thread {
            val u = Api.fetchUser() ?: return@Thread
            val email = u.optString("email", "")
            val meta = u.optJSONObject("user_metadata")
            val name = (meta?.optString("full_name", "") ?: "").ifEmpty { meta?.optString("name", "") ?: "" }
                .ifEmpty { email.substringBefore("@") }
            val pic = (meta?.optString("avatar_url", "") ?: "").ifEmpty { meta?.optString("picture", "") ?: "" }
            if (pic.isNotEmpty()) Session.prefs.edit().putString("u_avatar", pic).apply()
            val provider = u.optJSONObject("app_metadata")?.optString("provider", "email") ?: "email"
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                currentName = name
                Avatar.show(this, avatarView, pic)
                nameView.text = name
                emailView.text = email
                methodView.text = if (provider == "email") "Signed in with email" else "Signed in with Google"
                // Google users have no password to change
                passRow.visibility = if (provider == "email") View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun editNameDialog() {
        val input = Ui.input(this, "Your name", false)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        input.setText(currentName)
        input.setSelection(input.text.length)
        Dialogs.show(this, "Edit name", "", "Save", "Cancel", onPrimary = {
            val n = input.text.toString().trim()
            if (n.isNotEmpty()) Thread {
                val r = Api.updateName(n)
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    if (r.ok) { currentName = n; nameView.text = n }
                    else Dialogs.show(this, "Could not save the name", r.message(), "OK")
                }
            }.start()
        }, content = input)
    }

    private fun changePasswordDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val p1 = Ui.input(this, "New password", true)
        val p2 = Ui.input(this, "Confirm new password", true)
        box.addTo(p1, 0)
        box.addTo(p2, 10)
        Dialogs.show(this, "Change password", "", "Save", "Cancel", onPrimary = {
            val a = p1.text.toString()
            if (a.length < 6) {
                Dialogs.show(this, "Password too short", "Your password needs at least 6 characters.", "OK")
            } else if (a != p2.text.toString()) {
                Dialogs.show(this, "Passwords do not match", "Type the same password twice.", "OK")
            } else Thread {
                val r = Api.setPassword(a)
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    if (r.ok) Dialogs.show(this, "Password changed", "Use your new password next time you log in.", "OK")
                    else Dialogs.show(this, "Could not change the password", r.message(), "OK")
                }
            }.start()
        }, content = box)
    }

    private fun signOut() {
        Reminders.cancel(this)
        Session.clear()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    private fun confirmDelete() {
        Dialogs.show(
            this,
            "Delete account?",
            "This permanently deletes your account and all your training history. It cannot be undone.",
            "Delete",
            "Cancel",
            onPrimary = { deleteAccount() },
            accent = Ui.RED
        )
    }

    private fun deleteAccount() {
        Thread {
            val r = Api.deleteAccount()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (r.ok) {
                    Reminders.cancel(this)
                    Session.clear()
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                } else {
                    Dialogs.show(this, "Could not delete account", r.message(), "OK")
                }
            }
        }.start()
    }
}
