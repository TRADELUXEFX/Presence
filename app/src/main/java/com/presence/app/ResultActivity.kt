package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.presence.app.Ui.addTo

/** The results of a finished training, on their own page over Home. */
class ResultActivity : BaseActivity(), Engine.Listener {
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            finish()
            return
        }
        val c = this
        val uid = Session.userId
        val key = intent.getStringExtra("key") ?: Trainings.BLINK
        val accent = Trainings.accent(key)
        val ink = Color.parseColor("#0F172A")
        val ts = intent.getLongExtra("ts", 0L)
        val labels = intent.getStringArrayListExtra("labels") ?: arrayListOf()
        val values = intent.getStringArrayListExtra("values") ?: arrayListOf()
        val quote = intent.getStringExtra("quote") ?: ""

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.header(c, Trainings.name(key), accent, showBack = false) { }, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 22), Ui.dp(c, 24), Ui.dp(c, 22), Ui.dp(c, 28))
        body.gravity = Gravity.CENTER_HORIZONTAL

        val title = Ui.text(c, Trainings.doneTitle(key), 26f, Ui.TEXT, Ui.BOLD)
        title.gravity = Gravity.CENTER
        body.addView(title, LinearLayout.LayoutParams(-1, -2))
        val quoteText = Ui.text(c, quote, 14f, Ui.MUTED, Ui.REG)
        quoteText.gravity = Gravity.CENTER
        quoteText.setLineSpacing(0f, 1.3f)
        body.addView(quoteText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 12) })

        // day-streak milestone (7, 30, 100): shown once, on the training that reached it
        val streak = Profile.normalize(LocalStore.profile(uid)).optInt("streak", 0)
        val msg = Milestones.message(streak)
        if (msg != null && Session.prefs.getInt("milestone", 0) != streak) {
            Session.prefs.edit().putInt("milestone", streak).apply()
            val m = Ui.card(c, Trainings.soft(key))
            val mt = Ui.text(c, msg, 14f, Ui.TEXT, Ui.MED)
            mt.gravity = Gravity.CENTER
            mt.setLineSpacing(0f, 1.25f)
            m.addView(mt)
            val share = Ui.pill(c, "Share my streak", accent, ink)
            share.setOnClickListener { ShareCard.shareStreak(c, streak) }
            m.addView(share, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 12) })
            body.addView(m, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 20) })
        }

        // a session: what comes next, and the way on
        val inSession = TrainingSession.items.size > 1
        if (inSession) {
            val s = Ui.card(c, Trainings.soft(key))
            val st = Ui.text(c, "", 16f, Ui.TEXT, Ui.BOLD)
            st.gravity = Gravity.CENTER
            st.setLineSpacing(0f, 1.25f)
            s.addView(st)
            if (TrainingSession.hasNext()) {
                val nxt = TrainingSession.items[TrainingSession.index + 1]
                // the Engine starts the next training itself after 5 seconds, even with the screen off
                // "Up next" now sits on the countdown button below, above "Starts in 3"
                st.text = "Training ${TrainingSession.position()} done."
            } else {
                st.text = "Session complete: ${TrainingSession.items.size} trainings done."
                Recent.rememberSession(uid, TrainingSession.items, TrainingSession.name)
            }
            if (TrainingSession.hasNext()) {
                // the button is the countdown: it fills and counts 5 to 1, then the Engine starts the next training
                val nextName = Trainings.name(TrainingSession.items[TrainingSession.index + 1].key)
                s.addTo(CountdownButton(c, accent, ink, "Up next: $nextName"), 12)
            }
            body.addView(s, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 20) })
        }

        // the numbers: duration, total time, and so on
        val stats = Ui.card(c, Trainings.soft(key))
        for ((i, label) in labels.withIndex()) {
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(Ui.text(c, label, 14f, Ui.MUTED, Ui.REG), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Ui.text(c, values.getOrElse(i) { "" }, 15f, Ui.TEXT, Ui.BOLD))
            stats.addTo(row, if (i == 0) 0 else 12)
        }
        body.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 24) })

        // how it felt, saved against this training's record
        val feelLabel = Ui.text(c, "How did it feel?", 14f, Ui.MUTED, Ui.MED)
        feelLabel.gravity = Gravity.CENTER
        body.addView(feelLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 20) })
        val feelRow = LinearLayout(c)
        feelRow.orientation = LinearLayout.HORIZONTAL
        val feelButtons = ArrayList<View>()
        for ((i, label) in Feel.OPTIONS.withIndex()) {
            val b = LinearLayout(c)
            b.orientation = LinearLayout.HORIZONTAL
            b.gravity = Gravity.CENTER
            b.background = Ui.rounded(c, Ui.SOFT, 23)
            b.setPadding(Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12))
            b.isClickable = true
            b.addView(FsIconView(c, Feel.face(label), Ui.TEXT), LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)))
            b.addView(Ui.text(c, label, 14f, Ui.TEXT, Ui.BOLD), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(c, 6) })
            b.setOnClickListener {
                if (ts > 0L) Feel.set(uid, ts, label)
                for (other in feelButtons) other.alpha = if (other === b) 1f else 0.4f
                Toast.makeText(c, "Saved: $label", Toast.LENGTH_SHORT).show()
            }
            feelButtons.add(b)
            feelRow.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = Ui.dp(c, 8) })
        }
        body.addView(feelRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 10) })

        val done = Ui.pill(c, "Done", accent, ink)
        done.setOnClickListener {
            Engine.cancelSession()
            TrainingSession.clear()
            // Done always returns to Home, whichever screen the training was started from
            Nav.goHome(this)
        }
        body.addView(done, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 24) })

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        Engine.addListener(this)
        // The session may have moved on while the screen was off: show the training that is running now
        if (Engine.isActive()) openTraining()
    }

    override fun onStop() {
        Engine.removeListener(this)
        super.onStop()
    }

    override fun onDestroy() {
        // Back (or Done) leaves the session. A screen rotation or a locked phone does not come through here as finishing.
        if (isFinishing && !leaving) Engine.cancelSession()
        super.onDestroy()
    }

    /** The Engine started the next training of the session: follow it. */
    override fun onEngine() {
        if (Engine.running) openTraining()
    }

    private fun openTraining() {
        if (leaving || isFinishing) return
        leaving = true
        startActivity(Intent(this, TrainingActivity::class.java))
        finish()
    }
}
