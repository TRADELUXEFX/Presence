package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.presence.app.Ui.addTo
import org.json.JSONObject

/** Saved list: your named trainings. Tap one to start it. Select mode picks lists to delete. */
class SavedListActivity : BaseActivity() {
    private lateinit var column: LinearLayout
    private lateinit var selectBtn: TextView
    private lateinit var bar: LinearLayout
    private lateinit var deleteBtn: Button
    private var selecting = false
    private val selected = LinkedHashSet<String>()

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

        selectBtn = Ui.text(c, "Select", 13f, Color.WHITE, Ui.BOLD)
        selectBtn.gravity = Gravity.CENTER
        selectBtn.background = Ui.rounded(c, Color.parseColor("#33FFFFFF"), 20)
        selectBtn.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8))
        selectBtn.setOnClickListener { onSelectTapped() }
        root.addView(Ui.header(c, "Saved list", 0, selectBtn) { finish() }, LinearLayout.LayoutParams(-1, -2))

        column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 24))
        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // bottom bar, only while selecting (curved top, like PauzFirst)
        bar = LinearLayout(c)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14))
        Ui.tintedBottomBar(c, bar, Ui.BLUE)
        bar.elevation = Ui.dp(c, 8).toFloat()
        val doneBtn = Ui.pill(c, "Done", Ui.SOFT, Ui.TEXT)
        doneBtn.setOnClickListener {
            selecting = false
            selected.clear()
            render()
        }
        val doneLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        doneLp.rightMargin = Ui.dp(c, 6)
        bar.addView(doneBtn, doneLp)
        deleteBtn = Ui.pill(c, "Delete", Ui.RED, Color.WHITE)
        deleteBtn.setOnClickListener { confirmDelete() }
        val delLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        delLp.leftMargin = Ui.dp(c, 6)
        bar.addView(deleteBtn, delLp)
        bar.visibility = View.GONE
        Ui.padInsets(bar, bottom = true)
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        Ui.setNavColor(c, Ui.BLUE_DARK)
        render()
    }

    /** Select turns on picking; then All selects everything (None clears it). */
    private fun onSelectTapped() {
        val all = SavedLists.all(Session.userId)
        if (!selecting) {
            selecting = true
            selected.clear()
        } else if (all.isNotEmpty() && selected.size == all.size) {
            selected.clear()
        } else {
            selected.clear()
            for (o in all) selected.add(o.optString("id"))
        }
        render()
    }

    private fun render() {
        val c = this
        val all = SavedLists.all(Session.userId)
        selected.retainAll(all.map { it.optString("id") }.toSet())
        column.removeAllViews()

        selectBtn.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        selectBtn.text = when {
            !selecting -> "Select"
            all.isNotEmpty() && selected.size == all.size -> "None"
            else -> "All"
        }
        bar.visibility = if (selecting) View.VISIBLE else View.GONE
        deleteBtn.text = if (selected.isEmpty()) "Delete" else "Delete ${selected.size}"
        deleteBtn.alpha = if (selected.isEmpty()) 0.45f else 1f

        if (all.isEmpty()) {
            column.addTo(Ui.note(c, "No saved lists yet. Set up a training, then tap Save list at the top."), 0)
            return
        }
        val hint = if (selecting) "Tick the lists you want to delete." else "Tap a list to start that training."
        column.addTo(Ui.text(c, hint, 13f, Ui.MUTED, Ui.REG), 0)
        for (o in all) column.addTo(row(o), 12)
    }

    private fun row(o: JSONObject): LinearLayout {
        val c = this
        val id = o.optString("id")
        val cfg = SavedLists.toConfig(o)
        val card = Ui.card(c)
        card.isClickable = true
        val line = LinearLayout(c)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(c)
        left.orientation = LinearLayout.VERTICAL
        left.addView(Ui.text(c, o.optString("name"), 15f, Ui.TEXT, Ui.BOLD))
        left.addTo(Ui.text(c, Trainings.name(cfg.key) + " · " + summary(cfg), 12f, Ui.MUTED, Ui.REG), 3)
        line.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        if (selecting) {
            line.addView(Ui.tick(c, selected.contains(id), 22), LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)))
            card.setOnClickListener {
                if (!selected.add(id)) selected.remove(id)
                render()
            }
        } else {
            line.addView(Ui.text(c, "Start", 13f, Trainings.ink(cfg.key), Ui.BOLD))
            card.setOnClickListener {
                // ConfigActivity reads listId, uses the saved settings and starts the training (same permission checks)
                startActivity(Intent(c, ConfigActivity::class.java).putExtra("key", cfg.key).putExtra("listId", id))
            }
        }
        card.addView(line)
        return card
    }

    private fun confirmDelete() {
        val n = selected.size
        if (n == 0) return
        Dialogs.show(
            this,
            if (n == 1) "Delete this list?" else "Delete $n lists?",
            "They'll be removed from this phone and your account.",
            "Delete",
            "Keep",
            onPrimary = {
                SavedLists.remove(Session.userId, selected.toSet())
                selected.clear()
                selecting = false
                render()
            }
        )
    }

    private fun summary(cfg: Config): String = when (cfg.key) {
        Trainings.MEDITATE -> "${cfg.medMin} min"
        Trainings.BREATH -> "${cfg.sets} cycles · ${cfg.restSec}s rest"
        else -> "${Trainings.modeLabel(cfg.mode)} · ${cfg.sets} sets · ${cfg.repSec}s per rep · ${cfg.restSec}s rest"
    }
}
