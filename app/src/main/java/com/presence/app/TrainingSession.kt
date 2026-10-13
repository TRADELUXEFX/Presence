package com.presence.app

import org.json.JSONObject

/** Trainings done one after another. Kept in memory while the session runs. */
object TrainingSession {
    val items = ArrayList<Config>()
    var index = 0
    var name = "Session"

    /** A finished training of a session that is not complete yet. It is kept here, not saved. */
    class Held(val ts: Long, val key: String, val sec: Int, val meta: Meta, val pos: Int)

    /**
     * A session only counts once its LAST training is done. Until then each finished training waits here,
     * in memory. If the session is left early, they are dropped and nothing is saved.
     */
    val held = ArrayList<Held>()

    fun active(): Boolean = items.isNotEmpty()

    fun hasNext(): Boolean = index + 1 < items.size

    /** Moves to the next training in the session, or returns null at the end. */
    fun next(): Config? {
        if (!hasNext()) return null
        index++
        return items[index]
    }

    fun clear() {
        items.clear()
        held.clear()
        index = 0
    }

    fun position(): String = "${index + 1} of ${items.size}"

    /** Length of one training in seconds, rest between sets included. */
    fun seconds(c: Config): Long {
        if (c.key == Trainings.MEDITATE) return c.medMin * 60L
        val active = (if (c.key == Trainings.BREATH) 16 else c.repSec) * c.sets
        val rest = c.restSec * (c.sets - 1)
        return (active + rest).toLong()
    }
}

/** The session being built, shared with the settings screen while one item is edited. */
object SessionDraft {
    val items = ArrayList<Config>()
    var editing = -1
}

/** Which finished trainings were part of a session, kept on this phone (keyed by the training's time). */
object SessionLog {
    private fun key(uid: String) = "session_log_$uid"

    private fun load(uid: String): JSONObject = try {
        JSONObject(Session.prefs.getString(key(uid), "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    fun put(uid: String, ts: Long, name: String, pos: Int, total: Int) {
        if (uid.isEmpty() || ts <= 0L) return
        val map = load(uid)
        map.put(ts.toString(), JSONObject().put("name", name).put("pos", pos).put("total", total))
        Session.prefs.edit().putString(key(uid), map.toString()).apply()
    }

    fun get(uid: String, ts: Long): JSONObject? = load(uid).optJSONObject(ts.toString())
}
