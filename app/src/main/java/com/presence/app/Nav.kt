package com.presence.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import com.presence.app.Ui.addTo

/** The bottom bar: Home, Trainings and History. Transparent with curved top corners; the tab you are on is blue. */
object Nav {
    fun bar(a: Activity, active: Int): LinearLayout {
        val bar = LinearLayout(a)
        bar.orientation = LinearLayout.HORIZONTAL
        val g = GradientDrawable()
        g.setColor(Color.TRANSPARENT)
        val r = Ui.dp(a, 22).toFloat()
        g.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        bar.background = g
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

    /** Back on a tab goes here: Home. */
    fun goHome(a: Activity) {
        a.startActivity(Intent(a, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        a.finish()
    }
}
