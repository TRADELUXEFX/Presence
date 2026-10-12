package com.presence.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
        if (p.optInt("sessions", 0) == 0) {
            column.addTo(Ui.note(this, "No trainings yet. Start one from Home and it will show up here."), 8)
        }
        val doneToday = Trainings.ALL.count { p.optJSONObject("today_done")?.optBoolean(it, false) == true }
        val mods = p.optJSONObject("mods") ?: JSONObject()
        addGrid(listOf(
            statCard("Trainings", p.optInt("sessions", 0).toString()),
            statCard("Day streak", p.optInt("streak", 0).toString()),
            statCard("Full streak", p.optInt("full_streak", 0).toString()),
            statCard("Today", "$doneToday / 4")
        ), 16)
        column.addTo(statCard("Total training time", Fmt.duration(p.optLong("total_sec", 0L))), 12)
        addGrid(Trainings.ALL.map { statCard(Trainings.name(it), mods.optInt(it, 0).toString()) }, 12)
        column.addTo(weeklyCard(), 12)
    }

    private fun weeklyCard(): LinearLayout {
        val c = this
        val card = Ui.card(c, Ui.CARD_TINT)
        card.isClickable = true
        card.setOnClickListener { startActivity(Intent(c, WeeklyActivity::class.java)) }
        card.addView(Ui.text(c, "Weekly summary", 15f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(c, "Last 7 days, with a PDF report", 12f, Ui.MUTED_TINT, Ui.REG), 3)
        return card
    }

    /** Puts cards two to a row, with equal heights. A lone last card keeps its half width. */
    private fun addGrid(items: List<View>, topDp: Int) {
        val c = this
        var i = 0
        var first = true
        while (i < items.size) {
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in 0..1) {
                val lp = LinearLayout.LayoutParams(0, -1, 1f)
                if (j == 1) lp.leftMargin = Ui.dp(c, 12)
                if (i + j < items.size) row.addView(items[i + j], lp) else row.addView(View(c), lp)
            }
            column.addTo(row, if (first) topDp else 12)
            first = false
            i += 2
        }
    }

    /** One tinted tile: big number centred over a small label, as on PauzFirst. */
    private fun statCard(label: String, value: String): LinearLayout {
        val c = this
        val card = Ui.card(c, Ui.CARD_TINT)
        card.setPadding(Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16))
        card.gravity = Gravity.CENTER
        val v = Ui.text(c, value, 20f, Ui.TEXT, Ui.BOLD)
        v.gravity = Gravity.CENTER
        val l = Ui.text(c, label, 11f, Ui.MUTED_TINT, Ui.MED)
        l.gravity = Gravity.CENTER
        card.addView(v, LinearLayout.LayoutParams(-1, -2))
        card.addTo(l, 3)
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
        addGrid(rows.map { historyCard(it) }, 0)
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
        val card = Ui.card(c, Ui.CARD_TINT)
        card.addView(Ui.text(c, Trainings.name(key), 14f, Ui.TEXT, Ui.BOLD))
        val meta = metaText(key, r)
        if (meta.isNotEmpty()) card.addTo(Ui.text(c, meta, 11f, Ui.MUTED, Ui.REG), 2)
        card.addTo(Ui.text(c, Fmt.duration(r.optLong("sec", 0L)), 16f, Ui.BLUE, Ui.BOLD), 8)
        card.addTo(Ui.text(c, timeLabel(r.optLong("ts", 0L)), 11f, Ui.MUTED, Ui.REG), 2)
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
