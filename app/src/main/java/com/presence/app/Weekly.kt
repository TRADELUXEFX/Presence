package com.presence.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.util.Calendar

/** What the weekly summary shows: the last 7 days, today included. */
class WeekSummary(
    val count: Int,
    val totalSec: Long,
    val dayTrained: BooleanArray,
    val dayLetters: List<String>,
    val modCount: Map<String, Int>,
    val modSec: Map<String, Long>,
    val streak: Int
) {
    val activeDays: Int get() = dayTrained.count { it }
}

/** The weekly summary: the numbers, and a notification every Sunday at 6:00 PM. */
object Weekly {
    private const val REQUEST = 7002
    private const val NOTIFICATION = 2002
    private const val CHANNEL = "presence_weekly"
    private const val HOUR = 18

    /** On unless switched off on the Notifications screen. */
    var on: Boolean
        get() = Session.prefs.getBoolean("w_on", true)
        set(v) {
            Session.prefs.edit().putBoolean("w_on", v).apply()
        }

    /** Flag on the reminder: the summary it opens shows the week that just ended. */
    const val EXTRA_LAST_WEEK = "last_week"

    /** Counts the trainings of one week, Sunday to Saturday. weeksBack 0 = this week, 1 = last week. */
    fun summarize(rows: List<JSONObject>, streak: Int, weeksBack: Int = 0): WeekSummary {
        val starts = LongArray(8)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // back to the Sunday that starts this week, then to the one before it if asked
        cal.add(Calendar.DAY_OF_MONTH, -(cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY) - 7 * weeksBack)
        val letters = ArrayList<String>()
        val names = arrayOf("S", "M", "T", "W", "T", "F", "S")
        for (i in 0..7) {
            starts[i] = cal.timeInMillis
            if (i < 7) letters.add(names[i])
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        val trained = BooleanArray(7)
        val modCount = HashMap<String, Int>()
        val modSec = HashMap<String, Long>()
        var count = 0
        var total = 0L
        for (r in rows) {
            val ts = r.optLong("ts", 0L)
            val mod = r.optString("mod")
            if (!Trainings.ALL.contains(mod)) continue
            var day = -1
            for (i in 0..6) if (ts >= starts[i] && ts < starts[i + 1]) day = i
            if (day < 0) continue
            val sec = r.optLong("sec", 0L)
            trained[day] = true
            count++
            total += sec
            modCount[mod] = (modCount[mod] ?: 0) + 1
            modSec[mod] = (modSec[mod] ?: 0L) + sec
        }
        return WeekSummary(count, total, trained, letters, modCount, modSec, streak)
    }

    // ---------- Sunday notification ----------

    /** Schedules the next Sunday notification when it is on and someone is logged in; otherwise cancels it. */
    fun apply(ctx: Context) {
        if (on && Session.loggedIn()) schedule(ctx) else cancel(ctx)
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQUEST, Intent(ctx, WeeklyReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextSunday()
        val pi = pending(ctx)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }

    /** The next Sunday at 6:00 PM: today if it is Sunday and still ahead, otherwise the coming Sunday. */
    fun nextSunday(): Long {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, HOUR)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || c.timeInMillis <= now) {
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return c.timeInMillis
    }

    fun showNotification(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Weekly summary", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, WeeklySummaryActivity::class.java).putExtra(EXTRA_LAST_WEEK, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_presence)
            .setColor(Ui.BLUE)
            .setContentTitle("Your week in Presence")
            .setContentText("Your weekly summary is ready. Tap to see it.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION, n)
        } catch (e: SecurityException) {
        }
    }
}

class WeeklyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Weekly.on && Session.loggedIn()) Weekly.showNotification(ctx)
        Weekly.apply(ctx)
    }
}
