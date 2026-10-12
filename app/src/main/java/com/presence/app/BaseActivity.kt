package com.presence.app

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Every screen: picks the light or dark palette for this launch and draws behind the system bars. */
abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.dark = Settings.isDark(this)
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        window.setBackgroundDrawable(ColorDrawable(Ui.BG))
    }
}
