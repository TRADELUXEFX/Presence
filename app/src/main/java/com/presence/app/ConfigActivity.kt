package com.presence.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.presence.app.Ui.addTo
import org.json.JSONObject

/** Config for one training: mode, reps, sets, rest, minutes, voice. Starts the training. */
class ConfigActivity : BaseActivity() {
    private lateinit var key: String
    private lateinit var cfg: Config
    private lateinit var panel: LinearLayout
    private lateinit var startBtn: Button
    private val accentColor: Int get() = Trainings.accent(key)

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        if (needsCamera() && !granted(Manifest.permission.CAMERA)) {
            Dialogs.show(
                this,
                "Camera needed",
                "Mirror mode uses the front camera. Allow camera access, or choose Eyes Open or Eyes Closed.",
                "OK"
            )
        } else {
            begin()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.loggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        if (Engine.isActive()) {
            startActivity(Intent(this, TrainingActivity::class.java))
            finish()
            return
        }
        val requested = intent.getStringExtra("key") ?: Trainings.BLINK
        key = if (Trainings.ALL.contains(requested)) requested else Trainings.BLINK
        val defaults = LocalStore.profile(Session.userId).optJSONObject("defaults") ?: JSONObject()
        cfg = Trainings.defaultConfig(key, defaults)
        // opened from Saved list: use that saved training and start it straight away
        val saved = intent.getStringExtra("listId")?.let { id ->
            SavedLists.all(Session.userId).firstOrNull { it.optString("id") == id }
        }
        if (saved != null) {
            key = saved.optString("key")
            cfg = SavedLists.toConfig(saved)
        }
        build()
        if (saved != null) onStartTapped()
    }

