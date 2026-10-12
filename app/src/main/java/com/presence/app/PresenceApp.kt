package com.presence.app

import android.app.Application
import android.content.res.Configuration

class PresenceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
    }

    companion object {
        fun isNight(c: Configuration): Boolean =
            (c.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
