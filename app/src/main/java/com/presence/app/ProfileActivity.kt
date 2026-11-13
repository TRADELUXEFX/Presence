package com.presence.app

import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.presence.app.Ui.addTo

/** Profile: opened by tapping the app logo on Home. Holds account actions. */
class ProfileActivity : BaseActivity() {
    private lateinit var nameView: TextView
    private lateinit var emailView: TextView
    private lateinit var initialView: TextView
    private lateinit var photoView: ImageView

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
        root.addView(Ui.header(c, "Profile") { finish() }, LinearLayout.LayoutParams(-1, -2))

        val column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 24))

        // Who is signed in: Google photo (or the first letter), name and email
        val card = Ui.card(c)
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 24), Ui.dp(c, 18), Ui.dp(c, 22))
        val avatar = FrameLayout(c)
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Ui.BLUE)
        avatar.background = circle
        initialView = Ui.text(c, "", 34f, Ui.WHITE, Ui.BOLD)
        initialView.gravity = Gravity.CENTER
        avatar.addView(initialView, FrameLayout.LayoutParams(-1, -1))
        photoView = ImageView(c)
        photoView.scaleType = ImageView.ScaleType.CENTER_CROP
        photoView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        photoView.clipToOutline = true
        photoView.visibility = View.GONE
        avatar.addView(photoView, FrameLayout.LayoutParams(-1, -1))
        card.addView(avatar, LinearLayout.LayoutParams(Ui.dp(c, 88), Ui.dp(c, 88)))
        nameView = Ui.text(c, "", 20f, Ui.TEXT, Ui.BOLD)
        nameView.gravity = Gravity.CENTER
        card.addTo(nameView, 14)
        emailView = Ui.text(c, "", 14f, Ui.MUTED, Ui.REG)
        emailView.gravity = Gravity.CENTER
        card.addTo(emailView, 4)
        column.addTo(card, 0)

        // Log out and Delete account: both centered, full-width pills
        val logout = Ui.pill(c, "Log out")
        logout.setOnClickListener { confirmSignOut() }
        column.addTo(logout, 28)
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
        loadUser()
    }

    /** Name, email and photo come from the account (Google gives all three; email sign-ups give the name they typed). */
    private fun loadUser() {
        Thread {
            val u = Api.fetchUser() ?: return@Thread
            val email = u.optString("email", "")
            val meta = u.optJSONObject("user_metadata")
            val name = (meta?.optString("full_name", "") ?: "").ifEmpty { meta?.optString("name", "") ?: "" }
                .ifEmpty { email.substringBefore("@") }
            val photoUrl = (meta?.optString("avatar_url", "") ?: "").ifEmpty { meta?.optString("picture", "") ?: "" }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                nameView.text = name
                emailView.text = email
                initialView.text = name.take(1).uppercase()
            }
            if (photoUrl.isNotEmpty()) {
                val bmp = try {
                    java.net.URL(photoUrl).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                } catch (e: Exception) { null }
                if (bmp != null) runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    photoView.setImageBitmap(bmp)
                    photoView.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun confirmSignOut() {
        Dialogs.show(
            this,
            "Log out?",
            "Trainings not yet saved stay on this phone and sync the next time you log in to this account.",
            "Log out",
            "Cancel",
            onPrimary = { signOut() }
        )
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
