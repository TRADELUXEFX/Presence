package com.presence.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout

/** The bottom bar: Home, Trainings and History. Blue like the header, with curved top corners; the tab you are on is white. */
object Nav {
    fun bar(a: Activity, active: Int): LinearLayout {
        // the phone's navigation strip takes the same blue, so the bar runs down into it
        Ui.setNavColor(a, Ui.BLUE_DARK)
        val bar = LinearLayout(a)
        bar.orientation = LinearLayout.HORIZONTAL
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.BLUE, Ui.BLUE_DARK))
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
            val col = if (on) Color.WHITE else Color.argb(170, 255, 255, 255)
            val tab = LinearLayout(a)
            tab.orientation = LinearLayout.VERTICAL
            tab.gravity = Gravity.CENTER
            tab.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4))
            val icon = FsIconView(a, icons[i], col)
            val iconLp = LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22))
            iconLp.gravity = Gravity.CENTER_HORIZONTAL
            tab.addView(icon, iconLp)
            val label = Ui.text(a, labels[i], 11f, col, if (on) Ui.BOLD else Ui.MED)
            label.gravity = Gravity.CENTER
            val labelLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            labelLp.gravity = Gravity.CENTER_HORIZONTAL
            labelLp.topMargin = Ui.dp(a, 2)
            tab.addView(label, labelLp)
            tab.setOnClickListener {
                if (!on) {
                    a.startActivity(Intent(a, targets[i]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    a.finish()
                }
            }
            bar.addView(tab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return bar
    }

    /** Back on a tab goes here: Home. */
    fun goHome(a: Activity) {
        a.startActivity(Intent(a, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        a.finish()
    }
}
