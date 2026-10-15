package com.presence.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The Mirror recording choice on this phone: off, audio only, or video (the screen with its sound). */
object RecPrefs {
    const val OFF = "off"
    const val AUDIO = "audio"
    const val VIDEO = "video"

    var mode: String
        get() = Session.prefs.getString("rec_mode", OFF) ?: OFF
        set(v) {
            Session.prefs.edit().putString("rec_mode", v).apply()
        }

    val on: Boolean get() = mode != OFF
    val video: Boolean get() = mode == VIDEO
}

/**
 * Recordings kept only on this phone, next to the training history. A recording covers the time it was
 * made; a history row shows its Play button when its training happened inside that time.
 */
object Recordings {
    class Rec(val uri: String, val video: Boolean, val startMs: Long, val endMs: Long)

    private fun key(uid: String) = "recs_$uid"

    private fun arr(uid: String): JSONArray = try {
        JSONArray(Session.prefs.getString(key(uid), "[]") ?: "[]")
    } catch (e: Exception) {
        JSONArray()
    }

    @Synchronized
    fun add(uid: String, uri: String, video: Boolean, startMs: Long, endMs: Long) {
        val old = arr(uid)
        val out = JSONArray()
        out.put(JSONObject().put("uri", uri).put("video", video).put("start", startMs).put("end", endMs))
        for (i in 0 until minOf(old.length(), 299)) out.put(old.get(i))
        Session.prefs.edit().putString(key(uid), out.toString()).apply()
    }

    fun all(uid: String): List<Rec> {
        val a = arr(uid)
        val out = ArrayList<Rec>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Rec(o.optString("uri"), o.optBoolean("video"), o.optLong("start"), o.optLong("end")))
        }
        return out
    }

    /**
     * The recording that covers this training, or null. The training ran from [endMs] minus [secs] seconds
     * up to [endMs]. If several recordings overlap it, the one with the longest overlap wins.
     */
    fun forTraining(recs: List<Rec>, endMs: Long, secs: Int): Rec? {
        if (endMs <= 0L) return null
        val slack = 5000L
        val start = endMs - secs * 1000L
        var best: Rec? = null
        var bestLen = Long.MIN_VALUE
        for (r in recs) {
            if (r.startMs > endMs + slack || r.endMs < start - slack) continue
            val len = Math.min(endMs, r.endMs) - Math.max(start, r.startMs)
            if (len > bestLen) {
                best = r
                bestLen = len
            }
        }
        return best
    }

    /** False when the file was deleted or moved. */
    fun exists(ctx: Context, uri: String): Boolean {
        return try {
            val u = Uri.parse(uri)
            if (u.scheme == "file") {
                File(u.path ?: return false).exists()
            } else {
                ctx.contentResolver.openFileDescriptor(u, "r")?.use { true } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }
}
