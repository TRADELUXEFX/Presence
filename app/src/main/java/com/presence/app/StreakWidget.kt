package com.presence.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: day streak and today's progress, from the same local copy the app uses. */
class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, views(ctx))
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, StreakWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, views(ctx))
        }

        private fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_streak)
            val p = Profile.normalize(LocalStore.profile(Session.userId))
            val streak = p.optInt("streak", 0)
            val done = Trainings.ALL.count { p.optJSONObject("today_done")?.optBoolean(it, false) == true }
            v.setTextViewText(R.id.widget_streak_value, streak.toString())
            v.setTextViewText(R.id.widget_streak_label, "day streak")
            v.setTextViewText(R.id.widget_today, "$done / 4 today")
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
