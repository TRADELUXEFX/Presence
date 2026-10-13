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

/** Notifications: the daily reminder. Sign out is at the bottom. */
class NotificationsActivity : BaseActivity() {
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
        root.addView(Ui.header(c, "Notifications") { finish() }, LinearLayout.LayoutParams(-1, -2))

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
        column.addTo(Ui.sectionTitle(c, "Daily reminder"), 0)
        column.addTo(
            cardOf(
                Ui.switchRow(c, "Remind me each day", "At your chosen time, skipped if you already trained today", Settings.reminderOn) { on ->
                    setReminder(on)
                },
                timeRow()
            ),
            8
        )
        column.addTo(Ui.sectionTitle(c, "Weekly summary"), 24)
        column.addTo(
            cardOf(
                Ui.switchRow(c, "Weekly summary", "Every Sunday at 6:00 PM", Weekly.on) { on -> setWeekly(on) },
                seeWeekRow()
            ),
            8
        )
    }

    private fun seeWeekRow(): LinearLayout {
        val c = this
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(Ui.text(c, "See this week now", 14f, Ui.BLUE, Ui.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener { startActivity(Intent(c, WeeklySummaryActivity::class.java)) }
        return row
    }

    private fun setWeekly(on: Boolean) {
        Weekly.on = on
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Weekly.apply(this)
        render()
    }

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
}
