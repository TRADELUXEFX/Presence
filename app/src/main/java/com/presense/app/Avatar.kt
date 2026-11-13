package com.presense.app

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The Google account photo, shown in the round profile icon. Saved on the phone so it also shows offline. */
object Avatar {
    private const val FILE = "avatar.png"

    /** A round ImageView. It starts with the app's own default avatar and switches to the Google photo when there is one. */
    fun view(c: Activity): ImageView {
        val iv = ImageView(c)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
        }
        iv.clipToOutline = true
        iv.setImageResource(R.drawable.default_avatar)
        return iv
    }

    /** Google links end in =s96-c (a tiny photo); ask for a sharper one. */
    private fun sharp(url: String): String = url.replace(Regex("=s\\d+(-c)?$"), "=s256-c")

    /** Shows the saved photo at once, then downloads it again only if the link changed. */
    fun show(c: Activity, iv: ImageView, url: String) {
        if (url.isEmpty()) return
        val f = File(c.filesDir, FILE)
        if (f.exists() && Session.prefs.getString("u_avatar_src", "") == url) {
            BitmapFactory.decodeFile(f.path)?.let { iv.setImageBitmap(it); iv.visibility = View.VISIBLE }
            return
        }
        Thread {
            try {
                val con = URL(sharp(url)).openConnection() as HttpURLConnection
                con.connectTimeout = 8000
                con.readTimeout = 8000
                if (con.responseCode != 200) return@Thread
                val bytes = con.inputStream.use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@Thread
                f.writeBytes(bytes)
                Session.prefs.edit().putString("u_avatar_src", url).apply()
                c.runOnUiThread {
                    if (c.isFinishing || c.isDestroyed) return@runOnUiThread
                    iv.setImageBitmap(bmp)
                    iv.visibility = View.VISIBLE
                }
            } catch (e: Exception) { }
        }.start()
    }
}
