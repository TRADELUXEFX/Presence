package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.presence.app.Ui.addTo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Progress, laid out like PauzFirst's "Your progress" screen.
 * Training Stats: tinted tiles, with "Your practice" (Week bars / Month calendar) at the bottom.
 * Training History: filters, summary tiles, full-width cards, export.
 */
class ProgressActivity : BaseActivity() {
    private lateinit var tabs: LinearLayout
    private lateinit var tabsWrap: LinearLayout
    private lateinit var column: LinearLayout
    private lateinit var scroll: ScrollView          // Training History's own scroll area
    private lateinit var statsScroll: ScrollView     // Training Stats' own scroll area
    private lateinit var historyColumn: LinearLayout
    private lateinit var historyTop: LinearLayout     // fixed part of Training History: filters, range switch, tiles
    private lateinit var statsColumn: LinearLayout
    private lateinit var exportBar: LinearLayout
    private lateinit var exportBtn: Button
    private var showHistory = true      // History opens on Training History; Training Stats is the second tab
    private var history: List<JSONObject>? = null
    private var loaderClosing = false        // the ring is finishing its lap before the content appears
    private var historyLoading = false
    private var historyFailed = false
    private var historyShown = 100          // history cards drawn so far; "Show more" adds 100
    private var offline = false
    private var filterMod: String? = null   // null = all trainings
    private var rangeDays = 0               // 7, 30, or 0 for all time (default: all time)
    private var exportable: List<JSONObject> = emptyList()
    private var practiceMonth = false       // "Your practice": false = Week, true = Month
    private var practiceMonthOffset = 0     // 0 = this month, -1 = last month, ...
    private var navBottom = 0               // height of the system navigation bar

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
        // blue header: the Progress title, with the Training History | Training Stats switch inside it
        val headBox = LinearLayout(c)
        headBox.orientation = LinearLayout.VERTICAL
        val r = Ui.dp(c, 24).toFloat()
        val headBg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        headBg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        headBox.background = headBg
        exportBtn = Ui.pill(c, "Export", Ui.BLUE, Ui.WHITE)
        exportBtn.textSize = 13f
        exportBtn.minimumHeight = Ui.dp(c, 34)
        exportBtn.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6))
        val titleRow = Ui.header(c, "History", action = exportBtn, showBack = false) { }
        titleRow.background = null
        headBox.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        tabs = LinearLayout(c)
        tabs.orientation = LinearLayout.VERTICAL
        tabsWrap = LinearLayout(c)
        tabsWrap.setPadding(Ui.dp(c, 18), Ui.dp(c, 4), Ui.dp(c, 18), Ui.dp(c, 18))
        tabsWrap.addView(tabs, LinearLayout.LayoutParams(-1, -2))
        headBox.addView(tabsWrap, LinearLayout.LayoutParams(-1, -2))
        root.addView(headBox, LinearLayout.LayoutParams(-1, -2))

        // Training History: this part stays put; only the trainings list below it scrolls.
        historyTop = LinearLayout(c)
        historyTop.orientation = LinearLayout.VERTICAL
        historyTop.setBackgroundColor(Ui.BG)
        historyTop.setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 18), Ui.dp(c, 16))
        root.addView(historyTop, LinearLayout.LayoutParams(-1, -2))

        // Each tab has its own scroll area, so one tab's scroll position can never carry over to the other.
        historyColumn = newColumn()
        statsColumn = newColumn()
        column = historyColumn          // the column the current tab draws into (set again in render())
        scroll = newScroll(historyColumn)
        statsScroll = newScroll(statsColumn)
        statsScroll.visibility = View.GONE
        val scrollHost = FrameLayout(c)
        scrollHost.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        scrollHost.addView(statsScroll, FrameLayout.LayoutParams(-1, -1))
        root.addView(scrollHost, LinearLayout.LayoutParams(-1, 0, 1f))

        exportBar = LinearLayout(c)
        exportBar.orientation = LinearLayout.VERTICAL
        exportBar.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14))
        Ui.bottomBarBg(c, exportBar)
        exportBtn.setOnClickListener { chooseExportRange() }
        exportBtn.visibility = View.INVISIBLE
        // same structure as the Trainings tab: the bar is the last child of the screen's root
        root.addView(Nav.bar(c, 2), LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        // BaseActivity turns clipping off on every view, so the history list drew over the tabs
        // when scrolling up. Turn clipping back on for the scroll area only, with rounded top corners
        // so content scrolling under the tabs is cut softly (same as PauzFirst).
        for (sv in listOf(scroll, statsScroll)) {
        sv.clipChildren = true
        val topR = Ui.dp(c, 20).toFloat()
        sv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height + topR.toInt(), topR)
            }
        }
        sv.clipToOutline = true
        }

        // Stats content sits above the system navigation bar, so the last card and the
        // "Great job" line are not hidden behind it (as on PauzFirst).
        for (col in listOf(historyColumn, statsColumn)) {
            ViewCompat.setOnApplyWindowInsetsListener(col) { _, insets ->
                navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).bottom
                applyColumnPad()
                insets
            }
            ViewCompat.requestApplyInsets(col)
        }
    }

    private fun newColumn(): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 40))
        return col
    }

    private fun newScroll(col: LinearLayout): ScrollView {
        val sv = ScrollView(this)
        sv.isFillViewport = true
        sv.setBackgroundColor(Ui.BG)
        // Cards fade out at the top and bottom edge of the scroll area instead of being cut off.
        sv.isVerticalFadingEdgeEnabled = true
        sv.setFadingEdgeLength(Ui.dp(this, 28))
        sv.addView(col)
        return sv
    }

    private fun applyColumnPad() {
        val c = this
        historyColumn.setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 18), Ui.dp(c, 24))
        statsColumn.setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 18), Ui.dp(c, 24) + navBottom)
    }

    override fun onBackPressed() {
        Nav.goHome(this)
    }

    override fun onResume() {
        super.onResume()
        render()
        // back online: send anything waiting, then load the server's history if we were showing the offline copy
        if (Session.loggedIn() && NetWatch.isOnline(this)) {
            Sync.flush {
                if (!isFinishing && offline && !historyLoading) loadHistory()
            }
        }
    }

    private fun setTab(hist: Boolean) {
        showHistory = hist
        if (hist && history == null && !historyLoading) loadHistory()
        render()
        // the tab you switch to always opens at the top
        val target = if (hist) scroll else statsScroll
        target.scrollTo(0, 0)
        target.post { target.scrollTo(0, 0) }
    }

    private fun loadHistory() {
        historyLoading = true
        historyFailed = false
        Thread {
            val uid = Session.userId
            val fresh = Api.fetchSessions()
            if (fresh != null) cacheHistory(uid, fresh)
            // online: the server's history. Offline: the copy saved on this phone.
            // Either way, trainings not yet uploaded are added, so nothing finished is missing.
            val base = fresh ?: cachedHistory(uid)
            val merged = (base + LocalStore.pendingFor(uid))
                .distinctBy { it.optLong("ts") to it.optString("mod") }
                .sortedByDescending { it.optLong("ts") }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                historyLoading = false
                offline = fresh == null
                historyFailed = fresh == null && merged.isEmpty()
                history = merged
                render()
            }
        }.start()
    }

    /** Keeps the last history from the server on this phone, for when there is no connection. */
    private fun cacheHistory(uid: String, rows: List<JSONObject>) {
        val arr = JSONArray()
        for (r in rows) arr.put(r)
        Session.prefs.edit().putString("history_cache_$uid", arr.toString()).apply()
    }

    private fun cachedHistory(uid: String): List<JSONObject> {
        val text = Session.prefs.getString("history_cache_$uid", null) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            arr.objects()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun render() {
        // draw into the current tab's own column and show only its scroll area
        val showStats = history != null && !showHistory
        column = if (showStats) statsColumn else historyColumn
        scroll.visibility = if (showStats) View.GONE else View.VISIBLE
        statsScroll.visibility = if (showStats) View.VISIBLE else View.GONE
        // Nothing shows until every result is in: a blank page with the loading ring,
        // then the tabs and cards appear together.
        if (history == null) {
            tabsWrap.visibility = View.INVISIBLE
            exportBtn.visibility = View.INVISIBLE
            // a ring is already turning: leave it alone, so a repeat render does not restart it
            if (!historyFailed && Loading.has(column)) return
            historyTop.removeAllViews()
            historyTop.visibility = View.GONE
            column.removeAllViews()
            if (historyFailed) {
                column.gravity = Gravity.TOP
                column.layoutParams = FrameLayout.LayoutParams(-1, -2)
                column.addTo(historyErrorCard(), 0)
            } else {
                if (!historyLoading) loadHistory()
                // the loader sits in the middle of the free screen, not at the top
                column.gravity = Gravity.CENTER
                column.layoutParams = FrameLayout.LayoutParams(-1, -1)
                column.addTo(Loading.page(this), 0)
            }
            return
        }
        // the data has arrived: let the ring finish its lap and fade out, then draw the content
        if (loaderClosing) return
        if (Loading.has(column)) {
            loaderClosing = true
            Loading.finish(column) {
                loaderClosing = false
                column.removeAllViews()
                render()
            }
            return
        }
        column.gravity = Gravity.TOP
        column.layoutParams = FrameLayout.LayoutParams(-1, -2)
        tabsWrap.visibility = View.VISIBLE
        renderTabs()
        column.removeAllViews()
        historyTop.removeAllViews()
        historyTop.visibility = if (showHistory) View.VISIBLE else View.GONE
        // offline note: the same pill on both History tabs (kept above the list)
        if (offline) historyTop.addView(Ui.offlineRow(this), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@ProgressActivity, 6) })
        exportBtn.visibility = if (showHistory) View.VISIBLE else View.INVISIBLE
        applyColumnPad()
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

    /** The Training History | Training Stats switch, drawn inside the blue header. */
    private fun headerSegment(labels: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val c = this
        val box = LinearLayout(c)
        box.orientation = LinearLayout.HORIZONTAL
        box.background = Ui.rounded(c, Color.parseColor("#33FFFFFF"), 26)
        box.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        for ((i, label) in labels.withIndex()) {
            val sel = i == selected
            val t = Ui.text(c, label, 14f, if (sel) Ui.BLUE else Ui.WHITE, Ui.BOLD)
            t.gravity = Gravity.CENTER
            t.background = Ui.rounded(c, if (sel) Ui.WHITE else Color.TRANSPARENT, 22)
            t.setPadding(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12))
            t.setOnClickListener { onPick(i) }
            box.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return box
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        tabs.addView(
            headerSegment(listOf("Training History", "Training Stats"), if (showHistory) 0 else 1) { setTab(it == 0) },
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    // ---------- Training Stats ----------

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
        renderPractice()
    }

    /** "Your practice": one tinted card at the bottom of Training Stats, with a Week | Month switch. */
    private fun renderPractice() {
        val c = this
        val card = Ui.card(c, Ui.CARD_TINT)
        fillPractice(card)
        column.addTo(card, 12)

        val all = history ?: return
        if (secondsOnDay(all, dayStart(0)) > 0L) {
            column.addTo(Ui.text(c, "Great job! You practiced today.", 14f, Ui.MUTED, Ui.REG).apply {
                gravity = Gravity.CENTER
            }, 16)
        }
    }

    /** Rebuilds the practice card in place, so switching Week / Month or months does not redraw the whole page. */
    private fun fillPractice(card: LinearLayout) {
        val c = this
        card.removeAllViews()
        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val title = Ui.text(c, "YOUR PRACTICE", 12f, Ui.MUTED_TINT, Ui.BOLD)
        title.letterSpacing = 0.1f
        head.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val pill = LinearLayout(c)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.background = Ui.rounded(c, Ui.c("#C4DCFB", "#26426B"), 12)
        pill.setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3))
        for ((label, isMonth) in listOf("Week" to false, "Month" to true)) {
            val on = practiceMonth == isMonth
            val t = Ui.text(c, label, 13f, if (on) Ui.WHITE else Ui.c("#3F5F8F", "#94A3B8"), Ui.BOLD)
            t.gravity = Gravity.CENTER
            t.setPadding(Ui.dp(c, 14), Ui.dp(c, 7), Ui.dp(c, 14), Ui.dp(c, 7))
            if (on) t.background = Ui.rounded(c, Ui.BLUE, 10)
            t.setOnClickListener {
                if (practiceMonth != isMonth) {
                    practiceMonth = isMonth
                    if (isMonth) practiceMonthOffset = 0
                    fillPractice(card)
                }
            }
            pill.addView(t)
        }
        head.addView(pill)
        card.addTo(head)

        val all = history
        if (all == null) {
            if (historyFailed) {
                card.addTo(Ui.text(c, "Couldn't load your practice. Check your connection.", 13f, Ui.MUTED_TINT, Ui.REG), 14)
            } else {
                card.addTo(Loading.inline(c), 14)
            }
            return
        }
        if (practiceMonth) monthContent(card, all) else weekContent(card, all)
    }

    /** Week view: one bar per day for the last 7 days (today on the right), height = time trained that day. */
    private fun weekContent(card: LinearLayout, all: List<JSONObject>) {
        val c = this
        val secs = LongArray(7) { i -> secondsOnDay(all, dayStart(6 - i)) }
        var max = 1L
        var total = 0L
        for (v in secs) { total += v; if (v > max) max = v }
        card.addTo(Ui.clockLine(c, null, Fmt.duration(total) + " this week", 13f, Ui.MUTED_TINT, Ui.MED, 15), 10)

        val dayFmt = SimpleDateFormat("EEEEE", Locale.getDefault())
        val barArea = Ui.dp(c, 96)
        val cols = LinearLayout(c)
        cols.orientation = LinearLayout.HORIZONTAL
        for (i in 0 until 7) {
            val col = LinearLayout(c)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            val v = secs[i]
            val isToday = i == 6

            val value = Ui.text(c, if (v == 0L) "" else shortTime(v), 10f, Ui.MUTED_TINT, Ui.MED)
            value.gravity = Gravity.CENTER
            col.addView(value)

            val barH = if (v == 0L) Ui.dp(c, 4) else Math.max(Ui.dp(c, 8), (barArea.toLong() * v / max).toInt())
            val bar = View(c)
            val barColor = if (isToday) Ui.BLUE
                else if (v == 0L) Ui.c("#B7D5FA", "#2F5F9E")
                else Color.argb(150, 30, 144, 255)
            bar.background = Ui.rounded(c, barColor, 6)
            val barLp = LinearLayout.LayoutParams(Ui.dp(c, 18), barH)
            barLp.topMargin = Ui.dp(c, 4)
            col.addView(bar, barLp)

            val day = Calendar.getInstance()
            day.add(Calendar.DAY_OF_YEAR, -(6 - i))
            val label = Ui.text(c, dayFmt.format(day.time), 11f,
                if (isToday) Ui.TEXT else Ui.MUTED_TINT, if (isToday) Ui.BOLD else Ui.MED)
            label.gravity = Gravity.CENTER
            val labLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            labLp.topMargin = Ui.dp(c, 6)
            col.addView(label, labLp)

            cols.addView(col, LinearLayout.LayoutParams(0, Ui.dp(c, 136), 1f))
        }
        card.addTo(cols, 8)
    }

    /** Month view: calendar for one month, days shaded by minutes trained, today outlined. */
    private fun monthContent(card: LinearLayout, all: List<JSONObject>) {
        val c = this
        val shown = Calendar.getInstance()
        shown.set(Calendar.DAY_OF_MONTH, 1)
        shown.add(Calendar.MONTH, practiceMonthOffset)
        val year = shown.get(Calendar.YEAR)
        val month = shown.get(Calendar.MONTH)
        val secs = secondsInMonth(all, year, month)

        // month name with back / forward arrows
        val nav = LinearLayout(c)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        val canForward = practiceMonthOffset < 0
        val back = Ui.text(c, "\u2039", 26f, Ui.MUTED_TINT, Ui.BOLD)
        back.gravity = Gravity.CENTER
        back.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0)
        back.setOnClickListener { practiceMonthOffset--; fillPractice(card) }
        val fwd = Ui.text(c, "\u203A", 26f, if (canForward) Ui.MUTED_TINT else Ui.c("#B7D5FA", "#2F5F9E"), Ui.BOLD)
        fwd.gravity = Gravity.CENTER
        fwd.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0)
        if (canForward) fwd.setOnClickListener { practiceMonthOffset++; fillPractice(card) }
        val name = Ui.text(c, SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(shown.time), 16f, Ui.TEXT, Ui.BOLD)
        name.gravity = Gravity.CENTER
        nav.addView(back)
        nav.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(fwd)
        card.addTo(nav, 10)

        // weekday letters, starting on the locale's first day of the week
        val firstDow = Calendar.getInstance().firstDayOfWeek
        val letters = SimpleDateFormat("EEEEE", Locale.getDefault())
        val labelRow = LinearLayout(c)
        labelRow.orientation = LinearLayout.HORIZONTAL
        for (i in 0 until 7) {
            val d = Calendar.getInstance()
            d.set(Calendar.DAY_OF_WEEK, ((firstDow - 1 + i) % 7) + 1)
            val t = Ui.text(c, letters.format(d.time), 12f, Ui.MUTED_TINT, Ui.MED)
            t.gravity = Gravity.CENTER
            labelRow.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addTo(labelRow, 12)

        // day grid
        val first = Calendar.getInstance()
        first.clear()
        first.set(year, month, 1)
        val offset = (first.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
        val now = Calendar.getInstance()
        val isThisMonth = now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) == month
        val todayDay = if (isThisMonth) now.get(Calendar.DAY_OF_MONTH) else -1
        val inFuture = practiceMonthOffset > 0
        var practiced = 0
        val cell = Ui.dp(c, 38)
        val rows = (offset + secs.size + 6) / 7
        for (r in 0 until rows) {
            val row = LinearLayout(c)
            row.orientation = LinearLayout.HORIZONTAL
            for (col in 0 until 7) {
                val day = r * 7 + col - offset + 1
                val holder = FrameLayout(c)
                if (day in 1..secs.size) {
                    val v = secs[day - 1]
                    val future = inFuture || (isThisMonth && day > todayDay)
                    val shade = if (v <= 0L) 0 else if (v < 300L) 1 else if (v < 600L) 2 else 3
                    if (shade > 0) practiced++
                    val bg = when (shade) {
                        1 -> Ui.c("#B7D5FA", "#2F5F9E")
                        2 -> Ui.c("#7DB8FF", "#3F7FD0")
                        3 -> Ui.BLUE
                        else -> 0
                    }
                    val fg = when {
                        shade >= 2 -> Ui.WHITE
                        shade == 1 -> Ui.c("#0B3B7A", "#F1F5F9")
                        future -> Ui.c("#94A3B8", "#64748B")
                        else -> Ui.MUTED_TINT
                    }
                    val t = Ui.text(c, day.toString(), 13f, fg, if (shade > 0 || day == todayDay) Ui.BOLD else Ui.MED)
                    t.gravity = Gravity.CENTER
                    val isToday = day == todayDay
                    if (bg != 0 || isToday) {
                        t.background = Ui.rounded(c, if (bg != 0) bg else 0x00000000, 19,
                            if (isToday) Ui.c("#0B5FD1", "#8FC1FF") else 0, if (isToday) 2 else 0)
                    }
                    holder.addView(t, FrameLayout.LayoutParams(cell, cell, Gravity.CENTER))
                }
                row.addView(holder, LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f))
            }
            card.addTo(row, if (r == 0) 4 else 0)
        }

        val count = Ui.text(c,
            if (practiced == 1) "1 day practiced this month" else "$practiced days practiced this month",
            13f, Ui.MUTED_TINT, Ui.MED)
        count.gravity = Gravity.CENTER
        card.addTo(count, 10)
    }

    /** Minutes trained on the local day that starts at [dayStartMs], in seconds. */
    private fun secondsOnDay(all: List<JSONObject>, dayStartMs: Long): Long {
        val key = dayKeyOf(dayStartMs)
        return all.filter { dayKeyOf(it.optLong("ts", 0L)) == key }.sumOf { it.optLong("sec", 0L) }
    }

    /** Seconds trained per day of one calendar month (index 0 = the 1st). */
    private fun secondsInMonth(all: List<JSONObject>, year: Int, month: Int): LongArray {
        val first = Calendar.getInstance()
        first.clear()
        first.set(year, month, 1)
        val out = LongArray(first.getActualMaximum(Calendar.DAY_OF_MONTH))
        val cal = Calendar.getInstance()
        for (r in all) {
            val ms = r.optLong("ts", 0L)
            if (ms <= 0L) continue
            cal.timeInMillis = ms
            if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                out[cal.get(Calendar.DAY_OF_MONTH) - 1] += r.optLong("sec", 0L)
            }
        }
        return out
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

    /** "45s", "5m", "1h20m". Seconds under a minute, so no "<" sign. */
    private fun shortTime(secs: Long): String {
        if (secs < 60L) return "${secs}s"
        if (secs < 3600L) return (secs / 60L).toString() + "m"
        return (secs / 3600L).toString() + "h" + ((secs % 3600L) / 60L).toString() + "m"
    }

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
        historyTop.addView(chips, chipsLp)

        val rangeIndex = when (rangeDays) { 7 -> 0; 30 -> 1; else -> 2 }
        historyTop.addTo(
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
        historyTop.addTo(tiles, 14)

        if (list.isEmpty()) {
            val msg = if (all.isEmpty()) "No trainings yet. Finish a training to see it here."
            else "No trainings in this range."
            column.addTo(Ui.note(c, msg), 12)
            return
        }
        val recs = Recordings.all(Session.userId)
        for (r in list.take(historyShown)) column.addTo(historyCard(r, recs), 12)
        if (list.size > historyShown) {
            val more = Ui.pill(c, "Show more  \u00B7  ${list.size - historyShown} left", Ui.SOFT, Ui.TEXT)
            more.setOnClickListener {
                historyShown += 100
                render()
            }
            column.addTo(more, 14)
        }
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
    private fun historyCard(r: JSONObject, recs: List<Recordings.Rec>): LinearLayout {
        val c = this
        val key = r.optString("mod")
        val card = Ui.card(c, Trainings.soft(key))
        // top row: the time on the left, the training name chip on the right.
        // Below it, the details line gets the full card width, so a long one never runs under the chip.
        val row = LinearLayout(c)
        row.orientation = LinearLayout.VERTICAL
        val top = LinearLayout(c)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val whenText = Ui.text(c, timeLabel(r.optLong("ts", 0L)), 14f, Ui.TEXT, Ui.MED)
        Ui.fit(whenText, 14)
        top.addView(whenText, LinearLayout.LayoutParams(0, -2, 1f))
        // a recording of this training, kept on this phone: a round Play icon beside the name tag (opens the player)
        val rec = Recordings.forTraining(recs, r.optLong("ts", 0L), r.optInt("sec", 0))
        if (rec != null && Recordings.exists(c, rec.uri)) {
            val play = FrameLayout(c)
            play.background = Ui.rounded(c, Ui.BLUE, 17)
            play.contentDescription = "Play video"
            play.addView(
                FsIconView(c, Icons.VIDEO, Color.WHITE),
                FrameLayout.LayoutParams(Ui.dp(c, 17), Ui.dp(c, 17), Gravity.CENTER)
            )
            play.setOnClickListener {
                startActivity(Intent(c, PlayerActivity::class.java).putExtra("uri", rec.uri))
            }
            val playLp = LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34))
            playLp.rightMargin = Ui.dp(c, 8)
            top.addView(play, playLp)
        }
        val tag = Ui.text(c, Trainings.name(key), 11f, Ui.onColor(Trainings.accent(key)), Ui.BOLD)
        tag.gravity = Gravity.CENTER
        tag.background = Ui.rounded(c, Trainings.accent(key), 20)
        tag.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6))
        top.addView(tag, LinearLayout.LayoutParams(-2, -2))
        row.addView(top, LinearLayout.LayoutParams(-1, -2))

        // details: settings, the eyes mode as a small icon (Eyes Open stays hidden, as before), then the length
        val meta = metaText(key, r, includeMode = false)
        val sec = Fmt.duration(r.optLong("sec", 0L))
        val modeKey = r.str("mode")
        val modePath = when (modeKey) {
            "closed" -> Icons.EYE_OFF
            "animated" -> Icons.EYES
            "mirror" -> Icons.MIRROR
            else -> null
        }
        val showMode = key != Trainings.MEDITATE && key != Trainings.BREATH && modePath != null
        val felt = Feel.get(Session.userId, r.optLong("ts", 0L))
        val detailRow = LinearLayout(c)
        detailRow.orientation = LinearLayout.HORIZONTAL
        detailRow.gravity = Gravity.CENTER_VERTICAL
        // the clock icon sits before the length, as on the Quick start cards
        fun clockAndLength() {
            detailRow.addView(FsIconView(c, Icons.ALARM, Ui.MUTED_TINT),
                LinearLayout.LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14)))
            detailRow.addView(Ui.text(c, sec, 13f, Ui.MUTED_TINT, Ui.REG),
                LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(c, 4) })
        }
        if (showMode) {
            if (meta.isNotEmpty()) detailRow.addView(Ui.text(c, "$meta ·", 13f, Ui.MUTED_TINT, Ui.REG))
            detailRow.addView(modeIcon(c, modePath!!, Ui.MUTED_TINT),
                LinearLayout.LayoutParams(Ui.dp(c, 15), Ui.dp(c, 15)).apply { leftMargin = Ui.dp(c, 6); rightMargin = Ui.dp(c, 6) })
            detailRow.addView(Ui.text(c, "·", 13f, Ui.MUTED_TINT, Ui.REG),
                LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Ui.dp(c, 6) })
            clockAndLength()
        } else {
            if (meta.isNotEmpty()) detailRow.addView(Ui.text(c, "$meta ·", 13f, Ui.MUTED_TINT, Ui.REG),
                LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Ui.dp(c, 6) })
            clockAndLength()
        }
        // the face of how it felt (no text), when it was rated
        if (felt != null) {
            detailRow.addView(FsIconView(c, Feel.face(felt), Ui.MUTED_TINT),
                LinearLayout.LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14)).apply { leftMargin = Ui.dp(c, 6) })
        }
        row.addTo(detailRow, 4)

        card.addView(row)
        card.isClickable = true
        card.setOnClickListener {
            startActivity(Intent(c, TrainingDetailActivity::class.java)
                .putExtra("row", r.toString())
                .putExtra("when", whenText.text.toString()))
        }
        return card
    }

    private fun historyErrorCard(): LinearLayout =
        Ui.offlineCard(this, "Couldn't load your history", "Check your connection and try again. Your trainings are saved on this phone.") {
            loadHistory()
            render()
        }

    private fun metaText(key: String, r: JSONObject, includeMode: Boolean = true): String {
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
                if (includeMode && mode != null && mode != "open") parts.add(Trainings.modeLabel(mode))
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

    /** Pop-up: pick how far back the PDF goes. */
    private fun chooseExportRange() {
        if (history.isNullOrEmpty()) {
            android.widget.Toast.makeText(this, "No history to export yet", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (isFinishing) return
        val d = android.app.Dialog(this)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val card = Ui.card(this)
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22))
        card.addView(Ui.text(this, "Export history", 18f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(this, "Which trainings do you want in the PDF?", 14f, Ui.MUTED, Ui.REG), 8)
        val options = listOf(Pair("Last 7 days", 7), Pair("Last 30 days", 30), Pair("All time", 0))
        for ((i, o) in options.withIndex()) {
            val primary = o.second == 0
            val b = Ui.pill(this, o.first, if (primary) Ui.BLUE else Ui.SOFT, if (primary) Ui.WHITE else Ui.TEXT)
            b.setOnClickListener {
                d.dismiss()
                exportHistory(rangeOf(o.second), o.first)
            }
            card.addTo(b, if (i == 0) 20 else 10)
        }
        val dates = Ui.pill(this, "Choose dates", Ui.SOFT, Ui.TEXT)
        dates.setOnClickListener {
            d.dismiss()
            pickDates()
        }
        card.addTo(dates, 10)
        Ui.noClipDeep(card)
        d.setContentView(card)
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    /** Two calendars: the first day, then the last day. The PDF covers everything in between. */
    private fun pickDates() {
        val now = Calendar.getInstance()
        val dayFmt = SimpleDateFormat("d MMM yyyy", Locale.US)
        android.app.DatePickerDialog(this, { _, y, m, d ->
            val start = Calendar.getInstance().apply {
                set(y, m, d, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            android.app.DatePickerDialog(this, { _, y2, m2, d2 ->
                val end = Calendar.getInstance().apply {
                    set(y2, m2, d2, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                val all = history ?: emptyList()
                val list = all.filter {
                    matchesFilter(it) && it.optLong("ts", 0L) in start..end
                }
                exportHistory(list, dayFmt.format(Date(start)) + " - " + dayFmt.format(Date(end)))
            }, y, m, d).apply {
                datePicker.minDate = start
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.maxDate = now.timeInMillis
        }.show()
    }

    /** The trainings in the chosen range that match the training filter on screen. */
    private fun rangeOf(days: Int): List<JSONObject> {
        val all = history ?: return emptyList()
        val cutoff = if (days == 0) 0L else System.currentTimeMillis() - days * DAY
        return all.filter { matchesFilter(it) && it.optLong("ts", 0L) >= cutoff }
    }

    private fun exportHistory(list: List<JSONObject>, rangeLabel: String) {
        if (list.isEmpty()) {
            android.widget.Toast.makeText(this, "No trainings in that range", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = writePdf(list, rangeLabel)
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share history"))
        } catch (e: Exception) {
            Dialogs.show(
                this,
                "Couldn't export",
                "Something went wrong while making the PDF. Please try again.",
                "OK"
            )
        }
    }

    /** The PDF for the trainings shown on the Progress screen, in the same design as PauzFirst's summary. */
    private fun writePdf(list: List<JSONObject>, rangeLabel: String): File {
        val df = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)
        val dayFmt = SimpleDateFormat("d MMM yyyy", Locale.US)
        val totalSec = list.sumOf { it.optLong("sec", 0L) }
        val rows = list.map { r ->
            val key = r.optString("mod")
            PdfReport.Row(
                df.format(Date(r.optLong("ts", 0L))),
                Trainings.name(key),
                metaText(key, r),
                Fmt.duration(r.optLong("sec", 0L))
            )
        }
        val totals = listOf(
            Pair("Trainings", list.size.toString()),
            Pair("Total time", Fmt.duration(totalSec)),
            Pair("Avg. length", Fmt.duration(totalSec / list.size))
        )
        val byTraining = Trainings.ALL.mapNotNull { k ->
            val n = list.count { it.optString("mod") == k }
            if (n > 0) Pair(Trainings.name(k), n) else null
        }
        val oldest = list.minOf { it.optLong("ts", 0L) }
        val newest = list.maxOf { it.optLong("ts", 0L) }
        val period = dayFmt.format(Date(oldest)) + " - " + dayFmt.format(Date(newest))
        val generated = "Generated " + df.format(Date())
        val dir = File(cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "presence-history.pdf")
        PdfReport.write(this, file, rangeLabel, period, generated, totals, byTraining, rows)
        return file
    }
}
