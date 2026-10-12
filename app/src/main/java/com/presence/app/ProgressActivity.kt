package com.presence.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.presence.app.Ui.addTo
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Progress: Training Stats and Training History. Every stat and every history entry is its own card. */
class ProgressActivity : BaseActivity() {
    private lateinit var tabs: LinearLayout
    private lateinit var column: LinearLayout
    private var showHistory = false
    private var history: List<JSONObject>? = null
    private var historyLoading = false
    private var historyFailed = false

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
        root.addView(Ui.header(c, "Progress") { finish() }, LinearLayout.LayoutParams(-1, -2))

        tabs = LinearLayout(c)
        tabs.orientation = LinearLayout.HORIZONTAL
        val tabsWrap = LinearLayout(c)
        tabsWrap.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), 0)
        tabsWrap.addView(tabs, LinearLayout.LayoutParams(-1, -2))
        root.addView(tabsWrap, LinearLayout.LayoutParams(-1, -2))

        column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 24))
        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        Ui.padInsets(root, bottom = true)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun setTab(hist: Boolean) {
        showHistory = hist
        if (hist && history == null && !historyLoading) loadHistory()
        render()
    }

    private fun loadHistory() {
        historyLoading = true
        historyFailed = false
        Thread {
            val rows = Api.fetchSessions()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                historyLoading = false
                if (rows == null) historyFailed = true else history = rows
                render()
            }
        }.start()
    }

    private fun render() {
        renderTabs()
        column.removeAllViews()
        val p = Profile.normalize(LocalStore.profile(Session.userId))
        if (showHistory) renderHistory() else renderStats(p)
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        val c = this
        val options = listOf(Pair("Training Stats", false), Pair("Training History", true))
        for ((i, opt) in options.withIndex()) {
            val sel = showHistory == opt.second
            val btn = Ui.pill(c, opt.first, if (sel) Ui.BLUE else Ui.SOFT, if (sel) Ui.WHITE else Ui.TEXT)
            btn.textSize = 14f
            btn.setOnClickListener { setTab(opt.second) }
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            if (i > 0) lp.leftMargin = Ui.dp(c, 8)
            tabs.addView(btn, lp)
        }
    }

    private fun renderStats(p: JSONObject) {
        val weekly = Ui.pill(this, "Weekly summary", Ui.BLUE, Ui.WHITE)
        weekly.setOnClickListener { startActivity(Intent(this, WeeklyActivity::class.java)) }
        column.addTo(weekly, 0)
        if (p.optInt("sessions", 0) == 0) {
            column.addTo(Ui.note(this, "No trainings yet. Start one from Home and it will show up here."), 12)
        }
        column.addTo(statCard("Trainings completed", p.optInt("sessions", 0).toString(), "all time"), 12)
        column.addTo(statCard("Total training time", Fmt.duration(p.optLong("total_sec", 0L)), "all time"), 12)
        column.addTo(statCard("Day streak", p.optInt("streak", 0).toString(), "days you showed up"), 12)
        column.addTo(statCard("Full streak", p.optInt("full_streak", 0).toString(), "days with all 4 done"), 12)
        val doneToday = Trainings.ALL.count { p.optJSONObject("today_done")?.optBoolean(it, false) == true }
        column.addTo(statCard("Today", "$doneToday / 4", "trainings done today"), 12)
        val mods = p.optJSONObject("mods") ?: JSONObject()
        for (k in Trainings.ALL) {
            column.addTo(statCard(Trainings.name(k), mods.optInt(k, 0).toString(), "times completed"), 12)
        }
    }

    private fun statCard(label: String, value: String, sub: String): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.addView(Ui.text(c, label, 13f, Ui.MUTED, Ui.MED))
        card.addTo(Ui.text(c, value, 26f, Ui.BLUE, Ui.BOLD), 4)
        card.addTo(Ui.text(c, sub, 11f, Ui.MUTED, Ui.REG), 2)
        return card
    }

    private fun renderHistory() {
        val c = this
        if (historyLoading) {
            column.addTo(Ui.note(c, "Loading your trainings…"), 0)
            return
        }
        if (historyFailed) {
            column.addTo(historyErrorCard(), 0)
            return
        }
        val rows = history ?: return
        if (rows.isEmpty()) {
            column.addTo(Ui.note(c, "No trainings yet. Finish a training to see it here."), 0)
            return
        }
        for ((i, r) in rows.withIndex()) {
            column.addTo(historyCard(r), if (i == 0) 0 else 12)
        }
    }

    private fun historyErrorCard(): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.addView(Ui.text(c, "Couldn't load your trainings", 15f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(c, "Check your connection and try again.", 13f, Ui.MUTED, Ui.REG), 4)
        val btn = Ui.pill(c, "Try again")
        btn.setOnClickListener { loadHistory(); render() }
        card.addTo(btn, 14)
        return card
    }

    private fun historyCard(r: JSONObject): LinearLayout {
        val c = this
        val key = r.optString("mod")
        val card = Ui.card(c)
        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(c)
        left.orientation = LinearLayout.VERTICAL
        left.addView(Ui.text(c, Trainings.name(key), 16f, Ui.TEXT, Ui.BOLD))
        val meta = metaText(key, r)
        if (meta.isNotEmpty()) left.addTo(Ui.text(c, meta, 12f, Ui.MUTED, Ui.REG), 3)
        head.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.text(c, Fmt.duration(r.optLong("sec", 0L)), 15f, Ui.BLUE, Ui.BOLD))
        card.addView(head)
        card.addTo(Ui.text(c, timeLabel(r.optLong("ts", 0L)), 12f, Ui.MUTED, Ui.REG), 8)
        return card
    }

    private fun metaText(key: String, r: JSONObject): String {
        val parts = ArrayList<String>()
        when (key) {
            Trainings.MEDITATE -> parts.add("${r.optInt("med_min", 0)} min")
            Trainings.BREATH -> parts.add("${r.optInt("sets", 0)} cycles")
            else -> {
                val sets = r.optInt("sets", 0)
                if (sets > 0) parts.add("$sets sets")
                val rep = r.optInt("rep_sec", 0)
                if (rep > 0) parts.add("${rep}s per rep")
                val rest = r.optInt("rest_sec", -1)
                if (rest >= 0) parts.add("${rest}s rest")
                val mode = r.str("mode")
                if (mode != null && mode != "open") parts.add(Trainings.modeLabel(mode))
            }
        }
        return parts.joinToString(" · ")
    }

    /** "Today at 6:38 PM", "Yesterday at 9:02 AM", or "Oct 5, 2026 at 7:15 PM". */
    private fun timeLabel(ts: Long): String {
        val time = SimpleDateFormat("h:mm a", Locale.US).format(Date(ts))
        val d = Calendar.getInstance().apply { timeInMillis = ts }
        val now = Calendar.getInstance()
        if (sameDay(d, now)) return "Today at $time"
        now.add(Calendar.DAY_OF_MONTH, -1)
        if (sameDay(d, now)) return "Yesterday at $time"
        return SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ts)) + " at $time"
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
