package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.presence.app.Ui.addTo
import org.json.JSONObject

/** Builds a session: several trainings, in the order you set, started one after another. */
class SessionActivity : BaseActivity() {
    private lateinit var column: LinearLayout
    private lateinit var startBtn: android.widget.Button
    private val items: ArrayList<Config> get() = SessionDraft.items
    private var editingId: String? = null
    private var editingName = ""

    private val closePath = "M18 6L6 18 M6 6l12 12"

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
        root.addView(Ui.header(c, "Session") { finish() }, LinearLayout.LayoutParams(-1, -2))

        column = LinearLayout(c)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 24))
        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        bar.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14))
        Ui.tintedBottomBar(c, bar, Ui.BLUE)
        bar.elevation = Ui.dp(c, 8).toFloat()
        startBtn = Ui.pill(c, "Start session", Color.WHITE, Ui.BLUE_DARK)
        startBtn.setOnClickListener { startSession() }
        bar.addView(startBtn, LinearLayout.LayoutParams(-1, -2))
        Ui.padInsets(bar, bottom = true)
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        Ui.setNavColor(c, Ui.BLUE_DARK)
        SessionDraft.items.clear()
        // opened from Saved list with a session to edit: load its trainings
        intent.getStringExtra("listId")?.let { id ->
            SavedLists.all(Session.userId).firstOrNull { it.optString("id") == id }?.let { o ->
                editingId = id
                editingName = o.optString("name")
                items.addAll(SavedLists.sessionItems(o))
            }
        }
        render()
    }

    private fun render() {
        val c = this
        column.removeAllViews()
        if (items.isEmpty()) {
            column.addTo(Ui.note(c, "Add the trainings you want, in the order you want to do them."), 0)
        }
        for ((i, cfg) in items.withIndex()) {
            column.addTo(row(i, cfg), if (i == 0) 0 else 10)
        }
        // the estimated time for the whole session (rest included) shows on the Start button only
        val total = items.sumOf { seconds(it) }
        column.addTo(addButton(), 14)
        if (items.isNotEmpty()) {
            val save = Ui.pill(c, if (editingId != null) "Save changes" else "Save session", Ui.SOFT, Ui.TEXT)
            save.setOnClickListener { saveSession() }
            column.addTo(save, 10)
        }
        startBtn.isEnabled = items.isNotEmpty()
        startBtn.alpha = if (items.isEmpty()) 0.4f else 1f
        startBtn.text = if (items.isEmpty()) "Start session" else "Start session  \u00B7  ${Fmt.duration(total)}"
    }

    private fun row(i: Int, cfg: Config): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14))
        val num = Ui.text(c, "${i + 1}", 13f, Ui.onColor(Trainings.accent(cfg.key)), Ui.BOLD)
        num.gravity = Gravity.CENTER
        num.background = Ui.rounded(c, Trainings.accent(cfg.key), 16)

        // number, name and the four small buttons share the top row; the summary sits below at the full card width
        val trailing = listOf(
            icon(Icons.CHEVRON, -90f) { move(i, -1) },
            icon(Icons.CHEVRON, 90f) { move(i, 1) },
            icon(Icons.PENCIL, 0f) { editItem(i) },
            icon(closePath, 0f) {
                items.removeAt(i)
                render()
            }
        )
        card.addView(Ui.nameRow(
            c, Trainings.name(cfg.key), trailing,
            Ui.text(c, summary(cfg), 12f, Ui.MUTED, Ui.REG),
            leading = Pair(num as View, LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)))
        ))
        return card
    }

    private fun icon(path: String, turn: Float, onClick: () -> Unit): Pair<View, LinearLayout.LayoutParams> {
        val c = this
        val box = FrameLayout(c)
        box.background = Ui.rounded(c, Ui.SOFT, 17)
        box.isClickable = true
        val icon = FsIconView(c, path, Ui.TEXT)
        icon.rotation = turn
        box.addView(icon, FrameLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16), Gravity.CENTER))
        box.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34))
        lp.leftMargin = Ui.dp(c, 6)
        return Pair(box as View, lp)
    }

    private fun move(i: Int, d: Int) {
        val j = i + d
        if (j < 0 || j >= items.size) return
        val tmp = items[i]
        items[i] = items[j]
        items[j] = tmp
        render()
    }

    private fun addButton(): View {
        return Ui.pill(this, "+ Add a training", Ui.SOFT, Ui.TEXT).also {
            it.setOnClickListener { showAddPicker() }
        }
    }

    private fun showAddPicker() {
        if (isFinishing) return
        val d = android.app.Dialog(this)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val card = Ui.card(this)
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22))
        card.addView(Ui.text(this, "Add a training", 18f, Ui.TEXT, Ui.BOLD))
        card.addTo(Ui.text(this, "Default settings", 12f, Ui.MUTED, Ui.MED), 14)
        for (k in Trainings.ALL) {
            val b = Ui.pill(this, Trainings.name(k), Trainings.accent(k), Ui.onColor(Trainings.accent(k)))
            b.setOnClickListener {
                d.dismiss()
                addTraining(k)
            }
            card.addTo(b, 8)
        }
        val saved = SavedLists.all(Session.userId).filter { it.optString("kind") != "session" }
        if (saved.isNotEmpty()) {
            card.addTo(Ui.text(this, "Your saved settings", 12f, Ui.MUTED, Ui.MED), 16)
            for (o in saved) {
                val cfg = SavedLists.toConfig(o)
                val b = Ui.pill(this, o.optString("name") + "  \u00B7  " + Trainings.name(cfg.key), Ui.SOFT, Ui.TEXT)
                b.setOnClickListener {
                    d.dismiss()
                    items.add(cfg)
                    render()
                }
                card.addTo(b, 8)
            }
        }
        val scroll = ScrollView(this)
        scroll.addView(card)
        d.setContentView(scroll)
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    /** A session item is edited on the full settings screen, which returns here when saved. */
    private fun editItem(i: Int) {
        SessionDraft.editing = i
        startActivity(Intent(this, ConfigActivity::class.java)
            .putExtra("draftEdit", true)
            .putExtra("key", items[i].key))
    }

    override fun onResume() {
        super.onResume()
        if (::startBtn.isInitialized) render()
    }

    /** Adds a training with its own saved default settings. */
    private fun addTraining(key: String) {
        val defaults = LocalStore.profile(Session.userId).optJSONObject("defaults") ?: JSONObject()
        items.add(Trainings.defaultConfig(key, defaults))
        render()
    }

    /** Saves the session to Saved list: a new one asks for a name, an edited one keeps its name. */
    private fun saveSession() {
        val uid = Session.userId
        val id = editingId
        if (id != null) {
            SavedLists.updateSession(uid, id, editingName, items.toList())
            Toast.makeText(this, "Changes saved to Saved list", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        SavedLists.askName(this) { name ->
            SavedLists.addSession(uid, name, items.toList())
            Toast.makeText(this, "Saved to Saved list", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSession() {
        if (items.isEmpty()) return
        TrainingSession.name = editingName.ifEmpty { "Session" }
        TrainingSession.clear()
        TrainingSession.items.addAll(items)
        // Config sets up permissions and starts the first training, the same way a single training starts
        startActivity(Intent(this, ConfigActivity::class.java).putExtra("session", true))
        finish()
    }

    /** Length of one training in seconds, rest between sets included, as the setup screen counts it. */
    private fun seconds(c: Config): Long {
        if (c.key == Trainings.MEDITATE) return c.medMin * 60L
        val active = (if (c.key == Trainings.BREATH) 16 else c.repSec) * c.sets
        val rest = c.restSec * (c.sets - 1)
        return (active + rest).toLong()
    }

    private fun summary(cfg: Config): String = when (cfg.key) {
        Trainings.MEDITATE -> "${cfg.medMin} min"
        Trainings.BREATH -> "${cfg.sets} cycles \u00B7 ${cfg.restSec}s rest"
        else -> "${cfg.sets} sets \u00B7 ${cfg.repSec}s per rep \u00B7 ${cfg.restSec}s rest"
    }
}
