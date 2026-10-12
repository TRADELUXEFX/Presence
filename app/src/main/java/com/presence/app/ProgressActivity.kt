package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.FileProvider
import com.presence.app.Ui.addTo
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Progress, laid out like PauzFirst's history screen.
 * Training Stats: tinted tiles. Training History: filters, summary tiles, full-width cards, export.
 */
class ProgressActivity : BaseActivity() {
    private lateinit var tabs: LinearLayout
    private lateinit var column: LinearLayout
    private lateinit var exportBar: LinearLayout
    private lateinit var exportBtn: Button
    private var showHistory = false
    private var history: List<JSONObject>? = null
    private var historyLoading = false
    private var historyFailed = false
    private var filterMod: String? = null   // null = all trainings
    private var rangeDays = 7               // 7, 30, or 0 for all time
    private var exportable: List<JSONObject> = emptyList()

    private val DAY = 24L * 60 * 60 * 1000

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
        tabs.orientation = LinearLayout.VERTICAL
        val tabsWrap = LinearLayout(c)
        tabsWrap.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 4))
        tabsWrap.addView(tabs, LinearLayout.LayoutParams(-1, -2))
        root.addView(tabsWrap, LinearLayout.LayoutParams(-1, -2))

        column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 18), Ui.dp(c, 24))
        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        exportBar = LinearLayout(c)
        exportBar.orientation = LinearLayout.VERTICAL
        exportBar.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14))
        Ui.bottomBarBg(c, exportBar)
        exportBtn = Ui.pill(c, "Export history")
        exportBtn.setOnClickListener { exportHistory(exportable) }
        exportBar.addView(exportBtn, LinearLayout.LayoutParams(-1, -2))
        exportBar.visibility = View.GONE
        root.addView(exportBar, LinearLayout.LayoutParams(-1, -2))
        Ui.padInsets(exportBar, bottom = true)
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
        exportBar.visibility = if (showHistory) View.VISIBLE else View.GONE
        if (!showHistory && history == null && !historyLoading && !historyFailed) loadHistory()
        val p = Profile.normalize(LocalStore.profile(Session.userId))
        if (showHistory) renderHistory() else renderStats(p)
    }

    /** A rounded switch with one highlighted, as on PauzFirst. */
    private fun segment(labels: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val c = this
        val box = LinearLayout(c)
        box.orientation = LinearLayout.HORIZONTAL
        box.background = Ui.rounded(c, Ui.SOFT, 26)
        box.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        for ((i, label) in labels.withIndex()) {
            val sel = i == selected
            val t = Ui.text(c, label, 14f, if (sel) Ui.WHITE else Ui.MUTED, Ui.BOLD)
            t.gravity = Gravity.CENTER
            t.background = Ui.rounded(c, if (sel) Ui.BLUE else Color.TRANSPARENT, 22)
            t.setPadding(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12))
            t.setOnClickListener { onPick(i) }
            box.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return box
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        tabs.addView(
            segment(listOf("Training Stats", "Training History"), if (showHistory) 1 else 0) { setTab(it == 1) },
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    // ---------- Training Stats ----------

    private fun renderStats(p: JSONObject) {
        renderPractice()
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
    }

    private var practiceMonth = false

    /** "Your practice": bar chart of the last 7 or 30 days, as on PauzFirst. */
    private fun renderPractice() {
        val c = this
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18))
        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(Ui.sectionTitle(c, "Your practice"), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(
            segment(listOf("Week", "Month"), if (practiceMonth) 1 else 0) {
                practiceMonth = it == 1
                render()
            },
            LinearLayout.LayoutParams(Ui.dp(c, 170), -2)
        )
        card.addView(head)

        val all = history
        if (all == null) {
            if (historyFailed) {
                card.addTo(Ui.text(c, "Couldn't load your practice. Check your connection.", 13f, Ui.MUTED, Ui.REG), 14)
            } else {
                card.addTo(Loading.inline(c), 14)
            }
            column.addTo(card, 0)
            return
        }

        val days = if (practiceMonth) 30 else 7
        val now = System.currentTimeMillis()
        val secs = LongArray(days)
        for (i in 0 until days) {
            val key = dayKeyOf(dayStart(days - 1 - i))
            secs[i] = all.filter { dayKeyOf(it.optLong("ts", 0L)) == key }.sumOf { it.optLong("sec", 0L) }
        }
        val total = secs.sum()
        val maxSec = Math.max(1L, secs.max() ?: 1L)

        card.addTo(Ui.text(
            c,
            "${Fmt.duration(total)} this ${if (practiceMonth) "month" else "week"}",
            15f, Ui.TEXT, Ui.MED
        ), 14)

        // Bars share one baseline; the value sits on top of each bar.
        val chart = LinearLayout(c)
        chart.orientation = LinearLayout.HORIZONTAL
        chart.gravity = Gravity.BOTTOM
        val labels = LinearLayout(c)
        labels.orientation = LinearLayout.HORIZONTAL
        val barW = if (practiceMonth) 6 else 14
        for (i in 0 until days) {
            val col = LinearLayout(c)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            if (!practiceMonth && secs[i] > 0) {
                col.addView(Ui.text(c, shortMinutes(secs[i]), 11f, Ui.MUTED, Ui.REG))
            }
            val h = if (secs[i] == 0L) Ui.dp(c, 4) else Math.max(Ui.dp(c, 6), (Ui.dp(c, 120).toLong() * secs[i] / maxSec).toInt())
            val bar = View(c)
            bar.background = Ui.rounded(c, if (secs[i] == 0L) Ui.BLUE_SOFT else Ui.BLUE, 6)
            col.addView(bar, LinearLayout.LayoutParams(Ui.dp(c, barW), h))
            chart.addView(col, LinearLayout.LayoutParams(0, -1, 1f))

            val isToday = i == days - 1
            val label = if (practiceMonth) {
                val dom = Calendar.getInstance().apply { timeInMillis = dayStart(days - 1 - i) }.get(Calendar.DAY_OF_MONTH)
                if (dom % 5 == 1 || isToday) dom.toString() else ""
            } else {
                SimpleDateFormat("EEE", Locale.US).format(Date(dayStart(days - 1 - i))).take(1)
            }
            val t = Ui.text(c, label, 12f, if (isToday) Ui.TEXT else Ui.MUTED, if (isToday) Ui.BOLD else Ui.REG)
            t.gravity = Gravity.CENTER
            labels.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val chartLp = LinearLayout.LayoutParams(-1, Ui.dp(c, 150))
        chartLp.topMargin = Ui.dp(c, 14)
        card.addView(chart, chartLp)
        card.addTo(labels, 8)
        column.addTo(card, 0)

        if (secs[days - 1] > 0) {
            column.addTo(Ui.text(c, "Great job! You practiced today.", 14f, Ui.MUTED, Ui.REG).apply {
                gravity = Gravity.CENTER
            }, 16)
        }
    }

    /** Local midnight, offset days before today. */
    private fun dayStart(offsetDays: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, -offsetDays)
        return cal.timeInMillis
    }

    private fun dayKeyOf(ms: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun shortMinutes(sec: Long): String = if (sec < 60) "${sec}s" else "${sec / 60}m"

    /** Puts tiles two to a row, with equal heights. A lone last tile keeps its half width. */
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

    // ---------- Training History ----------

    private fun renderHistory() {
        val c = this
        if (historyLoading) {
            exportBtn.alpha = 0.4f
            column.addTo(Loading.page(c), 0)
            return
        }
        if (historyFailed) {
            exportBtn.alpha = 0.4f
            column.addTo(historyErrorCard(), 0)
            return
        }
        val all = history ?: return

        val chips = HorizontalScrollView(c)
        chips.isHorizontalScrollBarEnabled = false
        chips.addView(filterRow())
        chips.clipToPadding = false
        chips.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0)
        val chipsLp = Ui.lp(c, 0)
        chipsLp.leftMargin = -Ui.dp(c, 18)
        chipsLp.rightMargin = -Ui.dp(c, 18)
        column.addView(chips, chipsLp)

        val rangeIndex = when (rangeDays) { 7 -> 0; 30 -> 1; else -> 2 }
        column.addTo(
            segment(listOf("7 days", "30 days", "All time"), rangeIndex) { i ->
                rangeDays = when (i) { 0 -> 7; 1 -> 30; else -> 0 }
                render()
            },
            14
        )

        val list = filtered(all)
        exportable = list
        exportBtn.alpha = if (list.isEmpty()) 0.4f else 1f

        val now = System.currentTimeMillis()
        val last7 = all.count { matchesFilter(it) && it.optLong("ts", 0L) >= now - 7 * DAY }
        val avg = if (list.isEmpty()) 0L else list.sumOf { it.optLong("sec", 0L) } / list.size
        val tiles = LinearLayout(c)
        tiles.orientation = LinearLayout.HORIZONTAL
        tiles.addView(statCard("Trainings", list.size.toString()), LinearLayout.LayoutParams(0, -2, 1f))
        val second = LinearLayout.LayoutParams(0, -2, 1f)
        second.leftMargin = Ui.dp(c, 10)
        tiles.addView(statCard("Last 7 days", last7.toString()), second)
        val third = LinearLayout.LayoutParams(0, -2, 1f)
        third.leftMargin = Ui.dp(c, 10)
        tiles.addView(statCard("Avg. length", Fmt.duration(avg)), third)
        column.addTo(tiles, 14)

        if (list.isEmpty()) {
            val msg = if (all.isEmpty()) "No trainings yet. Finish a training to see it here."
            else "No trainings in this range."
            column.addTo(Ui.note(c, msg), 12)
            return
        }
        for (r in list) column.addTo(historyCard(r), 12)
    }

    private fun filterRow(): LinearLayout {
        val c = this
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val options = ArrayList<Pair<String, String?>>()
        options.add(Pair("All", null))
        for (k in Trainings.ALL) options.add(Pair(Trainings.name(k), k))
        for ((i, opt) in options.withIndex()) {
            val sel = filterMod == opt.second
            val t = Ui.text(c, opt.first, 13f, if (sel) Ui.WHITE else Ui.TEXT, Ui.BOLD)
            t.gravity = Gravity.CENTER
            t.background = Ui.rounded(c, if (sel) Ui.BLUE else Ui.SOFT, 20)
            t.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10))
            t.setOnClickListener {
                filterMod = opt.second
                render()
            }
            val lp = LinearLayout.LayoutParams(-2, -2)
            if (i > 0) lp.leftMargin = Ui.dp(c, 8)
            row.addView(t, lp)
        }
        return row
    }

    private fun matchesFilter(r: JSONObject): Boolean = filterMod == null || r.optString("mod") == filterMod

    private fun filtered(all: List<JSONObject>): List<JSONObject> {
        val cutoff = if (rangeDays == 0) 0L else System.currentTimeMillis() - rangeDays * DAY
        return all.filter { matchesFilter(it) && it.optLong("ts", 0L) >= cutoff }
    }

    /** One full-width card: time and details on the left, training name on a chip on the right. */
    private fun historyCard(r: JSONObject): LinearLayout {
        val c = this
        val key = r.optString("mod")
        val card = Ui.card(c)
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(c)
        left.orientation = LinearLayout.VERTICAL
        left.addView(Ui.text(c, timeLabel(r.optLong("ts", 0L)), 16f, Ui.TEXT, Ui.BOLD))
        val meta = metaText(key, r)
        val details = if (meta.isEmpty()) Fmt.duration(r.optLong("sec", 0L))
        else "$meta · " + Fmt.duration(r.optLong("sec", 0L))
        left.addTo(Ui.text(c, details, 13f, Ui.MUTED, Ui.REG), 4)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Ui.chip(c, Trainings.name(key), Ui.BLUE, Ui.BLUE_SOFT), LinearLayout.LayoutParams(-2, -2))
        card.addView(row)
        return card
    }

    private fun historyErrorCard(): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.addView(Ui.text(c, "Couldn't load your trainings", 15f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(c, "Check your connection and try again.", 13f, Ui.MUTED, Ui.REG), 4)
        val btn = Ui.pill(c, "Try again")
        btn.setOnClickListener {
            loadHistory()
            render()
        }
        card.addTo(btn, 14)
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

    // ---------- export ----------

    private fun exportHistory(list: List<JSONObject>) {
        if (list.isEmpty()) return
        try {
            val file = writeCsv(list)
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share history"))
        } catch (e: Exception) {
            Dialogs.show(
                this,
                "Couldn't export",
                "Something went wrong while making the file. Please try again.",
                "OK"
            )
        }
    }

    private fun writeCsv(list: List<JSONObject>): File {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder("date,training,duration_seconds,details\n")
        for (r in list) {
            val key = r.optString("mod")
            sb.append(csv(fmt.format(Date(r.optLong("ts", 0L))))).append(',')
            sb.append(csv(Trainings.name(key))).append(',')
            sb.append(r.optLong("sec", 0L)).append(',')
            sb.append(csv(metaText(key, r))).append('\n')
        }
        val dir = File(cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "presence-history.csv")
        file.writeText(sb.toString())
        return file
    }

    private fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
}
