package com.presence.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
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

    /** Opens the phone's own "allow in background" prompt for this app, or the general list if that is missing. */
    fun request(a: Activity) {
        try {
            a.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + a.packageName))
            )
        } catch (e: Exception) {
            try {
                a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
            }
        }
    }
}
