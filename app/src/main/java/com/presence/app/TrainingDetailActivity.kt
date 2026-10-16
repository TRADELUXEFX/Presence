package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.presence.app.Ui.addTo
import org.json.JSONObject

/** One finished training, opened from a history card: its details, the session it was part of, and Do again. */
class TrainingDetailActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        val c = this
        val r = JSONObject(intent.getStringExtra("row") ?: "{}")
        val whenText = intent.getStringExtra("when") ?: ""
        val key = r.optString("mod")
        val accent = Trainings.accent(key)
        val ts = r.optLong("ts", 0L)
        val uid = Session.userId

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        // header in the training's colour, dark text; the back arrow returns to History
        // the eyes mode (open / closed / animated / mirror) is shown as an icon on the right of the title, and by name in the details
        var mode = r.optString("mode", "")
        // No Blinking: Eyes Open, Animated Eyes or Mirror; an older entry with no mode saved was Eyes Open
        if (key == Trainings.BLINK && (mode.isBlank() || mode == "null")) mode = "open"
        var modeIcon: View? = null
        if (key != Trainings.MEDITATE && key != Trainings.BREATH && mode.isNotBlank() && mode != "null") {
            val path = when (mode) {
                "closed" -> Icons.EYE_OFF
                "animated" -> Icons.EYES
                "mirror" -> Icons.MIRROR
                else -> Icons.EYE
            }
            val box = FrameLayout(c)
            val circle = GradientDrawable()
            circle.shape = GradientDrawable.OVAL
            circle.setColor(Color.parseColor("#33FFFFFF"))
            box.background = circle
            box.contentDescription = Trainings.modeLabel(mode)
            box.addView(modeIcon(c, path, Ui.onColor(accent)), FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER).apply { setMargins(Ui.dp(c, 9), Ui.dp(c, 9), Ui.dp(c, 9), Ui.dp(c, 9)) })
            modeIcon = box
        }
        root.addView(Ui.header(c, Trainings.name(key), accent, modeIcon) { finish() }, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 24))

        val date = Ui.text(c, whenText, 13f, Ui.MUTED, Ui.REG)
        date.gravity = Gravity.CENTER
        body.addView(date, LinearLayout.LayoutParams(-1, -2))

        // the details, two to a row
        val cells = ArrayList<Pair<String, String>>()
        cells.add("Duration" to Fmt.duration(r.optLong("sec", 0L)))
        if (key == Trainings.MEDITATE) {
            cells.add("Minutes" to r.optInt("med_min", 0).toString())
        } else if (key == Trainings.BREATH) {
            cells.add("Cycles" to r.optInt("sets", 0).toString())
            cells.add("Breath pattern" to "4-4-4-4")
            cells.add("Rest between" to "${r.optInt("rest_sec", 0)}s")
        } else {
            cells.add("Sets" to r.optInt("sets", 0).toString())
            cells.add("Per rep" to "${r.optInt("rep_sec", 0)}s")
            cells.add("Rest between" to "${r.optInt("rest_sec", 0)}s")
            // the mode by name: an older No Fidgeting entry with no mode saved was Eyes Closed
            val modeName = if (mode.isBlank() || mode == "null") (if (key == Trainings.BLINK) "open" else "closed") else mode
            cells.add(1, "Mode" to Trainings.modeLabel(modeName))
        }

        var i = 0
        while (i < cells.size) {
            val line = LinearLayout(c)
            line.orientation = LinearLayout.HORIZONTAL
            if (i + 1 < cells.size) {
                line.addView(detailCell(cells[i]), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                line.addView(detailCell(cells[i + 1]), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(c, 8) })
            } else {
                // a last card with no partner stays in the middle, the same width as the cards above it
                line.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
                line.addView(detailCell(cells[i]), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f).apply { leftMargin = Ui.dp(c, 4); rightMargin = Ui.dp(c, 4) })
                line.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
            }
            body.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = if (i == 0) Ui.dp(c, 14) else Ui.dp(c, 8) })
            i += 2
        }

        // how it felt: full width, all three faces, the rating given is highlighted in the training colour
        val felt = Feel.get(uid, ts)
        val feltCard = Ui.card(c)
        feltCard.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
        val feltLabel = Ui.text(c, "HOW IT FELT", 10f, Ui.MUTED, Ui.MED)
        feltLabel.gravity = Gravity.CENTER
        feltCard.addView(feltLabel, LinearLayout.LayoutParams(-1, -2))
        val opts = LinearLayout(c)
        opts.orientation = LinearLayout.HORIZONTAL
        for ((i, label) in Feel.OPTIONS.withIndex()) {
            val on = label == felt
            val fg = if (on) Color.parseColor("#0F172A") else Ui.TEXT
            val pill = LinearLayout(c)
            pill.orientation = LinearLayout.HORIZONTAL
            pill.gravity = Gravity.CENTER
            pill.background = Ui.rounded(c, if (on) accent else Ui.SOFT, 19)
            pill.setPadding(Ui.dp(c, 6), Ui.dp(c, 9), Ui.dp(c, 6), Ui.dp(c, 9))
            pill.addView(FsIconView(c, Feel.face(label), fg), LinearLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16)))
            pill.addView(Ui.text(c, label, 12f, fg, Ui.BOLD), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = Ui.dp(c, 6) })
            opts.addView(pill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) leftMargin = Ui.dp(c, 6) })
        }
        feltCard.addView(opts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 10) })
        body.addView(feltCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 8) })

        // the session this training was part of, if any
        val sess = SessionLog.get(uid, ts)
        if (sess != null) {
            val card = Ui.card(c)
            card.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14))
            card.addView(Ui.text(c, "PART OF A SESSION", 10f, Ui.MUTED, Ui.MED))
            card.addView(Ui.text(c, sess.optString("name"), 15f, Ui.TEXT, Ui.BOLD), LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 3) })
            val pos = sess.optInt("pos", 1)
            val total = sess.optInt("total", 1)
            val segs = LinearLayout(c)
            segs.orientation = LinearLayout.HORIZONTAL
            for (n in 1..total) {
                val seg = View(c)
                seg.background = Ui.rounded(c, if (n == pos) accent else Ui.SOFT, 3)
                segs.addView(seg, LinearLayout.LayoutParams(0, Ui.dp(c, 6), 1f).apply { if (n > 1) leftMargin = Ui.dp(c, 6) })
            }
            card.addView(segs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 10) })
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 14) })
        }

        // Do again: the same training with the same settings
        val again = Ui.pill(c, "Do again", accent, Color.parseColor("#0F172A"))
        again.setOnClickListener {
            val base = Trainings.defaultConfig(key, JSONObject())
            val cfg = base.copy(
                sets = r.optInt("sets", base.sets),
                repSec = r.optInt("rep_sec", base.repSec),
                restSec = r.optInt("rest_sec", base.restSec),
                medMin = r.optInt("med_min", base.medMin)
            )
            TrainingSession.name = "Session"
            TrainingSession.clear()
            TrainingSession.items.add(cfg)
            startActivity(Intent(c, ConfigActivity::class.java).putExtra("session", true))
        }
        body.addView(again, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 22) })

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun detailCell(p: Pair<String, String>): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
        card.addView(Ui.text(c, p.first, 10f, Ui.MUTED, Ui.MED))
        // the Duration tile gets the small clock before its value
        val value: View = if (p.first == "Duration") Ui.clockLine(c, null, p.second, 15f, Ui.TEXT, Ui.BOLD, 16)
        else Ui.text(c, p.second, 15f, Ui.TEXT, Ui.BOLD)
        card.addView(value, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 3) })
        return card
    }
}
