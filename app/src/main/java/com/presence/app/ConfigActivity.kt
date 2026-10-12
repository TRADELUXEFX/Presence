package com.presence.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.presence.app.Ui.addTo
import org.json.JSONObject

/** Config for one training: mode, reps, sets, rest, minutes, voice. Starts the training. */
class ConfigActivity : BaseActivity() {
    private lateinit var key: String
    private lateinit var cfg: Config
    private lateinit var panel: LinearLayout

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
        build()
    }

    private fun build() {
        val c = this
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.header(c, Trainings.name(key)) { finish() }, LinearLayout.LayoutParams(-1, -2))

        panel = LinearLayout(c)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 24))
        renderPanel()
        root.addView(panel, LinearLayout.LayoutParams(-1, -2))
        Ui.padInsets(root, bottom = true)

        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Ui.BG)
        scroll.addView(root)
        setContentView(scroll)
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
                        Ui.stepper(c, "Seconds per rep", { cfg.repSec }, { v -> setCfg(cfg.copy(repSec = v)) }, 5, 300, 5, show = { v -> "${v}s" }),
                        Ui.stepper(c, "Sets", { cfg.sets }, { v -> setCfg(cfg.copy(sets = v)) }, 1, 20, 1, show = { v -> "$v" }),
                        Ui.stepper(c, "Rest (seconds)", { cfg.restSec }, { v -> setCfg(cfg.copy(restSec = v)) }, 1, 60, 1, show = { v -> "${v}s" })
                    ),
                    8
                )
                voiceCard("Announce begin, halfway & rest")
            }
            Trainings.BREATH -> {
                panel.addTo(Ui.sectionTitle(c, "Cycles (16s each)"), 0)
                panel.addTo(
                    cardOf(
                        Ui.stepper(c, "Cycles", { cfg.sets }, { v -> setCfg(cfg.copy(sets = v)) }, 1, 30, 1, show = { v -> "$v" }),
                        Ui.stepper(c, "Rest (seconds)", { cfg.restSec }, { v -> setCfg(cfg.copy(restSec = v)) }, 0, 60, 1, show = { v -> "${v}s" })
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
                    val chip = Ui.text(c, "${m}m", 14f, if (sel) Ui.WHITE else Ui.TEXT, Ui.BOLD)
                    chip.gravity = Gravity.CENTER
                    chip.background = Ui.rounded(c, if (sel) Ui.BLUE else Ui.SURFACE, 18, if (sel) 0 else Ui.LINE, if (sel) 0 else 1)
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
                        Ui.stepper(c, "Minutes", { cfg.medMin }, { v -> setCfg(cfg.copy(medMin = v)) }, 1, 120, 1, fastFrom = 10, fastStep = 5, show = { v -> "$v min" })
                    ),
                    0
                )
                panel.addTo(Ui.sectionTitle(c, "Guided breathing cues"), 22)
                panel.addTo(
                    cardOf(
                        Ui.switchRow(c, "Guided breathing cues", "Gentle voice cues every 30 seconds", cfg.voice) { on ->
                            cfg = cfg.copy(voice = on)
                        }
                    ),
                    0
                )
            }
        }

        panel.addTo(estimateCard(), 22)
        val saveBtn = Ui.pill(c, "Set as default", Ui.SOFT, Ui.TEXT)
        saveBtn.setOnClickListener { saveAsDefault(saveBtn) }
        panel.addTo(saveBtn, 16)
        val startBtn = Ui.pill(c, "Start training")
        startBtn.setOnClickListener { onStartTapped() }
        panel.addTo(startBtn, 10)
    }

    private fun voiceCard(sub: String) {
        panel.addTo(Ui.sectionTitle(this, "Voice guidance"), 22)
        panel.addTo(
            cardOf(
                Ui.switchRow(this, "Voice guidance", sub, cfg.voice) { on ->
                    cfg = cfg.copy(voice = on)
                }
            ),
            8
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
            tile.background = Ui.rounded(c, Ui.SURFACE, 18, if (sel) Ui.BLUE else Ui.LINE, if (sel) 2 else 1)
            val iconPath = when (m) {
                "closed" -> Icons.EYE_OFF
                "mirror" -> Icons.MIRROR
                else -> Icons.EYE
            }
            tile.addView(
                FsIconView(c, iconPath, if (sel) Ui.BLUE else Ui.MUTED),
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
        card.background = Ui.rounded(c, Ui.BLUE_SOFT, 22)
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16))
        card.addView(Ui.text(c, "Training time", 12f, Ui.MUTED, Ui.MED))
        card.addTo(Ui.text(c, Trainings.estimate(cfg), 20f, Ui.BLUE, Ui.BOLD), 4)
        return card
    }

    private fun saveAsDefault(btn: Button) {
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
        btn.text = "Saved as default"
        btn.postDelayed({ btn.text = "Set as default" }, 1500L)
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
