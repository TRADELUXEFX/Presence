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
    private var editingId: String? = null
    private var editingName = ""
    private var draftEdit = false
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
        // Only a session start brings its own list of trainings. Anything left over from a session that was
        // not finished must not turn the next single training into part of it (it would log it as a session
        // and start the next training on its own after 5 seconds).
        if (!intent.getBooleanExtra("session", false)) TrainingSession.clear()
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
        // "Edit" from Saved list opens the settings to change them and save back to that list
        val editing = intent.getBooleanExtra("edit", false)
        if (saved != null && editing) {
            editingId = saved.optString("id")
            editingName = saved.optString("name")
        }
        // a session from the Session screen: the first training starts straight away
        val sessionStart = intent.getBooleanExtra("session", false) && TrainingSession.active()
        if (sessionStart) {
            key = TrainingSession.items[0].key
            cfg = TrainingSession.items[0]
        }
        // a session item being edited: this screen changes its settings and returns to the session builder
        draftEdit = intent.getBooleanExtra("draftEdit", false) && SessionDraft.editing in SessionDraft.items.indices
        if (draftEdit) {
            key = SessionDraft.items[SessionDraft.editing].key
            cfg = SessionDraft.items[SessionDraft.editing]
        }
        build()
        if (sessionStart) onStartTapped()
        else if (saved != null && !editing) onStartTapped()
    }

    private fun build() {
        val c = this
        // the strip under the bottom bar takes the deep shade of this training's colour
        Ui.setNavColor(c, Ui.mix(accentColor, Color.BLACK, 0.2f))
        val frame = LinearLayout(c)
        frame.orientation = LinearLayout.VERTICAL
        frame.setBackgroundColor(Ui.BG)
        frame.addView(Ui.header(c, Trainings.name(key), accentColor, if (draftEdit) null else saveAction()) { finish() }, LinearLayout.LayoutParams(-1, -2))

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
        Ui.tintedBottomBar(c, bar, accentColor)
        bar.elevation = Ui.dp(c, 8).toFloat()
        startBtn = Ui.pill(c, if (draftEdit) "Save changes" else "Start training", Color.WHITE, Trainings.ink(key))
        startBtn.setOnClickListener { if (draftEdit) saveDraftEdit() else onStartTapped() }
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
        val label = Ui.text(c, if (editingId != null) "Save changes" else "Save", 13f, fg, Ui.BOLD)
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.leftMargin = Ui.dp(c, 6)
        box.addView(label, lp)
        box.setOnClickListener {
            val editId = editingId
            if (editId != null) {
                SavedLists.update(Session.userId, editId, editingName, cfg)
                Toast.makeText(this, "Changes saved to Saved list", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SavedLists.chooseSave(
                this,
                onDefault = { saveAsDefault() },
                onList = {
                    SavedLists.askName(this, accentColor) { name ->
                        SavedLists.add(Session.userId, name, cfg)
                        Toast.makeText(this, "Saved to Saved list", Toast.LENGTH_SHORT).show()
                    }
                },
                accent = accentColor
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
                voiceCard("A calm voice says when to begin and when to rest.")
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
                voiceCard("A calm voice says the name of each phase as you go.")
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
                voiceCard("A calm voice says a short cue about every 30 seconds, like \"Breathe in slowly\" or \"Relax your shoulders\".")
            }
        }

        panel.addTo(estimateCard(), 22)
    }

    /** The vibrate icon: grey when off, green when on. It toggles vibration on its own. */
    private fun vibrateButton(): android.widget.FrameLayout {
        val c = this
        val box = android.widget.FrameLayout(c)
        box.isClickable = true
        box.contentDescription = "Vibration cues"
        fun paint() {
            val on = Session.prefs.getBoolean("vibrate", true)
            box.removeAllViews()
            box.background = Ui.rounded(c, if (on) Trainings.soft(key) else Ui.SOFT, 20)
            box.addView(
                FsIconView(c, Icons.VIBRATE, if (on) Trainings.ink(key) else Ui.TEXT),
                android.widget.FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER)
            )
        }
        paint()
        box.setOnClickListener {
            val now = !Session.prefs.getBoolean("vibrate", true)
            Session.prefs.edit().putBoolean("vibrate", now).apply()
            paint()
            if (now) testBuzz()
        }
        return box
    }

    /** The bell icon: same look as the vibrate icon, switches the soft start and end bell on or off. */
    private fun bellButton(): android.widget.FrameLayout {
        val c = this
        val box = android.widget.FrameLayout(c)
        box.isClickable = true
        box.contentDescription = "Bell sound"
        fun paint() {
            val on = Session.prefs.getBoolean("bell", true)
            box.removeAllViews()
            box.background = Ui.rounded(c, if (on) Trainings.soft(key) else Ui.SOFT, 20)
            box.addView(
                FsIconView(c, Icons.BELL, if (on) Trainings.ink(key) else Ui.TEXT),
                android.widget.FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER)
            )
        }
        paint()
        box.setOnClickListener {
            val now = !Session.prefs.getBoolean("bell", true)
            Session.prefs.edit().putBoolean("bell", now).apply()
            paint()
            if (now) Bell.play(true)
        }
        // hold: choose which bell sounds
        box.setOnLongClickListener {
            box.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            SoundPicker.show(
                this, "Bell sound", "Tap a bell to hear it. It plays at the start and end of a training.",
                Bell.STYLES, Bell.style(), accentColor,
                onHear = { Bell.play(true, it) },
                onChoose = { Bell.setStyle(it) }
            )
            true
        }
        return box
    }

    /** The voice icon (a speaker): tap to switch the spoken cues on or off. */
    private fun speechButton(): android.widget.FrameLayout {
        val c = this
        val box = android.widget.FrameLayout(c)
        box.isClickable = true
        box.contentDescription = "Voice cues"
        fun paint() {
            val on = cfg.voice
            box.removeAllViews()
            box.background = Ui.rounded(c, if (on) Trainings.soft(key) else Ui.SOFT, 20)
            box.addView(
                FsIconView(c, Icons.VOICE, if (on) Trainings.ink(key) else Ui.TEXT),
                android.widget.FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER)
            )
        }
        paint()
        box.setOnClickListener {
            cfg = cfg.copy(voice = !cfg.voice)
            paint()
        }
        return box
    }

    /**
     * The music icon: tap to switch the quiet background sound on or off, hold to choose which sound.
     * It is a separate audio track, so the spoken cues and the bell are not affected.
     */
    private fun musicButton(): android.widget.FrameLayout {
        val c = this
        val box = android.widget.FrameLayout(c)
        box.isClickable = true
        box.contentDescription = "Background sound"
        fun paint() {
            val on = Ambient.selected() != "off"
            box.removeAllViews()
            box.background = Ui.rounded(c, if (on) Trainings.soft(key) else Ui.SOFT, 20)
            box.addView(
                FsIconView(c, Icons.MUSIC, if (on) Trainings.ink(key) else Ui.TEXT),
                android.widget.FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20), Gravity.CENTER)
            )
        }
        paint()
        box.setOnClickListener {
            val now = Ambient.selected()
            if (now != "off") {
                // remember the choice so switching back on brings the same sound
                Session.prefs.edit().putString("ambient_last", now).apply()
                Ambient.setSelected("off")
                Ambient.stopPreview()
            } else {
                val last = Session.prefs.getString("ambient_last", null)
                val back = if (last != null && last != "off" && Ambient.SOUNDS.any { it.first == last }) last else "rain"
                Ambient.setSelected(back)
                Ambient.preview(back)
            }
            paint()
        }
        // hold: choose the sound
        box.setOnLongClickListener {
            box.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            SoundPicker.show(
                this, "Background sound", "A quiet sound under the training. Your voice cues and bell stay clear on top of it.",
                Ambient.SOUNDS, Ambient.selected(), accentColor,
                onHear = { Ambient.preview(it) },
                onChoose = { Ambient.setSelected(it) },
                onClose = {
                    Ambient.stopPreview()
                    paint()
                }
            )
            true
        }
        return box
    }

    /** A short test buzz, so you can feel that vibration works when you switch it on. */
    private fun testBuzz() {
        val vib = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
        if (!vib.hasVibrator()) return
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(android.os.VibrationEffect.createOneShot(150L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vib.vibrate(150L)
        }
    }

    /**
     * One compact row: "Cues and sounds" with a ? that explains each switch, then the four switches side by side
     * (vibration, bell, voice, music), each with its name under it. Tap switches one on or off; hold the bell or music icon for more sound choices.
     * [voiceText] is what the voice does in this training, shown in the ? pop-up.
     */
    private fun cuesRow(voiceText: String): LinearLayout {
        val c = this
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(Ui.text(c, "Cues and sounds", 16f, Ui.TEXT, Ui.MED), LinearLayout.LayoutParams(0, -2, 1f))
        val help = Ui.text(c, "?", 14f, Ui.MUTED, Ui.BOLD)
        help.gravity = Gravity.CENTER
        help.background = Ui.rounded(c, Color.TRANSPARENT, 14, Ui.MUTED, 1)
        help.contentDescription = "What do these do?"
        help.setOnClickListener { Dialogs.cuesHelp(c, key, voiceText) }
        head.addView(help, LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)))
        col.addView(head, LinearLayout.LayoutParams(-1, -2))
        val icons = LinearLayout(c)
        icons.orientation = LinearLayout.HORIZONTAL
        val cells = listOf("Vibrate" to vibrateButton(), "Bell" to bellButton(), "Voice" to speechButton(), "Music" to musicButton())
        for ((name, btn) in cells) {
            val cell = LinearLayout(c)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.addView(btn, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
            cell.addTo(Ui.text(c, name, 12f, Ui.TEXT, Ui.MED), 4)
            icons.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        col.addTo(icons, 14)
        return col
    }

    private fun voiceCard(voiceText: String) {
        panel.addTo(cardOf(cuesRow(voiceText)), 22)
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
                .put("voice", cfg.voice)
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
        // Asked one time only: without it, some phones stop a training when the screen turns off or the app is closed.
        if (!Battery.exempt(this) && !Session.prefs.getBoolean("battery_asked", false)) {
            Session.prefs.edit().putBoolean("battery_asked", true).apply()
            Dialogs.show(
                this,
                "Keep your training running",
                "Many phones stop apps in the background to save battery. To keep a training going with the screen locked or the app closed, tap Allow and choose \"Allow\" or \"Unrestricted\" for Presence, then come back and press Start.",
                "Allow",
                "Not now",
                onPrimary = { Battery.request(this) },
                onSecondary = { askDndThenStart() },
                accent = accentColor
            )
            return
        }
        askDndThenStart()
    }

    private fun askDndThenStart() {
        // Do not disturb is always on during a training. The phone needs your permission once, so it is asked about one time only.
        if (!Dnd.granted(this) && !Session.prefs.getBoolean("dnd_asked", false)) {
            Session.prefs.edit().putBoolean("dnd_asked", true).apply()
            Dialogs.show(
                this,
                "Silence notifications while you train",
                "Presence turns on Do not disturb during each training and switches it back afterwards. The phone needs your permission once. Tap Open settings, switch on Presence, then come back and press Start.",
                "Open settings",
                "Not now",
                onPrimary = {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    } catch (e: Exception) {
                    }
                },
                onSecondary = { startChecked() },
                accent = accentColor
            )
            return
        }
        startChecked()
    }

    private fun startChecked() {
        val want = ArrayList<String>()
        if (needsCamera() && !granted(Manifest.permission.CAMERA)) want.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            want.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (want.isEmpty()) begin() else permLauncher.launch(want.toTypedArray())
    }

    /** Puts the changed settings back into the session being built, then returns to it. */
    private fun saveDraftEdit() {
        SessionDraft.items[SessionDraft.editing] = cfg
        finish()
    }

    override fun onDestroy() {
        Ambient.stopPreview()
        super.onDestroy()
    }

    private fun begin() {
        Engine.start(this, cfg)
        startActivity(Intent(this, TrainingActivity::class.java))
        finish()
    }
}
