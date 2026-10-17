package com.presence.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import java.util.Calendar

/**
 * The daily notice: app icon, "Presence", the count of trainings done today, and a Start button.
 * It stays in the shade. Start opens the Trainings list, so the person picks the training.
 * It shows "No training done" until a training is finished today.
 */
object DailyNotice {
    private const val CHANNEL = "presence_daily"
    private const val ID = 2101

    /** Shows the notice when logged in, and removes it when not. */
    fun sync(ctx: Context) {
        if (Session.loggedIn()) {
            post(ctx)
            scheduleMidnight(ctx)
        } else {
            cancel(ctx)
        }
    }

    private fun midnightIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 52, Intent(ctx, DailyNoticeReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Arms an alarm for just after the next midnight, so the count resets without opening the app. */
    fun scheduleMidnight(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_MONTH, 1)
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 5)
            c.set(Calendar.MILLISECOND, 0)
            val pi = midnightIntent(ctx)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
                }
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
            }
        } catch (e: Exception) {
            Log.w("Presence", "DailyNotice midnight alarm failed", e)
        }
    }

    fun cancel(ctx: Context) {
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(midnightIntent(ctx))
        } catch (e: Exception) {
        }
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID)
    }

    fun post(ctx: Context) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL, "Daily summary", NotificationManager.IMPORTANCE_LOW)
                ch.setShowBadge(false)
                nm.createNotificationChannel(ch)
            }
            val open = PendingIntent.getActivity(
                ctx, 51,
                Intent(ctx, TrainingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val row = RemoteViews(ctx.packageName, R.layout.notice_daily)
            row.setTextViewText(R.id.notice_daily_sub, message(ctx))
            row.setOnClickPendingIntent(R.id.notice_daily_btn, open)
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
            val note = b.setContentTitle("Presence")
                .setCustomContentView(row)
                .setSmallIcon(R.drawable.ic_stat_training)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            nm.notify(ID, note)
        } catch (e: Exception) {
            Log.w("Presence", "DailyNotice step failed", e)
        }
    }

    /** "No training done", or "1/4 training done" once trainings are finished. */
    private fun message(ctx: Context): String {
        val done = doneToday()
        return if (done == 0) "No training done" else "$done/${Trainings.ALL.size} training done"
    }

    private fun doneToday(): Int {
        val uid = Session.userId
        if (!LocalStore.hasProfile(uid)) return 0
        // normalize() clears yesterday's ticks, the same rule the Home screen uses
        val map = Profile.normalize(LocalStore.profile(uid)).optJSONObject("today_done") ?: return 0
        return Trainings.ALL.count { map.optBoolean(it, false) }
    }
}

/** Fires just after midnight (and on date, time or timezone changes): refreshes the notice and arms the next midnight. */
class DailyNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        DailyNotice.sync(ctx)
    }
}
