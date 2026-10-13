package com.presence.app

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

/** Every screen: picks the light or dark palette for this launch and draws behind the system bars. */
abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.dark = Settings.isDark(this)
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        window.setBackgroundDrawable(ColorDrawable(Ui.BG))
    }

    /** Every screen but Login and Forgot password needs a signed-in account. */
    protected open val needsLogin: Boolean get() = true

    override fun onResume() {
        super.onResume()
        // The server can reject the saved login while the app is open. Without this the app would keep working
        // but silently stop saving; send the person to Login instead (never in the middle of a training).
        if (needsLogin && !Session.loggedIn() && !Engine.isActive()) {
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }

    override fun setContentView(view: View?) {
        if (view != null) Ui.noClipDeep(view)
        super.setContentView(view)
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        if (view != null) Ui.noClipDeep(view)
        super.setContentView(view, params)
    }
}
