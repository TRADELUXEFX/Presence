package com.presence.app

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.presence.app.Ui.addTo
import org.json.JSONObject

/** Last seven days: totals, per-training counts, and the PDF report. */
class WeeklyActivity : BaseActivity() {
    private lateinit var column: LinearLayout
    private lateinit var exportBtn: Button
    private var summary: Weekly.Summary? = null
    private var rows: List<JSONObject> = emptyList()
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
        root.addView(Ui.header(c, "Weekly summary") { finish() }, LinearLayout.LayoutParams(-1, -2))

        column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 24))
        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        bar.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14))
        Ui.bottomBarBg(c, bar)
        exportBtn = Ui.pill(c, "Export PDF report")
        exportBtn.alpha = 0.4f
        exportBtn.setOnClickListener { summary?.let { exportPdf(it) } }
        bar.addView(exportBtn, LinearLayout.LayoutParams(-1, -2))
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        Ui.padInsets(bar, bottom = true)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        loading = true
        failed = false
        render()
        Thread {
            val r = Api.fetchSessions()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                loading = false
                if (r == null) failed = true else rows = r
                render()
            }
        }.start()
    }

    private fun render() {
        val c = this
        column.removeAllViews()
        summary = null
        exportBtn.alpha = 0.4f
        when {
            loading -> column.addTo(Ui.note(c, "Loading this week…"), 0)
            failed -> column.addTo(errorCard(), 0)
            else -> {
                val s = Weekly.summarize(rows)
                summary = s
                exportBtn.alpha = 1f
                addGridRows(listOf(
                    statCard("Trainings", s.count.toString()),
                    statCard("Days trained", "${s.days} of 7")
                ), 0)
                column.addTo(statCard("Total time", Fmt.duration(s.totalSec)), 12)
                addGridRows(Trainings.ALL.map { statCard(Trainings.name(it), (s.perMod[it] ?: 0).toString()) }, 12)
                if (s.count == 0) column.addTo(Ui.note(c, "No trainings this week yet."), 12)
            }
        }
    }

    private fun addGridRows(items: List<View>, topDp: Int) {
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

    private fun exportPdf(s: Weekly.Summary) {
        try {
            val file = WeeklyReport.write(this, s)
            WeeklyReport.share(this, file)
        } catch (e: Exception) {
            Dialogs.show(
                this,
                "Couldn't make the report",
                "Something went wrong while making the PDF. Please try again.",
                "OK"
            )
        }
    }

    /** One tinted tile, the same style as the Progress screen. */
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

    private fun errorCard(): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.addView(Ui.text(c, "Couldn't load this week", 15f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(c, "Check your connection and try again.", 13f, Ui.MUTED, Ui.REG), 4)
        val btn = Ui.pill(c, "Try again")
        btn.setOnClickListener { load() }
        card.addTo(btn, 14)
        return card
    }
}
