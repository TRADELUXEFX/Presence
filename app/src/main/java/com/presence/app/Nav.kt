package com.presence.app

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import com.presence.app.Ui.addTo

/** The bottom bar: Home, Trainings and History. The tab you are on is blue. */
object Nav {
    fun bar(a: Activity, active: Int): LinearLayout {
        val bar = LinearLayout(a)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(Ui.SURFACE)
        bar.elevation = Ui.dp(a, 6).toFloat()
        bar.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8))
        Ui.padInsets(bar, bottom = true)
        val icons = listOf(Icons.HOME, Icons.LIST, Icons.CHART)
        val labels = listOf("Home", "Trainings", "History")
        val targets = listOf(HomeActivity::class.java, TrainingsActivity::class.java, ProgressActivity::class.java)
        for (i in icons.indices) {
            val on = i == active
            val col = if (on) Ui.BLUE else Ui.MUTED
            val tab = LinearLayout(a)
            tab.orientation = LinearLayout.VERTICAL
            tab.gravity = Gravity.CENTER_HORIZONTAL
            tab.addView(FsIconView(a, icons[i], col), LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)))
            tab.addTo(Ui.text(a, labels[i], 11f, col, if (on) Ui.BOLD else Ui.MED), 2)
            tab.setOnClickListener {
                if (!on) {
                    a.startActivity(Intent(a, targets[i]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    a.finish()
                }
            }
            bar.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return bar
    }
}
