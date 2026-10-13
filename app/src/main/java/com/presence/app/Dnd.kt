package com.presence.app

import android.app.NotificationManager
import android.content.Context

/**
 * Do not disturb while a training runs. It needs the one-time "Do not disturb access" that the phone
 * asks for in its own settings. It only changes the phone's mode if the phone is in normal mode, and it
 * only switches back what it switched itself.
 */
object Dnd {
    /** Always on: there is no switch for it. It only works once the phone has given permission. */
    fun enabled(): Boolean = true

    private fun nm(ctx: Context): NotificationManager? =
        ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun granted(ctx: Context): Boolean = nm(ctx)?.isNotificationPolicyAccessGranted ?: false

    /** Turns Do not disturb on for the training, if it is switched on here and allowed by the phone. */
    fun engage(ctx: Context) {
        try {
            if (!enabled() || !granted(ctx)) return
            val m = nm(ctx) ?: return
            if (m.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
            m.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            Session.prefs.edit().putBoolean("dnd_set", true).apply()
        } catch (e: Exception) {
        }
    }

    /** Puts the phone back to normal, but only if this app was the one that changed it. */
    fun restore(ctx: Context) {
        try {
            if (!Session.prefs.getBoolean("dnd_set", false)) return
            Session.prefs.edit().remove("dnd_set").apply()
            if (!granted(ctx)) return
            val m = nm(ctx) ?: return
            if (m.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                m.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: Exception) {
        }
    }
}
