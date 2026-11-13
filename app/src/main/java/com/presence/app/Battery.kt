package com.presence.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

/**
 * Many phones (Tecno, Infinix, Xiaomi, Samsung and others) stop apps in the background to save battery, even while
 * a training notification is showing. Being exempt from battery saving is what keeps a training running with the
 * screen off or the app closed.
 */
object Battery {
    fun exempt(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (e: Exception) {
        true
    }

    /**
     * Opens the phone's battery list, where you can allow this app to run in the background.
     * This uses the settings list instead of the one-tap permission dialog, which Google Play restricts.
     */
    fun request(a: Activity) {
        try {
            a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
        }
    }
}
