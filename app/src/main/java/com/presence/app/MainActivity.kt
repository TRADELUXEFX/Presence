package com.presence.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Launcher entry. Sends the user to Home when logged in, otherwise to Login. Shows nothing itself. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val next = if (Session.loggedIn()) HomeActivity::class.java else LoginActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}
