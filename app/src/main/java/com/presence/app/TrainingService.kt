package com.presence.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps a training running with the screen off: a foreground notification (with Stop) and a CPU wake lock.
 * The timing itself lives in Engine; this service only keeps the phone from pausing it.
 */
class TrainingService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var shown = ""

    private val listener = object : Engine.Listener {
        override fun onEngine() {
            refresh()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            build(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        up = true
        shown = Engine.statusKey()
        acquireWakeLock()
        Engine.addListener(listener)
        if (!Engine.keepService()) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) Engine.cancel()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        up = false
        Engine.removeListener(listener)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refresh() {
        if (!Engine.keepService()) {
            stopSelf()
            return
        }
        val key = Engine.statusKey()
        if (key == shown) return
        shown = key
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, build(this))
        } catch (e: SecurityException) {
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "presence:training")
            wl.acquire(3 * 60 * 60 * 1000L)
            wakeLock = wl
        } catch (e: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            val wl = wakeLock
            if (wl != null && wl.isHeld) wl.release()
        } catch (e: Exception) {
        }
        wakeLock = null
    }

    companion object {
        const val ACTION_STOP = "com.presence.app.STOP_TRAINING"
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL = "presence_training"

        /** True while the service is running, so a session's next training does not try to start it from the background. */
        @Volatile var up = false

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, TrainingService::class.java))
            } catch (e: Exception) {
            }
        }

        fun stop(ctx: Context) {
            up = false
            try {
                ctx.stopService(Intent(ctx, TrainingService::class.java))
            } catch (e: Exception) {
            }
        }

        private fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Training", NotificationManager.IMPORTANCE_LOW))
        }

        private fun build(ctx: Context): Notification {
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, TrainingActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val stop = PendingIntent.getService(
                ctx, 1, Intent(ctx, TrainingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_presence)
                .setColor(Ui.BLUE)
                .setContentTitle(Trainings.name(Engine.config.key))
                .setContentText(Engine.statusText())
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(R.drawable.ic_stat_presence, "Stop", stop)
                .build()
        }
    }
}
