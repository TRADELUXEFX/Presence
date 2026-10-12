package com.presence.app

import org.json.JSONObject

/** How a finished training felt (Hard, Okay, Great), kept in the account's defaults like the saved lists. */
object Feel {
    private const val FIELD = "feel"
    val OPTIONS = listOf("Hard", "Okay", "Great")

    /** The face drawn on each button as a vector line icon, like the rest of the app's icons. */
    fun face(v: String): String {
        val head = "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M9 9h.01 M15 9h.01"
        return when (v) {
            "Hard" -> head + " M8 16 Q12 13 16 16"
            "Okay" -> head + " M8 15h8"
            "Great" -> head + " M8 14 Q12 17 16 14"
            else -> head
        }
    }

    fun get(uid: String, ts: Long): String? {
        if (ts <= 0L) return null
        val v = LocalStore.profile(uid).optJSONObject("defaults")?.optJSONObject(FIELD)?.optString(ts.toString(), "")
        return if (v.isNullOrEmpty()) null else v
    }

    fun set(uid: String, ts: Long, value: String) {
        val row = LocalStore.profile(uid)
        val defaults = row.optJSONObject("defaults") ?: JSONObject()
        val map = defaults.optJSONObject(FIELD) ?: JSONObject()
        map.put(ts.toString(), value)
        defaults.put(FIELD, map)
        row.put("defaults", defaults)
        LocalStore.saveProfile(uid, row)
        Sync.flush()
    }
}
