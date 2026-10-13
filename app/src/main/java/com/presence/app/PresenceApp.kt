package com.presence.app

import android.app.Application
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class PresenceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        // if the app was closed in the middle of a training, give the phone its normal mode back
        Dnd.restore(this)
        // the Sunday summary notification (on unless switched off)
        Weekly.apply(this)
        watchConnection()
    }

    /** Whatever screen is open, send what is waiting the moment the phone gets a working connection again. */
    private fun watchConnection() {
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            var wasOnline = NetWatch.isOnline(this)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val up = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (up && !wasOnline && Session.loggedIn()) Sync.flush()
                    wasOnline = up
                }

                override fun onLost(network: Network) {
                    wasOnline = false
                }
            })
        } catch (e: Exception) {
        }
    }

    companion object {
        fun isNight(c: Configuration): Boolean =
            (c.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
