package com.presence.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/** Launcher entry: shows the splash, then sends the user to Home when logged in, otherwise to Login. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // a training notification left over from an earlier run is cleared when no training is running
        if (!Engine.isActive()) NotificationManagerCompat.from(this).cancel(TrainingService.NOTIFICATION_ID)
        val next = if (Session.loggedIn()) HomeActivity::class.java else LoginActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}