    private fun build() {
        val c = this
        val frame = LinearLayout(c)
        frame.orientation = LinearLayout.VERTICAL
        frame.setBackgroundColor(Ui.BG)
        frame.addView(Ui.header(c, Trainings.name(key), accentColor, saveAction()) { finish() }, LinearLayout.LayoutParams(-1, -2))

        panel = LinearLayout(c)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 24))
        renderPanel()
        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(panel)
        frame.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Start training stays on screen like PauzFirst's bottom bar: curved top, never scrolled away
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.VERTICAL
        bar.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14))
        Ui.bottomBarBg(c, bar)
        bar.elevation = Ui.dp(c, 8).toFloat()
        startBtn = Ui.pill(c, "Start training", accentColor, Ui.onColor(accentColor))
        startBtn.setOnClickListener { onStartTapped() }
        bar.addView(startBtn, LinearLayout.LayoutParams(-1, -2))
        Ui.padInsets(bar, bottom = true)
        frame.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(frame)
    }

    /** Save list button in the header, on the right: asks for a name, then adds this training to Saved list. */
    private fun saveAction(): LinearLayout {
        val c = this
        val fg = Ui.onColor(accentColor)
        val box = LinearLayout(c)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.background = Ui.rounded(c, Color.parseColor("#33FFFFFF"), 20)
        box.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8))
        box.contentDescription = "Save"
        box.addView(FsIconView(c, Icons.BOOKMARK, fg), LinearLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16)))
        val label = Ui.text(c, "Save", 13f, fg, Ui.BOLD)
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.leftMargin = Ui.dp(c, 6)
        box.addView(label, lp)
        box.setOnClickListener {
            SavedLists.chooseSave(
                this,
                onDefault = { saveAsDefault() },
                onList = {
                    SavedLists.askName(this) { name ->
                        SavedLists.add(Session.userId, name, cfg)
                        Toast.makeText(this, "Saved to Saved list", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        return box
    }

    /** Any change rebuilds the panel, so every control shows the current settings. */
    private fun setCfg(next: Config) {
        cfg = next
        renderPanel()
    }

    private fun renderPanel() {
        val c = this
        panel.removeAllViews()
        when (key) {
            Trainings.BLINK, Trainings.FIDGET -> {
                panel.addTo(Ui.sectionTitle(c, "Training mode"), 0)
                panel.addTo(modeTiles(), 8)
                panel.addTo(
                    Ui.text(c, Trainings.modeDesc(cfg.mode), 13f, Ui.MUTED, Ui.REG).apply {
                        gravity = Gravity.CENTER
                        setLineSpacing(0f, 1.25f)
                    },
                    10
                )
                panel.addTo(Ui.sectionTitle(c, "Duration & sets"), 22)
                panel.addTo(
                    cardOf(
                        Ui.stepper(c, "Seconds per rep", { cfg.repSec }, { v -> setCfg(cfg.copy(repSec = v)) }, 5, 300, 5, tint = accentColor, show = { v -> "${v}s" }),
                        Ui.stepper(c, "Sets", { cfg.sets }, { v -> setCfg(cfg.copy(sets = v)) }, 1, 20, 1, tint = accentColor, show = { v -> "$v" }),
                        Ui.stepper(c, "Rest (seconds)", { cfg.restSec }, { v -> setCfg(cfg.copy(restSec = v)) }, 1, 60, 1, tint = accentColor, show = { v -> "${v}s" })
                    ),
                    8
                )
                voiceCard("Announce begin, halfway & rest")
            }
            Trainings.BREATH -> {
                panel.addTo(Ui.sectionTitle(c, "Cycles (16s each)"), 0)
                panel.addTo(
                    cardOf(
                        Ui.stepper(c, "Cycles", { cfg.sets }, { v -> setCfg(cfg.copy(sets = v)) }, 1, 30, 1, tint = accentColor, show = { v -> "$v" }),
                        Ui.stepper(c, "Rest (seconds)", { cfg.restSec }, { v -> setCfg(cfg.copy(restSec = v)) }, 0, 60, 1, tint = accentColor, show = { v -> "${v}s" })
                    ),
                    8
                )
                voiceCard("Announce each phase name")
            }
            else -> {
                panel.addTo(Ui.sectionTitle(c, "Preset duration"), 0)
                val row = LinearLayout(c)
                row.orientation = LinearLayout.HORIZONTAL
                val presets = listOf(5, 10, 15, 20, 30)
                for (m in presets) {
                    val sel = cfg.medMin == m
                    val chip = Ui.text(c, "${m}m", 14f, if (sel) Ui.onColor(accentColor) else Ui.TEXT, Ui.BOLD)
                    chip.gravity = Gravity.CENTER
                    chip.background = Ui.rounded(c, if (sel) accentColor else Ui.SURFACE, 18, if (sel) 0 else Ui.LINE, if (sel) 0 else 1)
                    chip.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12))
                    chip.setOnClickListener { setCfg(cfg.copy(medMin = m)) }
                    val lp = LinearLayout.LayoutParams(0, -2, 1f)
                    if (m != presets.first()) lp.leftMargin = Ui.dp(c, 6)
                    row.addView(chip, lp)
                }
                panel.addTo(row, 8)
                panel.addTo(Ui.sectionTitle(c, "Custom duration"), 22)
                panel.addTo(
                    cardOf(
                        Ui.stepper(c, "Minutes", { cfg.medMin }, { v -> setCfg(cfg.copy(medMin = v)) }, 1, 120, 1, fastFrom = 10, fastStep = 5, tint = accentColor, show = { v -> "$v min" })
                    ),
                    8
                )
                // one label only: the card's own switch label, with the same gap above as the other sections
                panel.addTo(
                    cardOf(
                        Ui.switchRow(c, "Guided breathing cues", "Gentle voice cues every 30 seconds", cfg.voice, accentColor) { on ->
                            cfg = cfg.copy(voice = on)
                        }
                    ),
                    22
                )
            }
        }

        panel.addTo(estimateCard(), 22)
    }

    private fun voiceCard(sub: String) {
        // one label only: the card's own switch label, with the same gap above as the other sections
        panel.addTo(
            cardOf(
                Ui.switchRow(this, "Voice guidance", sub, cfg.voice, accentColor) { on ->
                    cfg = cfg.copy(voice = on)
                }
            ),
            22
        )
    }

    /** A card holding one or more rows, spaced apart. */
    private fun cardOf(vararg rows: View): LinearLayout {
        val card = Ui.card(this)
        for ((i, r) in rows.withIndex()) {
            card.addTo(r, if (i == 0) 0 else 14)
        }
        return card
    }

    private fun modeTiles(): LinearLayout {
        val c = this
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        val modes = if (key == Trainings.BLINK) listOf("open", "mirror") else listOf("closed", "open", "mirror")
        for ((i, m) in modes.withIndex()) {
            val sel = cfg.mode == m
            val tile = LinearLayout(c)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            tile.setPadding(Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14))
            tile.background = Ui.rounded(c, Ui.SURFACE, 18, if (sel) accentColor else Ui.LINE, if (sel) 2 else 1)
            val iconPath = when (m) {
                "closed" -> Icons.EYE_OFF
                "mirror" -> Icons.MIRROR
                else -> Icons.EYE
            }
            tile.addView(
                FsIconView(c, iconPath, if (sel) accentColor else Ui.MUTED),
                LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26))
            )
            tile.addTo(Ui.text(c, Trainings.modeLabel(m), 13f, Ui.TEXT, Ui.MED).apply { gravity = Gravity.CENTER }, 8)
            tile.addTo(Ui.text(c, diffLabel(m), 11f, Ui.MUTED, Ui.REG).apply { gravity = Gravity.CENTER }, 2)
            tile.setOnClickListener { setCfg(cfg.copy(mode = m)) }
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            if (i > 0) lp.leftMargin = Ui.dp(c, 8)
            row.addView(tile, lp)
        }
        return row
    }

    private fun diffLabel(m: String): String = when (m) {
        "closed" -> "Normal"
        "open" -> if (key == Trainings.BLINK) "Normal" else "Focused"
        else -> "Advanced"
    }

    private fun estimateCard(): LinearLayout {
        val c = this
        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.rounded(c, Trainings.soft(key), 22)
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16))
        card.gravity = android.view.Gravity.CENTER_HORIZONTAL
        val tl = Ui.text(c, "Training time", 12f, Ui.MUTED, Ui.MED)
        tl.gravity = android.view.Gravity.CENTER
        card.addView(tl, LinearLayout.LayoutParams(-1, -2))
        val tv = Ui.text(c, Trainings.estimate(cfg), 20f, Trainings.ink(key), Ui.BOLD)
        tv.gravity = android.view.Gravity.CENTER
        card.addTo(tv, 4)
        return card
    }

    private fun saveAsDefault() {
        val uid = Session.userId
        val row = LocalStore.profile(uid)
        val defaults = row.optJSONObject("defaults") ?: JSONObject()
        defaults.put(
            key,
            JSONObject()
                .put("mode", cfg.mode)
                .put("repSec", cfg.repSec)
                .put("sets", cfg.sets)
                .put("restSec", cfg.restSec)
                .put("medMin", cfg.medMin)
        )
        row.put("defaults", defaults)
        LocalStore.saveProfile(uid, row)
        Sync.flush()
        Toast.makeText(this, "Saved as default", Toast.LENGTH_SHORT).show()
    }

    private fun needsCamera(): Boolean =
        cfg.mode == "mirror" && (key == Trainings.BLINK || key == Trainings.FIDGET)

    private fun granted(p: String): Boolean =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun onStartTapped() {
        val want = ArrayList<String>()
        if (needsCamera() && !granted(Manifest.permission.CAMERA)) want.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            want.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (want.isEmpty()) begin() else permLauncher.launch(want.toTypedArray())
    }

    private fun begin() {
        Engine.start(this, cfg)
        startActivity(Intent(this, TrainingActivity::class.java))
        finish()
    }
}
