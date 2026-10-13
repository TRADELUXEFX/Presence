package com.presence.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import com.presence.app.Ui.addTo

/** The Trainings tab: the four trainings, or your saved sessions, switched with the toggle. */
class TrainingsActivity : BaseActivity() {
    private lateinit var toggle: LinearLayout
    private lateinit var trainingsView: LinearLayout
    private lateinit var sessionsView: LinearLayout
    private var showSessions = false
    private var group = 0   // Sessions view: 0 = all, 1 = sessions, 2 = trainings
    private lateinit var selectBar: LinearLayout
    private lateinit var selectBtn: TextView
    private lateinit var navBar: View
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
        // blue header: the title, with the Trainings | Sessions switch inside it
        val headBox = LinearLayout(c)
        headBox.orientation = LinearLayout.VERTICAL
        val r = Ui.dp(c, 24).toFloat()
        val headBg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
        headBg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        headBox.background = headBg
        // Select sits in the header on the right (Sessions view only), like on the Saved list screen
        selectBtn = Ui.text(c, "Select", 13f, Color.WHITE, Ui.BOLD)
        selectBtn.gravity = Gravity.CENTER
        selectBtn.background = Ui.rounded(c, Color.parseColor("#33FFFFFF"), 20)
        selectBtn.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8))
        selectBtn.visibility = View.GONE
        selectBtn.setOnClickListener { onSelectTapped() }
        val titleRow = Ui.header(c, "Trainings", action = selectBtn, showBack = false) { }
        titleRow.background = null
        headBox.addView(titleRow, LinearLayout.LayoutParams(-1, -2))
        toggle = LinearLayout(c)
        val toggleWrap = LinearLayout(c)
        toggleWrap.setPadding(Ui.dp(c, 18), Ui.dp(c, 4), Ui.dp(c, 18), Ui.dp(c, 18))
        toggleWrap.addView(toggle, LinearLayout.LayoutParams(-1, -2))
        headBox.addView(toggleWrap, LinearLayout.LayoutParams(-1, -2))
        root.addView(headBox, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 24))
        trainingsView = LinearLayout(c)
        trainingsView.orientation = LinearLayout.VERTICAL
        body.addView(trainingsView, LinearLayout.LayoutParams(-1, -2))
        sessionsView = LinearLayout(c)
        sessionsView.orientation = LinearLayout.VERTICAL
        body.addView(sessionsView, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        // bottom bar, only while selecting saved sessions to delete
        selectBar = LinearLayout(c)
        selectBar.orientation = LinearLayout.HORIZONTAL
        selectBar.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14))
        Ui.tintedBottomBar(c, selectBar, Ui.BLUE)
        selectBar.elevation = Ui.dp(c, 8).toFloat()
        val doneBtn = Ui.pill(c, "Done", Ui.SOFT, Ui.TEXT)
        doneBtn.setOnClickListener {
            selecting = false
            selected.clear()
            render()
        }
        selectBar.addView(doneBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(c, 6) })
        deleteBtn = Ui.pill(c, "Delete", Ui.RED, Color.WHITE)
        deleteBtn.setOnClickListener { confirmDelete() }
        selectBar.addView(deleteBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(c, 6) })
        selectBar.visibility = View.GONE
        Ui.padInsets(selectBar, bottom = true)
        root.addView(selectBar, LinearLayout.LayoutParams(-1, -2))
        navBar = Nav.bar(c, 1)
        root.addView(navBar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        render()
    }

    override fun onBackPressed() {
        Nav.goHome(this)
    }

    override fun onResume() {
        super.onResume()
        if (::toggle.isInitialized) render()
        // back online: send any trainings waiting on this phone
        if (Session.loggedIn() && NetWatch.isOnline(this)) Sync.flush()
    }

    private fun render() {
        renderToggle()
        trainingsView.visibility = if (showSessions) View.GONE else View.VISIBLE
        sessionsView.visibility = if (showSessions) View.VISIBLE else View.GONE
        if (!showSessions) { selecting = false; selected.clear(); selectBtn.visibility = View.GONE }
        selectBar.visibility = if (showSessions && selecting) View.VISIBLE else View.GONE
        navBar.visibility = if (showSessions && selecting) View.GONE else View.VISIBLE
        if (showSessions) renderSessions() else renderTrainings()
    }

    /** Two halves, like Training Stats | Training History: the one you pick is blue. */
    private fun renderToggle() {
        val c = this
        toggle.removeAllViews()
        toggle.orientation = LinearLayout.HORIZONTAL
        toggle.background = Ui.rounded(c, Color.parseColor("#33FFFFFF"), 26)
        toggle.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
        for ((i, label) in listOf("Trainings", "Sessions").withIndex()) {
            val on = (i == 1) == showSessions
            val t = Ui.text(c, label, 14f, if (on) Ui.BLUE else Ui.WHITE, Ui.BOLD)
            t.gravity = Gravity.CENTER
            t.background = Ui.rounded(c, if (on) Ui.WHITE else Color.TRANSPARENT, 22)
            t.setPadding(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12))
            t.setOnClickListener {
                showSessions = i == 1
                render()
            }
            toggle.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun renderTrainings() {
        trainingsView.removeAllViews()
        if (!NetWatch.isOnline(this)) trainingsView.addView(Ui.offlineRow(this), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@TrainingsActivity, 12) })
        for ((i, k) in Trainings.ALL.withIndex()) {
            trainingsView.addTo(trainingCard(k), if (i == 0) 0 else 12)
        }
    }

    /** One training as a card: name, what it is, and Open. Used on the Trainings and Sessions lists. */
    private fun trainingCard(k: String): LinearLayout {
        val c = this
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14))
        val open = Ui.pill(c, "Open", Trainings.accent(k), Color.parseColor("#0F172A"))
        open.textSize = 14f
        open.setOnClickListener { openTraining(k) }
        val openLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40))
        openLp.leftMargin = Ui.dp(c, 10)
        // name + Open share the top row; the description sits below at the full card width
        card.addView(Ui.nameRow(c, Trainings.name(k), listOf(Pair(open as View, openLp)),
            Ui.text(c, Trainings.about(k), 12f, Ui.MUTED, Ui.REG)))
        card.setOnClickListener { openTraining(k) }
        return card
    }

    private fun openTraining(key: String) {
        startActivity(Intent(this, ConfigActivity::class.java).putExtra("key", key))
    }

    private fun renderSessions() {
        val c = this
        sessionsView.removeAllViews()
        if (!NetWatch.isOnline(this)) sessionsView.addView(Ui.offlineRow(this), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@TrainingsActivity, 12) })
        // All | Sessions | Trainings: groups the list, like the History filter
        val chips = LinearLayout(c)
        chips.orientation = LinearLayout.HORIZONTAL
        for ((i, label) in listOf("All", "Sessions", "Trainings").withIndex()) {
            val sel = i == group
            // same colours in light and dark: white on blue when selected, blue on white when not
            val t = Ui.text(c, label, 13f, if (sel) Ui.WHITE else Ui.BLUE, Ui.BOLD)
            t.gravity = Gravity.CENTER
            t.background = Ui.rounded(c, if (sel) Ui.BLUE else Ui.SURFACE, 20)
            t.setPadding(Ui.dp(c, 16), Ui.dp(c, 9), Ui.dp(c, 16), Ui.dp(c, 9))
            t.setOnClickListener {
                group = i
                render()
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (i > 0) lp.leftMargin = Ui.dp(c, 8)
            chips.addView(t, lp)
        }
        val all = SavedLists.all(Session.userId)
        selected.retainAll(all.map { it.optString("id") }.toSet())
        if (all.isEmpty()) selecting = false
        selectBar.visibility = if (selecting) View.VISIBLE else View.GONE
        navBar.visibility = if (selecting) View.GONE else View.VISIBLE
        deleteBtn.text = if (selected.isEmpty()) "Delete" else "Delete ${selected.size}"
        deleteBtn.alpha = if (selected.isEmpty()) 0.45f else 1f
        selectBtn.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        selectBtn.text = when {
            !selecting -> "Select"
            selected.size == all.size -> "None"
            else -> "All"
        }
        sessionsView.addTo(chips, 0)

        if (!selecting) {
            val build = Ui.pill(c, "+ Build a session", Ui.SOFT, Ui.TEXT)
            build.setOnClickListener { startActivity(Intent(c, SessionActivity::class.java)) }
            sessionsView.addTo(build, 12)
        } else {
            sessionsView.addTo(Ui.note(c, "Tap the ones you want to delete."), 12)
        }

        val shown = all.filter { o ->
            val isSession = o.optString("kind") == "session"
            when (group) {
                1 -> isSession
                2 -> !isSession
                else -> true
            }
        }
        if (shown.isEmpty() && group == 1) sessionsView.addTo(Ui.note(c, "No saved sessions yet. Build one above."), 14)
        for (o in shown) {
            val row = if (o.optString("kind") == "session") sessionRow(o) else singleRow(o)
            sessionsView.addTo(row, 12)
        }
    }

    /** A single saved training, shown in the same list as sessions. */
    private fun singleRow(o: org.json.JSONObject): LinearLayout {
        val c = this
        val id = o.optString("id")
        val cfg = SavedLists.toConfig(o)
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16))
        val edit = Ui.pill(c, "Edit", Ui.SOFT, Ui.TEXT)
        edit.textSize = 13f
        edit.setOnClickListener {
            startActivity(Intent(c, ConfigActivity::class.java).putExtra("listId", id).putExtra("edit", true))
        }
        val editLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40)).apply { leftMargin = Ui.dp(c, 8) }
        val tint = Trainings.accent(cfg.key)
        val start = Ui.pill(c, "Start", tint, Ui.onColor(tint))
        start.textSize = 14f
        start.setOnClickListener {
            startActivity(Intent(c, ConfigActivity::class.java).putExtra("listId", id))
        }
        val startLp1 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40)).apply { leftMargin = Ui.dp(c, 8) }
        // name + buttons share the top row; the details sit below at the full card width
        val head = Ui.nameRow(c, o.optString("name"),
            if (selecting) emptyList() else listOf(Pair(edit as View, editLp), Pair(start as View, startLp1)),
            Ui.clockLine(c, Trainings.name(cfg.key), Trainings.estimate(cfg)))
        if (selecting) card.setOnClickListener { toggleSelect(id) }
        // long press to rename
        card.isLongClickable = !selecting
        card.setOnLongClickListener {
            SavedLists.askName(this) { name ->
                SavedLists.rename(Session.userId, o.optString("id"), name)
                render()
            }
            true
        }
        card.addView(head)
        if (selecting && selected.contains(id)) markSelected(card)
        return card
    }

    /** A saved session: its name, how many trainings and how long, then a pencil and Start. */
    private fun sessionRow(o: org.json.JSONObject): LinearLayout {
        val c = this
        val items = SavedLists.sessionItems(o)
        val secs = items.sumOf { TrainingSession.seconds(it) }
        val card = Ui.card(c)
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16))
        val pencil = Ui.pill(c, "Edit", Ui.SOFT, Ui.TEXT)
        pencil.textSize = 13f
        pencil.setOnClickListener {
            startActivity(Intent(c, SessionActivity::class.java).putExtra("listId", o.optString("id")))
        }
        val pencilLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40))
        pencilLp.leftMargin = Ui.dp(c, 8)
        val tint = if (items.isEmpty()) Ui.BLUE else Trainings.accent(items[0].key)
        val start = Ui.pill(c, "Start", tint, Ui.onColor(tint))
        start.textSize = 14f
        start.setOnClickListener {
            TrainingSession.name = o.optString("name")
            TrainingSession.clear()
            TrainingSession.items.addAll(items)
            startActivity(Intent(c, ConfigActivity::class.java).putExtra("session", true))
        }
        val startLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(c, 40))
        startLp.leftMargin = Ui.dp(c, 8)
        // name + buttons share the top row; the details sit below at the full card width
        val id = o.optString("id")
        val head = Ui.nameRow(c, o.optString("name"),
            if (selecting) emptyList() else listOf(Pair(pencil as View, pencilLp), Pair(start as View, startLp)),
            Ui.clockLine(c, "Session \u00B7 ${items.size} trainings", Fmt.duration(secs)))
        if (selecting) card.setOnClickListener { toggleSelect(id) }
        // long press to rename
        card.isLongClickable = !selecting
        card.setOnLongClickListener {
            SavedLists.askName(this) { name ->
                SavedLists.rename(Session.userId, o.optString("id"), name)
                render()
            }
            true
        }
        card.addView(head)
        if (selecting && selected.contains(id)) markSelected(card)
        return card
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

    /** A ticked card turns blue, with white text. */
    private fun markSelected(card: LinearLayout) {
        card.background = Ui.rounded(this, Ui.BLUE, 22)
        fun paint(v: View) {
            if (v is TextView) v.setTextColor(Color.WHITE)
            if (v is FsIconView) v.setColor(Color.WHITE)
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) paint(v.getChildAt(i))
        }
        paint(card)
    }

    private fun toggleSelect(id: String) {
        if (!selected.add(id)) selected.remove(id)
        render()
    }

    private fun confirmDelete() {
        val n = selected.size
        if (n == 0) return
        Dialogs.show(
            this,
            if (n == 1) "Delete this one?" else "Delete $n items?",
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
}
