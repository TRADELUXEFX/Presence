package com.presence.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import org.json.JSONObject

/** Home-screen widget: the same TODAY card as the Home screen (count, colour bar in finish order, and the four trainings). */
class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, views(ctx))
    }

    companion object {
        private val SEGS = intArrayOf(R.id.seg0, R.id.seg1, R.id.seg2, R.id.seg3)
        private val DOTS = intArrayOf(R.id.dot0, R.id.dot1, R.id.dot2, R.id.dot3)
        private val NAMES = intArrayOf(R.id.name0, R.id.name1, R.id.name2, R.id.name3)
        private val IDLE = Color.parseColor("#E2E8F0")
        private val TEXT = Color.parseColor("#0F172A")

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, StreakWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, views(ctx))
        }

        private fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_streak)
            val p = Profile.normalize(LocalStore.profile(Session.userId))
            val today = p.optJSONObject("today_done") ?: JSONObject()
            val done = Trainings.ALL.count { today.optBoolean(it, false) }

            v.setTextViewText(R.id.widget_title, "TODAY")
            v.setTextViewText(R.id.widget_today, "$done / 4")

            // the bar fills in the order the trainings were finished, same as the Home card
            val order = Trainings.doneOrder(today)
            for (i in SEGS.indices) {
                val color = if (i < order.size) Trainings.accent(order[i]) else IDLE
                v.setInt(SEGS[i], "setBackgroundColor", color)
            }

            // the four trainings, each with its colour dot
            for ((i, k) in Trainings.ALL.withIndex()) {
                if (i >= NAMES.size) break
                v.setTextViewText(NAMES[i], Trainings.name(k))
                v.setTextColor(NAMES[i], TEXT)
                v.setInt(DOTS[i], "setColorFilter", Trainings.accent(k))
            }

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
