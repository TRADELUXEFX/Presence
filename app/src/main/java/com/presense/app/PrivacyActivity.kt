package com.presense.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent

/**
 * The privacy policy opens as the hosted web page in a browser tab (same approach as Anthropic's Legal center).
 * Edit the page on Netlify and every user sees the change without a new APK.
 * This has no screen of its own: it opens the tab and closes, so it works before anyone is signed in.
 */
class PrivacyActivity : Activity() {
    companion object {
        const val PRIVACY_URL = "https://presense-training.netlify.app/privacy"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = Uri.parse(PRIVACY_URL)
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: ActivityNotFoundException) { }
        }
        finish()
    }
}
