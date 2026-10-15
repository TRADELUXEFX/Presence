package com.presence.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast

/**
 * Opens when a Supabase link (invite or password reset) returns to the app as presence://auth-callback.
 * Supabase puts the login tokens after a # (or ?). Invites and resets go on to choose a password.
 */
class AuthCallbackActivity : BaseActivity() {
    override val needsLogin: Boolean get() = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val msg = Ui.text(this, "Signing you in\u2026", 16f, Ui.TEXT, Ui.MED)
        msg.gravity = Gravity.CENTER
        msg.setBackgroundColor(Ui.BG)
        setContentView(msg)

        val uri: Uri? = intent.data
        val params = HashMap<String, String>()
        val raw = listOfNotNull(uri?.encodedFragment, uri?.encodedQuery).joinToString("&")
        for (pair in raw.split("&")) {
            val i = pair.indexOf('=')
            if (i > 0) params[Uri.decode(pair.substring(0, i))] = Uri.decode(pair.substring(i + 1))
        }
        val access = params["access_token"] ?: ""
        val refresh = params["refresh_token"] ?: ""
        val type = params["type"] ?: ""
        val expires = params["expires_in"]?.toLongOrNull() ?: 3600L
        if (access.isEmpty() || refresh.isEmpty()) {
            fail("This link is not valid. Ask for a new one.")
            return
        }

        Thread {
            val ok = Api.sessionFromLink(access, refresh, expires)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (!ok) {
                    fail("This link has expired. Ask for a new one.")
                    return@runOnUiThread
                }
                val next = if (type == "invite" || type == "recovery") SetPasswordActivity::class.java else HomeActivity::class.java
                startActivity(Intent(this, next).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        }.start()
    }

    private fun fail(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
