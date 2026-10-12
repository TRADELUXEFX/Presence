package com.presence.app

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.presence.app.Ui.addTo
import org.json.JSONObject

/** Last seven days: totals, per-training counts, and the PDF report. */
class WeeklyActivity : BaseActivity() {
    private lateinit var column: LinearLayout
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
        Ui.padInsets(root, bottom = true)
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
        when {
            loading -> column.addTo(Ui.note(c, "Loading this week…"), 0)
            failed -> column.addTo(errorCard(), 0)
            else -> {
                val s = Weekly.summarize(rows)
                column.addTo(statCard("Trainings this week", s.count.toString(), "last 7 days"), 0)
                column.addTo(statCard("Total training time", Fmt.duration(s.totalSec), "last 7 days"), 12)
                column.addTo(statCard("Days trained", "${s.days} of 7", "days with at least one training"), 12)
                for (k in Trainings.ALL) {
                    column.addTo(statCard(Trainings.name(k), (s.perMod[k] ?: 0).toString(), "times this week"), 12)
                }
                if (s.count == 0) column.addTo(Ui.note(c, "No trainings this week yet."), 12)
                val export = Ui.pill(c, "Export PDF report")
                export.setOnClickListener { exportPdf(s) }
                column.addTo(export, 22)
            }
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

    private fun statCard(label: String, value: String, sub: String): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.addView(Ui.text(c, label, 13f, Ui.MUTED, Ui.MED))
        card.addTo(Ui.text(c, value, 26f, Ui.BLUE, Ui.BOLD), 4)
        card.addTo(Ui.text(c, sub, 11f, Ui.MUTED, Ui.REG), 2)
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
