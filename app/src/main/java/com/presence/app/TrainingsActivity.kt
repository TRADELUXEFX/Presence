package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.presence.app.Ui.addTo

/** The Trainings tab: the four trainings, or your saved sessions, switched with the toggle. */
class TrainingsActivity : BaseActivity() {
    private lateinit var toggle: LinearLayout
    private lateinit var trainingsView: LinearLayout
    private lateinit var sessionsView: LinearLayout
    private var showSessions = false

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
        // blue header: the title, with the Trainings | Sessions switch inside it
        val headBox = LinearLayout(c)
        headBox.orientation = LinearLayout.VERTICAL
        val r = Ui.dp(c, 24).toFloat()
        val headBg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        headBg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        headBox.background = headBg
        val titleRow = Ui.header(c, "Trainings") { finish() }
        titleRow.background = null
        headBox.addView(titleRow, LinearLayout.LayoutParams(-1, -2))
        toggle = LinearLayout(c)
        val toggleWrap = LinearLayout(c)
        toggleWrap.setPadding(Ui.dp(c, 18), Ui.dp(c, 4), Ui.dp(c, 18), Ui.dp(c, 18))
        toggleWrap.addView(toggle, LinearLayout.LayoutParams(-1, -2))
        headBox.addView(toggleWrap, LinearLayout.LayoutParams(-1, -2))
        root.addView(headBox, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 24))
        trainingsView = LinearLayout(c)
        trainingsView.orientation = LinearLayout.VERTICAL
        body.addView(trainingsView, LinearLayout.LayoutParams(-1, -2))
        sessionsView = LinearLayout(c)
        sessionsView.orientation = LinearLayout.VERTICAL
        body.addView(sessionsView, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Nav.bar(c, 1), LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::toggle.isInitialized) render()
    }

    private fun render() {
        renderToggle()
        trainingsView.visibility = if (showSessions) View.GONE else View.VISIBLE
        sessionsView.visibility = if (showSessions) View.VISIBLE else View.GONE
        if (showSessions) renderSessions() else renderTrainings()
    }

    /** Two halves, like Training Stats | Training History: the one you pick is blue. */
    private fun renderToggle() {
        val c = this
        toggle.removeAllViews()
        toggle.orientation = LinearLayout.HORIZONTAL
        toggle.background = Ui.rounded(c, Color.parseColor("#33FFFFFF"), 26)
        toggle.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        for ((i, label) in listOf("Trainings", "Sessions").withIndex()) {
            val on = (i == 1) == showSessions
            val t = Ui.text(c, label, 14f, if (on) Ui.BLUE else Ui.WHITE, Ui.BOLD)
            t.gravity = Gravity.CENTER
            t.background = Ui.rounded(c, if (on) Ui.WHITE else Color.TRANSPARENT, 22)
            t.setPadding(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12))
            t.setOnClickListener {
                showSessions = i == 1
                render()
            }
            toggle.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun renderTrainings() {
        val c = this
        trainingsView.removeAllViews()
        for ((i, k) in Trainings.ALL.withIndex()) {
            val card = Ui.card(c)
            card.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14))
            val line = LinearLayout(c)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            val text = LinearLayout(c)
            text.orientation = LinearLayout.VERTICAL
            text.addView(Ui.text(c, Trainings.name(k), 15f, Ui.TEXT, Ui.BOLD))
            text.addTo(Ui.text(c, Trainings.about(k), 12f, Ui.MUTED, Ui.REG), 3)
            line.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val open = Ui.pill(c, "Open", Trainings.accent(k), Ui.onColor(Trainings.accent(k)))
            open.textSize = 14f
            open.setOnClickListener { openTraining(k) }
            val openLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40))
            openLp.leftMargin = Ui.dp(c, 10)
            line.addView(open, openLp)
            card.addView(line)
            card.setOnClickListener { openTraining(k) }
            trainingsView.addTo(card, if (i == 0) 0 else 12)
        }
    }

    private fun openTraining(key: String) {
        startActivity(Intent(this, ConfigActivity::class.java).putExtra("key", key))
    }

    private fun renderSessions() {
        val c = this
        sessionsView.removeAllViews()
        val build = Ui.pill(c, "+ Build a session", Ui.SOFT, Ui.TEXT)
        build.setOnClickListener { startActivity(Intent(c, SessionActivity::class.java)) }
        sessionsView.addTo(build, 0)
        val sessions = SavedLists.all(Session.userId).filter { it.optString("kind") == "session" }
        if (sessions.isEmpty()) {
            sessionsView.addTo(Ui.note(c, "No saved sessions yet. Build one, then tap Save session."), 14)
        }
        for (o in sessions) sessionsView.addTo(sessionRow(o), 12)
    }

    /** A saved session: its name, how many trainings and how long, then a pencil and Start. */
    private fun sessionRow(o: org.json.JSONObject): LinearLayout {
        val c = this
        val items = SavedLists.sessionItems(o)
        val secs = items.sumOf { TrainingSession.seconds(it) }
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16))
        val line = LinearLayout(c)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(c)
        left.orientation = LinearLayout.VERTICAL
        left.addView(Ui.text(c, o.optString("name"), 15f, Ui.TEXT, Ui.BOLD))
        left.addTo(Ui.text(c, "Session \u00B7 ${items.size} trainings \u00B7 ${Fmt.duration(secs)}", 12f, Ui.MUTED, Ui.REG), 3)
        line.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val pencil = Ui.pill(c, "Edit", Ui.SOFT, Ui.TEXT)
        pencil.textSize = 13f
        pencil.setOnClickListener {
            startActivity(Intent(c, SessionActivity::class.java).putExtra("listId", o.optString("id")))
        }
        val pencilLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40))
        pencilLp.leftMargin = Ui.dp(c, 8)
        line.addView(pencil, pencilLp)
        val tint = if (items.isEmpty()) Ui.BLUE else Trainings.accent(items[0].key)
        val start = Ui.pill(c, "Start", tint, Ui.onColor(tint))
        start.textSize = 14f
        start.setOnClickListener {
            TrainingSession.clear()
            TrainingSession.items.addAll(items)
            startActivity(Intent(c, ConfigActivity::class.java).putExtra("session", true))
        }
        val startLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40))
        startLp.leftMargin = Ui.dp(c, 8)
        line.addView(start, startLp)
        card.addView(line)
        return card
    }
}
