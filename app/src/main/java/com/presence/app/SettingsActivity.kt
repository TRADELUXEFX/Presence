package com.presence.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.presence.app.Ui.addTo
import org.json.JSONObject

/** Settings: dark mode, daily reminder, saved defaults per training, sign out. */
class SettingsActivity : BaseActivity() {
    private lateinit var column: LinearLayout
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

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
        root.addView(Ui.header(c, "Settings") { finish() }, LinearLayout.LayoutParams(-1, -2))

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
        render()
    }

    private fun render() {
        val c = this
        column.removeAllViews()

        column.addTo(Ui.sectionTitle(c, "Appearance"), 0)
        column.addTo(
            cardOf(Ui.switchRow(c, "Dark mode", "Turn off for light mode", Settings.isDark(c)) { on -> setDark(on) }),
            8
        )

        column.addTo(Ui.sectionTitle(c, "Daily reminder"), 22)
        column.addTo(
            cardOf(
                Ui.switchRow(c, "Remind me each day", "A notification at your chosen time", Settings.reminderOn) { on ->
                    setReminder(on)
                },
                timeRow()
            ),
            8
        )

        column.addTo(Ui.sectionTitle(c, "Saved defaults"), 22)
        val defaults = LocalStore.profile(Session.userId).optJSONObject("defaults") ?: JSONObject()
        val rows = ArrayList<View>()
        for (k in Trainings.ALL) rows.add(defaultRow(k, defaults))
        column.addTo(cardOf(*rows.toTypedArray()), 8)

        column.addTo(Ui.sectionTitle(c, "Account"), 22)
        val out = Ui.pill(c, "Sign out", Ui.SOFT, Ui.RED)
        out.setOnClickListener { confirmSignOut() }
        column.addTo(out, 8)
    }

    /** A card holding one or more rows, spaced apart. */
    private fun cardOf(vararg rows: View): LinearLayout {
        val card = Ui.card(this)
        for ((i, r) in rows.withIndex()) card.addTo(r, if (i == 0) 0 else 14)
        return card
    }

    private fun timeRow(): LinearLayout {
        val c = this
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(Ui.text(c, "Reminder time", 14f, Ui.TEXT, Ui.MED), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Ui.text(c, formatMinutes(Settings.reminderMinutes), 14f, Ui.BLUE, Ui.BOLD))
        row.setOnClickListener {
            val m = Settings.reminderMinutes
            TimePickerDialog(c, { _, h, mi ->
                Settings.reminderMinutes = h * 60 + mi
                Reminders.apply(c)
                render()
            }, m / 60, m % 60, false).show()
        }
        return row
    }

    private fun formatMinutes(m: Int): String {
        val h24 = m / 60
        val mi = m % 60
        val ampm = if (h24 < 12) "AM" else "PM"
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        return "$h12:" + mi.toString().padStart(2, '0') + " $ampm"
    }

    private fun defaultRow(k: String, defaults: JSONObject): LinearLayout {
        val c = this
        val cfg = Trainings.defaultConfig(k, defaults)
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(c)
        left.orientation = LinearLayout.VERTICAL
        left.addView(Ui.text(c, Trainings.name(k), 14f, Ui.TEXT, Ui.MED))
        left.addTo(Ui.text(c, summary(cfg), 12f, Ui.MUTED, Ui.REG), 2)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Ui.text(c, "Edit", 13f, Ui.BLUE, Ui.BOLD))
        row.setOnClickListener {
            startActivity(Intent(c, ConfigActivity::class.java).putExtra("key", k))
        }
        return row
    }

    private fun summary(cfg: Config): String = when (cfg.key) {
        Trainings.MEDITATE -> "${cfg.medMin} min"
        Trainings.BREATH -> "${cfg.sets} cycles · ${cfg.restSec}s rest"
        else -> "${Trainings.modeLabel(cfg.mode)} · ${cfg.sets} sets · ${cfg.repSec}s per rep · ${cfg.restSec}s rest"
    }

    private fun setDark(on: Boolean) {
        Settings.setDark(on)
        // Restart at Home so every screen picks up the new palette.
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun setReminder(on: Boolean) {
        Settings.reminderOn = on
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Reminders.apply(this)
        render()
    }

    private fun confirmSignOut() {
        Dialogs.show(
            this,
            "Sign out?",
            "Trainings not yet saved stay on this phone and sync the next time you log in to this account.",
            "Sign out",
            "Cancel",
            onPrimary = { signOut() }
        )
    }

    private fun signOut() {
        Reminders.cancel(this)
        Session.clear()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}
