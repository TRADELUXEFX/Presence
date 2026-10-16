package com.presence.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.presence.app.Ui.addTo
import org.json.JSONArray
import org.json.JSONObject

/** Your week: trainings, minutes, streak, the days you showed up, and a line per training. */
class WeeklySummaryActivity : BaseActivity() {
    private lateinit var column: LinearLayout

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
        root.addView(Ui.header(c, "Your week") { finish() }, LinearLayout.LayoutParams(-1, -2))

        column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 24))
        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        Ui.padInsets(root, bottom = true)
        setContentView(root)
        column.addView(Ui.text(c, "Loading your week...", 14f, Ui.MUTED, Ui.REG))
        load()
    }

    private fun load() {
        // opened from the Sunday reminder: show the week that just ended
        val lastWeek = intent.getBooleanExtra(Weekly.EXTRA_LAST_WEEK, false)
        Thread {
            val uid = Session.userId
            val fresh = Api.fetchSessions()
            // online: the server's history. Offline: the copy kept on this phone. Trainings not yet uploaded are added either way.
            val base = fresh ?: cached(uid)
            val rows = (base + LocalStore.pendingFor(uid)).distinctBy { it.optLong("ts") to it.optString("mod") }
            val streak = Profile.normalize(LocalStore.profile(uid)).optInt("streak", 0)
            val summary = Weekly.summarize(rows, streak, if (lastWeek) 1 else 0)
            runOnUiThread {
                if (!isFinishing) show(summary)
            }
        }.start()
    }

    private fun cached(uid: String): List<JSONObject> {
        val text = Session.prefs.getString("history_cache_$uid", null) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            arr.objects()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun tile(value: String, label: String): LinearLayout {
        val c = this
        val t = LinearLayout(c)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        t.background = Ui.rounded(c, Ui.SOFT, 16)
        t.setPadding(Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14))
        val v = Ui.text(c, value, 24f, Ui.TEXT, Ui.BOLD)
        v.gravity = Gravity.CENTER
        t.addView(v)
        val l = Ui.text(c, label, 12f, Ui.MUTED, Ui.MED)
        l.gravity = Gravity.CENTER
        t.addTo(l, 2)
        return t
    }

    private fun show(s: WeekSummary) {
        val c = this
        column.removeAllViews()

        column.addTo(Ui.sectionTitle(c, "Last 7 days"), 0)
        val minutes = if (s.totalSec <= 0L) 0L else Math.max(1L, (s.totalSec + 30L) / 60L)
        val tiles = LinearLayout(c)
        tiles.orientation = LinearLayout.HORIZONTAL
        val vals = listOf(Pair(s.count.toString(), "Trainings"), Pair(minutes.toString(), "Minutes"), Pair(s.streak.toString(), "Day streak"))
        for ((i, p) in vals.withIndex()) {
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            if (i > 0) lp.leftMargin = Ui.dp(c, 8)
            tiles.addView(tile(p.first, p.second), lp)
        }
        column.addTo(tiles, 8)

        // the days you showed up
        val days = Ui.card(c)
        days.addView(Ui.text(c, "You showed up on ${s.activeDays} of 7 days", 15f, Ui.TEXT, Ui.BOLD))
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        for (i in 0..6) {
            val on = s.dayTrained[i]
            val dot = Ui.text(c, s.dayLetters[i], 13f, if (on) Ui.WHITE else Ui.MUTED, Ui.BOLD)
            dot.gravity = Gravity.CENTER
            dot.background = Ui.rounded(c, if (on) Ui.BLUE else Ui.SOFT, 18)
            val lp = LinearLayout.LayoutParams(0, Ui.dp(c, 36), 1f)
            if (i > 0) lp.leftMargin = Ui.dp(c, 6)
            row.addView(dot, lp)
        }
        days.addTo(row, 12)
        val note = when {
            s.activeDays >= 7 -> "A perfect week."
            s.activeDays >= 4 -> "A strong week. Keep it going."
            s.activeDays >= 1 -> "Every session counts."
            else -> "No trainings in the last 7 days. Start one today."
        }
        days.addTo(Ui.text(c, note, 13f, Ui.MUTED, Ui.REG), 10)
        column.addTo(days, 14)

        // one line per training
        if (s.count > 0) {
            column.addTo(Ui.sectionTitle(c, "By training"), 22)
            val list = Ui.card(c)
            var first = true
            for (k in Trainings.ALL) {
                val n = s.modCount[k] ?: 0
                if (n == 0) continue
                val line = LinearLayout(c)
                line.orientation = LinearLayout.HORIZONTAL
                line.gravity = Gravity.CENTER_VERTICAL
                val dot = android.view.View(c)
                dot.background = Ui.rounded(c, Trainings.accent(k), 6)
                line.addView(dot, LinearLayout.LayoutParams(Ui.dp(c, 12), Ui.dp(c, 12)))
                val name = Ui.text(c, Trainings.name(k), 15f, Ui.TEXT, Ui.MED)
                val nlp = LinearLayout.LayoutParams(0, -2, 1f)
                nlp.leftMargin = Ui.dp(c, 10)
                line.addView(name, nlp)
                val mins = Math.max(1L, ((s.modSec[k] ?: 0L) + 30L) / 60L)
                line.addView(Ui.text(c, "$n x  \u00B7  $mins min", 14f, Ui.MUTED, Ui.MED))
                list.addTo(line, if (first) 0 else 14)
                first = false
            }
            column.addTo(list, 8)
        }

        // share cards: numbers only
        val shareWeek = Ui.pill(c, "Share my week")
        shareWeek.setOnClickListener { ShareCard.shareWeek(c, s) }
        column.addTo(shareWeek, 22)
        if (s.streak > 0) {
            val shareStreak = Ui.pill(c, "Share my streak", Ui.SOFT, Ui.TEXT)
            shareStreak.setOnClickListener { ShareCard.shareStreak(c, s.streak) }
            column.addTo(shareStreak, 10)
        }
    }
}
