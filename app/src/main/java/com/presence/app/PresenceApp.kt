package com.presence.app

import android.app.Application
import android.content.res.Configuration

class PresenceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        // if the app was closed in the middle of a training, give the phone its normal mode back
        Dnd.restore(this)
        // the Sunday summary notification (on unless switched off)
        Weekly.apply(this)
    }

    companion object {
        fun isNight(c: Configuration): Boolean =
            (c.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
