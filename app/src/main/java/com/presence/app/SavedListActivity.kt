package com.presence.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.presence.app.Ui.addTo
import org.json.JSONObject

/** Saved list: a preview of the saved settings for each training. Tap Edit to change one. */
class SavedListActivity : BaseActivity() {
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
        root.addView(Ui.header(c, "Saved list") { finish() }, LinearLayout.LayoutParams(-1, -2))

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
        render()
    }

    private fun render() {
        val c = this
        column.removeAllViews()
        column.addTo(Ui.text(c, "Your saved settings for each training. These are used when you start one.", 13f, Ui.MUTED, Ui.REG), 0)
        val defaults = LocalStore.profile(Session.userId).optJSONObject("defaults") ?: JSONObject()
        for (k in Trainings.ALL) column.addTo(row(k, defaults), 14)
    }

    private fun row(k: String, defaults: JSONObject): LinearLayout {
        val c = this
        val cfg = Trainings.defaultConfig(k, defaults)
        val card = Ui.card(c)
        card.isClickable = true
        card.setOnClickListener { startActivity(Intent(c, ConfigActivity::class.java).putExtra("key", k)) }
        val line = LinearLayout(c)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(c)
        left.orientation = LinearLayout.VERTICAL
        left.addView(Ui.text(c, Trainings.name(k), 15f, Ui.TEXT, Ui.BOLD))
        left.addTo(Ui.text(c, summary(cfg), 12f, Ui.MUTED, Ui.REG), 3)
        line.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(Ui.text(c, "Edit", 13f, Ui.BLUE, Ui.BOLD))
        card.addView(line)
        return card
    }

    private fun summary(cfg: Config): String = when (cfg.key) {
        Trainings.MEDITATE -> "${cfg.medMin} min"
        Trainings.BREATH -> "${cfg.sets} cycles · ${cfg.restSec}s rest"
        else -> "${Trainings.modeLabel(cfg.mode)} · ${cfg.sets} sets · ${cfg.repSec}s per rep · ${cfg.restSec}s rest"
    }
}
