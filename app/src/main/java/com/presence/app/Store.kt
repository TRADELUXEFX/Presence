package com.presence.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * This phone's copy of each account's profile, and the trainings not saved online yet.
 * A revision counter tells Sync whether the profile has changes the server does not have.
 */
object LocalStore {
    private const val PENDING = "pending_trainings"

    private fun profileKey(uid: String) = "profile_$uid"
    private fun revKey(uid: String) = "rev_$uid"
    private fun savedRevKey(uid: String) = "saved_rev_$uid"

    private fun rev(uid: String): Long = Session.prefs.getLong(revKey(uid), 0L)
    private fun savedRev(uid: String): Long = Session.prefs.getLong(savedRevKey(uid), 0L)

    @Synchronized
    fun hasProfile(uid: String): Boolean = Session.prefs.contains(profileKey(uid))

    @Synchronized
    fun profile(uid: String): JSONObject {
        val s = Session.prefs.getString(profileKey(uid), null)
        if (s != null) {
            try {
                return JSONObject(s)
            } catch (e: Exception) {
            }
        }
        return Profile.fresh(uid)
    }

    /** A local change: the profile now differs from what the server has. */
    @Synchronized
    fun saveProfile(uid: String, row: JSONObject) {
        Session.prefs.edit()
            .putString(profileKey(uid), row.toString())
            .putLong(revKey(uid), rev(uid) + 1)
            .apply()
    }

    @Synchronized
    fun isDirty(uid: String): Boolean = rev(uid) != savedRev(uid)

    /** The profile and its revision, read together, for uploading. */
    @Synchronized
    fun snapshot(uid: String): Pair<Long, JSONObject> = Pair(rev(uid), profile(uid))

    @Synchronized
    fun markSaved(uid: String, rev: Long) {
        Session.prefs.edit().putLong(savedRevKey(uid), rev).apply()
    }

    /** Takes the server's copy, unless this phone has changes the server has not seen yet. */
    @Synchronized
    fun adoptServer(uid: String, row: JSONObject) {
        if (isDirty(uid)) return
        Session.prefs.edit().putString(profileKey(uid), row.toString()).apply()
    }

    /** When the last finished training was saved, so its feel rating can be stored against it. */
    @Volatile var lastTrainingTs = 0L

    /** Saves a finished training on the phone: updates the profile and queues the session row. */
    @Synchronized
    fun recordTraining(uid: String, key: String, sec: Int, meta: Meta): JSONObject {
        val ts = System.currentTimeMillis()
        lastTrainingTs = ts
        val row = Profile.record(profile(uid), key, sec)
        val queue = pendingArray()
        queue.put(Profile.sessionRow(uid, key, sec, meta, ts))
        Session.prefs.edit().putString(PENDING, queue.toString()).apply()
        saveProfile(uid, row)
        return row
    }

    @Synchronized
    fun pendingFor(uid: String): List<JSONObject> {
        val a = pendingArray()
        val out = ArrayList<JSONObject>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.optString("user_id") == uid) out.add(o)
        }
        return out
    }

    @Synchronized
    fun removePending(row: JSONObject) {
        val a = pendingArray()
        val keep = JSONArray()
        var removed = false
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val same = !removed &&
                o.optLong("ts") == row.optLong("ts") &&
                o.optString("user_id") == row.optString("user_id") &&
                o.optString("mod") == row.optString("mod")
            if (same) removed = true else keep.put(o)
        }
        Session.prefs.edit().putString(PENDING, keep.toString()).apply()
    }

    private fun pendingArray(): JSONArray {
        return try {
            JSONArray(Session.prefs.getString(PENDING, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
    }
}

/**
 * Saves queued trainings and profile changes to Supabase, and loads the profile back.
 * One background thread, so two syncs never run at the same time.
 */
object Sync {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Saves what is waiting. Calls back on the main thread with true when everything was saved. */
    fun flush(done: ((Boolean) -> Unit)? = null) {
        worker.execute {
            val ok = flushNow()
            if (done != null) main.post { done(ok) }
        }
    }

    /** Saves what is waiting, then loads the profile from the server. True if the server answered. */
    fun refresh(done: (Boolean) -> Unit) {
        worker.execute {
            flushNow()
            val uid = Session.userId
            val row = if (Session.loggedIn()) Api.fetchProfile(uid) else null
            if (row != null && row.has("id")) LocalStore.adoptServer(uid, row)
            main.post { done(row != null) }
        }
    }

    private fun flushNow(): Boolean {
        if (!Session.loggedIn()) return true
        val uid = Session.userId
        var ok = true
        for (row in LocalStore.pendingFor(uid)) {
            if (Api.insertSession(row)) LocalStore.removePending(row) else ok = false
        }
        if (LocalStore.isDirty(uid)) {
            val (rev, profile) = LocalStore.snapshot(uid)
            if (Api.upsertProfile(profile)) LocalStore.markSaved(uid, rev) else ok = false
        }
        return ok
    }
}

/** Tells a screen when the phone goes online or offline (not on the first check). */
class NetWatch(private val ctx: Context, private val onChange: (Boolean) -> Unit) {
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val h = Handler(Looper.getMainLooper())
    private var cb: ConnectivityManager.NetworkCallback? = null
    private var last: Boolean? = null

    companion object {
        fun isOnline(ctx: Context): Boolean {
            return try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                caps != null &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } catch (e: Exception) {
                true
            }
        }
    }

    private fun report() {
        val now = isOnline(ctx)
        if (now == last) return
        last = now
        onChange(now)
    }

    fun start() {
        last = isOnline(ctx)
        if (cb != null) return
        val c = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                h.post { report() }
            }

            override fun onLost(network: Network) {
                h.post { report() }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(c)
            cb = c
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            cb?.let { cm.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
        }
        cb = null
        last = null
    }
}
