package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.presence.app.Ui.addTo
import org.json.JSONObject

/** Home: streaks, today's progress, and the four trainings. */
class HomeActivity : BaseActivity() {
    private lateinit var column: LinearLayout
    private var net: NetWatch? = null
    private var online = true
    private var loading = false
    private var failed = false

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

        val hero = AuthUi.HeroLayout(c)
        hero.orientation = LinearLayout.VERTICAL
        hero.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 28))
        Ui.padInsets(hero, top = true)

        val top = LinearLayout(c)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val titleRow = LinearLayout(c)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val logoLp = LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36))
        logoLp.rightMargin = Ui.dp(c, 10)
        titleRow.addView(AuthUi.logo(c, 36), logoLp)
        val title = Ui.text(c, "Presence", 20f, Ui.WHITE, Ui.BOLD)
        title.maxLines = 1
        titleRow.addView(title)
        top.addView(titleRow, LinearLayout.LayoutParams(0, -2, 1f))
        val bellLp = LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38))
        bellLp.rightMargin = Ui.dp(c, 8)
        top.addView(bellButton(), bellLp)
        top.addView(themeButton(), LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)))
        hero.addView(top, LinearLayout.LayoutParams(-1, -2))
        // Sits under the name: the logo (36dp) and its gap (10dp) are left clear.
        val subtitle = Ui.text(c, "Train your presence, one drill at a time.", 13f, Color.parseColor("#CCE4FF"), Ui.REG)
        val subLp = LinearLayout.LayoutParams(-1, -2)
        subLp.leftMargin = Ui.dp(c, 46)
        subLp.topMargin = Ui.dp(c, 6)
        hero.addView(subtitle, subLp)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))

        column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 20), Ui.dp(c, 6), Ui.dp(c, 20), Ui.dp(c, 32))
        root.addView(column, LinearLayout.LayoutParams(-1, -2))
        Ui.padInsets(root, bottom = true)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(root)
        Ui.noClip(root, column, scroll)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (!Session.loggedIn()) return
        Reminders.apply(this)
        online = NetWatch.isOnline(this)
        val watch = NetWatch(this) { up ->
            online = up
            if (up) {
                Sync.flush()
                loadNow()
            } else {
                render()
            }
        }
        watch.start()
        net = watch
        loadNow()
    }

    override fun onPause() {
        net?.stop()
        net = null
        super.onPause()
    }

    /** Shows what this phone has straight away, then refreshes from the server. */
    private fun loadNow() {
        val uid = Session.userId
        if (!LocalStore.hasProfile(uid)) {
            loading = true
            failed = false
        }
        render()
        Sync.refresh { ok ->
            if (isFinishing) return@refresh
            loading = false
            failed = !ok && !LocalStore.hasProfile(Session.userId)
            render()
        }
    }

    private fun render() {
        if (!::column.isInitialized) return
        val c = this
        column.removeAllViews()
        column.addTo(navRow(), 4)
        StreakWidget.updateAll(c)
        if (!online) {
            val banner = Ui.banner(c, "You're offline. Trainings are saved on this phone and sync when you're back online.")
            banner.visibility = View.VISIBLE
            column.addTo(banner, 14)
        }
        if (Engine.isActive()) column.addTo(resumeCard(), 16)
        when {
            loading -> column.addTo(loadingCard(), 16)
            failed -> column.addTo(errorCard(), 16)
            else -> {
                val p = Profile.normalize(LocalStore.profile(Session.userId))
                column.addTo(statRow(p), 16)
                column.addTo(todayCard(p), 16)
                column.addTo(sectionHeader(), 28)
                val mods = p.optJSONObject("mods") ?: JSONObject()
                for (k in Trainings.ALL) column.addTo(trainingCard(k, mods.optInt(k, 0)), 14)
                column.addTo(signOutButton(), 28)
            }
        }
    }

    /** Light/dark switch in the header. Saves the choice and restarts Home so every colour updates. */
    private fun themeButton(): View {
        val c = this
        val box = FrameLayout(c)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#33FFFFFF"))
        box.background = bg
        box.contentDescription = if (Ui.dark) "Switch to light mode" else "Switch to dark mode"
        box.addView(
            FsIconView(c, if (Ui.dark) Icons.SUN else Icons.MOON, Ui.WHITE),
            FrameLayout.LayoutParams(Ui.dp(c, 21), Ui.dp(c, 21), Gravity.CENTER)
        )
        box.setOnClickListener {
            Settings.setDark(!Ui.dark)
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        return box
    }

    private fun navRow(): LinearLayout {
        val c = this
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val progress = Ui.pill(c, "Progress", Ui.SOFT, Ui.TEXT)
        progress.setOnClickListener { startActivity(Intent(this, ProgressActivity::class.java)) }
        row.addView(progress, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    /** "TRAININGS" on the left, the Saved list link on the right. */
    private fun sectionHeader(): LinearLayout {
        val c = this
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(Ui.sectionTitle(c, "Trainings"), LinearLayout.LayoutParams(0, -2, 1f))
        val link = Ui.text(c, "Saved list  ›", 13f, Ui.BLUE, Ui.BOLD)
        link.setOnClickListener { startActivity(Intent(this, SavedListActivity::class.java)) }
        row.addView(link)
        return row
    }

    /** Bell in the header: opens Notifications. */
    private fun bellButton(): View {
        val c = this
        val box = FrameLayout(c)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#33FFFFFF"))
        box.background = bg
        box.contentDescription = "Notifications"
        box.addView(
            FsIconView(c, Icons.BELL, Ui.WHITE),
            FrameLayout.LayoutParams(Ui.dp(c, 21), Ui.dp(c, 21), Gravity.CENTER)
        )
        box.setOnClickListener { startActivity(Intent(this, NotificationsActivity::class.java)) }
        return box
    }

    private fun resumeCard(): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.addView(Ui.text(c, "Training in progress", 15f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(c, Trainings.name(Engine.config.key), 13f, Ui.MUTED, Ui.REG), 2)
        val btn = Ui.pill(c, "Return to training")
        btn.setOnClickListener { startActivity(Intent(this, TrainingActivity::class.java)) }
        card.addTo(btn, 12)
        return card
    }

    private fun loadingCard(): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.addView(SpinnerRingView(c), LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)))
        val label = Ui.text(c, "Loading your progress…", 14f, Ui.MUTED, Ui.MED)
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.leftMargin = Ui.dp(c, 14)
        card.addView(label, lp)
        return card
    }

    private fun errorCard(): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.addView(Ui.text(c, "Couldn't load your progress", 15f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(c, "Check your connection and try again.", 13f, Ui.MUTED, Ui.REG), 4)
        val btn = Ui.pill(c, "Try again")
        btn.setOnClickListener { loadNow() }
        card.addTo(btn, 14)
        return card
    }

    private fun statRow(p: JSONObject): LinearLayout {
        val c = this
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(
            statCard(p.optInt("streak", 0), "Day streak", "days you showed up", true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val lp2 = LinearLayout.LayoutParams(0, -2, 1f)
        lp2.leftMargin = Ui.dp(c, 12)
        row.addView(statCard(p.optInt("full_streak", 0), "Full streak", "all 4 done", false), lp2)
        return row
    }

    private fun statCard(value: Int, label: String, sub: String, flame: Boolean): LinearLayout {
        val c = this
        val card = Ui.card(c)
        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        if (flame) {
            val icon = FsIconView(c, Icons.FLAME, Ui.BLUE)
            val ilp = LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18))
            ilp.rightMargin = Ui.dp(c, 6)
            head.addView(icon, ilp)
        }
        head.addView(Ui.text(c, label, 13f, Ui.TEXT, Ui.MED))
        card.addView(head)
        card.addTo(Ui.text(c, value.toString(), 34f, Ui.BLUE, Ui.BOLD), 6)
        card.addTo(Ui.text(c, sub, 11f, Ui.MUTED, Ui.REG), 2)
        return card
    }

    private fun todayCard(p: JSONObject): LinearLayout {
        val c = this
        val doneMap = p.optJSONObject("today_done") ?: JSONObject()
        val done = Trainings.ALL.count { doneMap.optBoolean(it, false) }
        val card = Ui.card(c)

        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(Ui.sectionTitle(c, "Today"), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.text(c, "$done / 4", 16f, Ui.BLUE, Ui.BOLD))
        card.addView(head)

        // One segment per training, filled in the order they were finished, each in its own colour.
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.HORIZONTAL
        val order = Trainings.doneOrder(doneMap)
        for (i in 0 until 4) {
            val seg = View(c)
            seg.background = Ui.rounded(c, if (i < order.size) Trainings.accent(order[i]) else Ui.SOFT, 6)
            val segLp = LinearLayout.LayoutParams(0, -1, 1f)
            if (i > 0) segLp.leftMargin = Ui.dp(c, 4)
            bar.addView(seg, segLp)
        }
        val barLp = LinearLayout.LayoutParams(-1, Ui.dp(c, 8))
        barLp.topMargin = Ui.dp(c, 12)
        card.addView(bar, barLp)

        // Each dot sits beside its label on one line, as on the web app. Two rows of two.
        val grid = LinearLayout(c)
        grid.orientation = LinearLayout.VERTICAL
        for ((r, pair) in Trainings.ALL.chunked(2).withIndex()) {
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            for (k in pair) {
                val item = LinearLayout(c)
                item.orientation = LinearLayout.HORIZONTAL
                item.gravity = Gravity.CENTER_VERTICAL
                val dot = View(c)
                dot.background = circle(Trainings.accent(k))
                dot.alpha = if (doneMap.optBoolean(k, false)) 1f else 0.25f
                item.addView(dot, LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10)))
                val label = LinearLayout.LayoutParams(-2, -2)
                label.leftMargin = Ui.dp(c, 8)
                item.addView(Ui.text(c, dotLabel(k), 13f, Ui.MUTED, Ui.MED), label)
                row.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
            }
            grid.addTo(row, if (r == 0) 0 else 10)
        }
        card.addTo(grid, 14)
        return card
    }

    private fun trainingCard(k: String, count: Int): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.isClickable = true
        card.setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java).putExtra("key", k))
        }
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val dot = View(c)
        dot.background = circle(Trainings.accent(k))
        val dlp = LinearLayout.LayoutParams(Ui.dp(c, 12), Ui.dp(c, 12))
        dlp.rightMargin = Ui.dp(c, 14)
        row.addView(dot, dlp)

        val text = LinearLayout(c)
        text.orientation = LinearLayout.VERTICAL
        text.addView(Ui.text(c, Trainings.name(k), 16f, Ui.TEXT, Ui.BOLD))
        val blurb = Ui.text(c, Trainings.blurb(k), 12f, Ui.MUTED, Ui.REG)
        blurb.setLineSpacing(0f, 1.2f)
        val blp = LinearLayout.LayoutParams(-1, -2)
        blp.topMargin = Ui.dp(c, 3)
        text.addView(blurb, blp)
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))

        row.addView(
            Ui.chip(c, if (count > 0) "${count}x" else "-", Ui.BLUE, Ui.BLUE_SOFT),
            LinearLayout.LayoutParams(-2, -2)
        )
        val chev = FsIconView(c, Icons.CHEVRON, Ui.MUTED)
        val clp = LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18))
        clp.leftMargin = Ui.dp(c, 10)
        row.addView(chev, clp)

        card.addView(row)
        return card
    }

    private fun dotLabel(k: String): String = when (k) {
        Trainings.BLINK -> "No Blinking"
        Trainings.FIDGET -> "No Fidgeting"
        Trainings.BREATH -> "Breathing"
        else -> "Meditation"
    }

    /** Sign out sits at the bottom of Home, below the trainings. */
    private fun signOutButton(): View {
        val c = this
        val btn = Ui.pill(c, "Sign out", Ui.SOFT, Ui.RED)
        btn.setOnClickListener { confirmSignOut() }
        return btn
    }

    private fun confirmSignOut() {
        Dialogs.show(
            this,
            "Sign out?",
            "Trainings not yet saved stay on this phone and sync the next time you log in to this account.",
            "Sign out",
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

    private fun circle(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        return g
    }
}
