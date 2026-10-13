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

    /** Marks revision [rev] as saved. If nothing changed on this phone meanwhile, keeps the merged copy too. */
    @Synchronized
    fun markSavedMerged(uid: String, rev: Long, merged: JSONObject) {
        val e = Session.prefs.edit()
        if (rev(uid) == rev) e.putString(profileKey(uid), merged.toString())
        e.putLong(savedRevKey(uid), rev).apply()
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
            val o = a.optJSONObject(i) ?: continue
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
            val o = a.optJSONObject(i) ?: continue
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
 * Combines this phone's profile with the server's copy, so two phones on one account do not erase each other.
 * Counts take the higher number, the day state follows the later day, and the lists are joined.
 */
object ProfileMerge {
    private fun dayMs(s: String?): Long = try {
        if (s == null) 0L else (java.text.SimpleDateFormat("EEE MMM dd yyyy", java.util.Locale.US).parse(s)?.time ?: 0L)
    } catch (e: Exception) {
        0L
    }

    fun merge(server: JSONObject, local: JSONObject): JSONObject {
        val out = JSONObject(local.toString())
        for (k in listOf("sessions", "total_sec", "full_streak")) {
            out.put(k, Math.max(server.optLong(k, 0L), local.optLong(k, 0L)))
        }
        // streak follows whichever side trained on the later day
        val sd = dayMs(server.str("last_date"))
        val ld = dayMs(local.str("last_date"))
        when {
            sd > ld -> {
                out.put("last_date", server.opt("last_date"))
                out.put("streak", server.optInt("streak", 0))
            }
            sd == ld -> out.put("streak", Math.max(server.optInt("streak", 0), local.optInt("streak", 0)))
        }
        // today's ticks: the later day wins; the same day joins both phones' ticks
        val sDay = dayMs(server.str("today_done_date"))
        val lDay = dayMs(local.str("today_done_date"))
        if (sDay > lDay) {
            out.put("today_done", server.optJSONObject("today_done") ?: JSONObject())
            out.put("today_done_date", server.opt("today_done_date"))
            out.put("today_full_counted", server.optBoolean("today_full_counted", false))
        } else if (sDay == lDay) {
            val a = local.optJSONObject("today_done") ?: JSONObject()
            val b = server.optJSONObject("today_done") ?: JSONObject()
            val joined = JSONObject(a.toString())
            for (k in Trainings.ALL) if (b.optBoolean(k, false)) joined.put(k, true)
            val order = a.optJSONArray("order") ?: JSONArray()
            val seen = HashSet<String>()
            for (i in 0 until order.length()) seen.add(order.optString(i))
            b.optJSONArray("order")?.let { bo -> for (i in 0 until bo.length()) if (seen.add(bo.optString(i))) order.put(bo.optString(i)) }
            joined.put("order", order)
            out.put("today_done", joined)
            out.put("today_full_counted", local.optBoolean("today_full_counted", false) || server.optBoolean("today_full_counted", false))
        }
        // per-training counts
        val mods = local.optJSONObject("mods") ?: JSONObject()
        val smods = server.optJSONObject("mods") ?: JSONObject()
        for (k in Trainings.ALL) mods.put(k, Math.max(mods.optInt(k, 0), smods.optInt(k, 0)))
        out.put("mods", mods)
        out.put("defaults", mergeDefaults(server.optJSONObject("defaults") ?: JSONObject(), local.optJSONObject("defaults") ?: JSONObject()))
        return out
    }

    private fun mergeDefaults(server: JSONObject, local: JSONObject): JSONObject {
        val out = JSONObject(local.toString())
        // settings the other phone has and this one does not
        val keys = server.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!out.has(k)) out.put(k, server.get(k))
        }
        // how it felt: join, this phone wins on the same training
        val feel = JSONObject(server.optJSONObject("feel")?.toString() ?: "{}")
        local.optJSONObject("feel")?.let { lf ->
            val it = lf.keys()
            while (it.hasNext()) {
                val k = it.next()
                feel.put(k, lf.get(k))
            }
        }
        if (feel.length() > 0) out.put("feel", feel)
        // saved lists: join by id, minus anything either phone deleted
        val removed = LinkedHashSet<String>()
        server.optJSONArray("saved_removed")?.let { for (i in 0 until it.length()) removed.add(it.optString(i)) }
        local.optJSONArray("saved_removed")?.let { for (i in 0 until it.length()) removed.add(it.optString(i)) }
        val saved = JSONArray()
        val ids = HashSet<String>()
        for (o in (local.optJSONArray("saved")?.objects() ?: emptyList())) {
            val id = o.optString("id")
            if (id !in removed && ids.add(id)) saved.put(o)
        }
        for (o in (server.optJSONArray("saved")?.objects() ?: emptyList())) {
            val id = o.optString("id")
            if (id !in removed && ids.add(id)) saved.put(o)
        }
        if (saved.length() > 0 || out.has("saved")) out.put("saved", saved)
        if (removed.isNotEmpty()) out.put("saved_removed", JSONArray(removed.toList().takeLast(200)))
        return out
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
            val (rev, local) = LocalStore.snapshot(uid)
            // Another phone may have saved since this one last looked, so read the server's copy first and
            // combine the two. Without this, the last phone to save would wipe out the other's counts and lists.
            val server = Api.fetchProfile(uid)
            if (server == null) {
                ok = false
            } else {
                val merged = if (server.has("id")) ProfileMerge.merge(server, local) else local
                if (Api.upsertProfile(merged)) LocalStore.markSavedMerged(uid, rev, merged) else ok = false
            }
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
