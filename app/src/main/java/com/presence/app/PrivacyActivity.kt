package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.presence.app.Ui.addTo

/** The privacy policy, shown inside the app in the same look as the other screens. */
class PrivacyActivity : BaseActivity() {
    private class Item(val head: String?, val body: String)
    private class Sec(val title: String, val icon: String, val items: List<Item>)

    companion object {
        const val PRIVACY_URL = "https://presence-privacy.netlify.app/"
        const val ICON_GLOBE = "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M2 12h20 M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"
        const val ICON_DATA = "M12 3c4.97 0 9 1.34 9 3s-4.03 3-9 3-9-1.34-9-3 4.03-3 9-3z M3 6v12c0 1.66 4.03 3 9 3s9-1.34 9-3V6 M3 12c0 1.66 4.03 3 9 3s9-1.34 9-3"
        const val ICON_SHIELD = "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"
        const val ICON_USE = "M22 12h-4l-3 9L9 3l-3 9H2"
        const val ICON_LINK = "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71 M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
        const val ICON_TRASH = "M3 6h18 M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6 M10 11v6 M14 11v6 M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"
        const val ICON_USERS = "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M23 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75"
        const val ICON_REFRESH = "M1 4v6h6 M23 20v-6h-6 M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"
        const val ICON_MAIL = "M4 6h16v12H4z M4 7l8 6 8-6"
    }

    private val email = "socialmediaprofitautomation@gmail.com"
    private val sections = listOf(
        Sec("Data we collect", ICON_DATA, listOf(Item("Account data", "Your name, email address and password (stored securely by Supabase), or your Google account name and email if you sign in with Google."), Item("App data", "Your profile, training sessions, history and stats."))),
        Sec("Permissions", ICON_SHIELD, listOf(Item("Camera and microphone", "Used only if you choose to record a session. Recordings stay on your device and are not uploaded."), Item("Notifications and alarms", "Used for the reminders you set."), Item("Internet", "Used to sign in and sync your data."))),
        Sec("How we use your data", ICON_USE, listOf(Item(null, "Only to run the app: sign you in, save and sync your data, and send your reminders. We do not sell your data and we do not show ads."))),
        Sec("Third-party services", ICON_LINK, listOf(Item("Supabase", "Authentication and database hosting."), Item("Google Sign-In", "Optional sign-in."))),
        Sec("Retention and deletion", ICON_TRASH, listOf(Item(null, "Your data is kept while your account exists. You can delete your account and all its data from the Profile screen, or email us and we will delete it."))),
        Sec("Children", ICON_USERS, listOf(Item(null, "The app is not directed at children under 13, and we do not knowingly collect data from them."))),
        Sec("Changes", ICON_REFRESH, listOf(Item(null, "If this policy changes, the date at the top will be updated."))),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        // ---------- blue hero with back arrow ----------
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
        val title = Ui.text(c, "Privacy policy", 24f, Ui.WHITE, Ui.BOLD)
        title.gravity = Gravity.CENTER
        hero.addTo(title, 8)
        val sub = Ui.text(c, "Presence · Last updated November 13, 2026", 12f, Color.parseColor("#CCE4FF"), Ui.REG)
        sub.gravity = Gravity.CENTER
        hero.addTo(sub, 6)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))

        // ---------- sections ----------
        val column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 20), Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 24))
        var first = true
        for (s in sections) {
            column.addTo(sectionHead(s.title, s.icon), if (first) 0 else 22)
            first = false
            val card = Ui.card(c)
            card.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 18))
            var firstItem = true
            for (item in s.items) {
                if (item.head != null) {
                    card.addTo(Ui.text(c, item.head, 14f, Ui.TEXT, Ui.BOLD), if (firstItem) 0 else 14)
                    val b = Ui.text(c, item.body, 13f, Ui.MUTED, Ui.REG)
                    b.setLineSpacing(0f, 1.25f)
                    card.addTo(b, 4)
                } else {
                    val b = Ui.text(c, item.body, 14f, Ui.MUTED, Ui.REG)
                    b.setLineSpacing(0f, 1.25f)
                    card.addTo(b, if (firstItem) 0 else 10)
                }
                firstItem = false
            }
            column.addTo(card, 10)
        }

        // ---------- contact ----------
        column.addTo(sectionHead("Contact", ICON_MAIL), 22)
        val contact = Ui.card(c)
        val mail = Ui.text(c, email, 15f, Ui.BLUE, Ui.BOLD)
        mail.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email))) } catch (e: Exception) { }
        }
        contact.addTo(mail, 0)
        column.addTo(contact, 10)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.visibility = android.view.View.GONE

        // ---------- the live page, shown inside the app ----------
        val web = android.webkit.WebView(c)
        web.setBackgroundColor(Ui.BG)
        web.visibility = android.view.View.GONE
        web.settings.javaScriptEnabled = false
        web.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(view: android.webkit.WebView, request: android.webkit.WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "mailto") {
                    try { startActivity(Intent(Intent.ACTION_SENDTO, u)) } catch (e: Exception) { }
                }
                return true // never leave the app from here
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        loadOnline(web, scroll)
        Ui.padInsets(root, bottom = true)
        setContentView(root)
    }

    /** Small blue icon badge followed by the uppercase section label. */
    private fun sectionHead(title: String, icon: String): LinearLayout {
        val c = this
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val badge = FrameLayout(c)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Ui.BLUE_SOFT)
        badge.background = bg
        badge.addView(FsIconView(c, icon, Ui.BLUE), FrameLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16), Gravity.CENTER))
        row.addView(badge, LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)))
        val label = Ui.sectionTitle(c, title)
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.leftMargin = Ui.dp(c, 10)
        row.addView(label, lp)
        return row
    }

    private fun hex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    /** Fetches the live page; if the phone is offline the built-in copy of the same text is shown instead. */
    private fun loadOnline(web: android.webkit.WebView, fallback: View) {
        Thread {
            var html: String? = null
            try {
                val con = java.net.URL(PRIVACY_URL).openConnection() as java.net.HttpURLConnection
                con.connectTimeout = 5000
                con.readTimeout = 5000
                if (con.responseCode == 200) html = con.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) { }
            val page = html
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (page != null && page.contains("</head>")) {
                    // the app already has its own blue header, so the page's one is hidden and its colors follow the app's light/dark mode
                    val style = "<style>.hero{display:none}:root{--bg:" + hex(Ui.BG) + ";--card:" + hex(Ui.SURFACE) +
                        ";--text:" + hex(Ui.TEXT) + ";--muted:" + hex(Ui.MUTED) + ";}</style></head>"
                    web.loadDataWithBaseURL(PRIVACY_URL, page.replace("</head>", style), "text/html", "utf-8", null)
                    web.visibility = View.VISIBLE
                } else {
                    fallback.visibility = View.VISIBLE
                }
            }
        }.start()
    }
}
