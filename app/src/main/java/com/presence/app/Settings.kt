package com.presence.app

import android.content.Context

/** Preferences kept on this phone: appearance and the daily reminder. */
object Settings {
    fun darkChosen(): Boolean = Session.prefs.contains("s_dark")

    fun darkMode(): Boolean = Session.prefs.getBoolean("s_dark", false)

    fun setDark(on: Boolean) {
        Session.prefs.edit().putBoolean("s_dark", on).apply()
    }

    /** The user's own choice if they made one, otherwise the phone's setting. */
    fun isDark(ctx: Context): Boolean =
        if (darkChosen()) darkMode() else PresenceApp.isNight(ctx.resources.configuration)

    var reminderOn: Boolean
        get() = Session.prefs.getBoolean("r_on", false)
        set(v) {
            Session.prefs.edit().putBoolean("r_on", v).apply()
        }

    /** Minutes after midnight. Default 8:00 PM. */
    var reminderMinutes: Int
        get() = Session.prefs.getInt("r_min", 20 * 60)
        set(v) {
            Session.prefs.edit().putInt("r_min", v).apply()
        }
}
