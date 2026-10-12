package com.presence.app

import org.json.JSONArray
import org.json.JSONObject

/** The most recent trainings the user finished, one per training, kept in the account's defaults. */
object Recent {
    private const val FIELD = "recent"
    private const val MAX = 3

    fun all(uid: String): List<Config> {
        val arr = LocalStore.profile(uid).optJSONObject("defaults")?.optJSONArray(FIELD) ?: return emptyList()
        return List(arr.length()) { SavedLists.toConfig(arr.getJSONObject(it)) }
    }

    /** Puts this training at the top of the recent list. */
    fun remember(uid: String, cfg: Config) {
        if (uid.isEmpty()) return
        val row = LocalStore.profile(uid)
        val defaults = row.optJSONObject("defaults") ?: JSONObject()
        val arr = defaults.optJSONArray(FIELD) ?: JSONArray()
        val list = MutableList(arr.length()) { arr.getJSONObject(it) }
        list.removeAll { it.optString("key") == cfg.key }
        list.add(0, JSONObject()
            .put("key", cfg.key)
            .put("mode", cfg.mode)
            .put("repSec", cfg.repSec)
            .put("sets", cfg.sets)
            .put("restSec", cfg.restSec)
            .put("medMin", cfg.medMin)
            .put("voice", cfg.voice))
        while (list.size > MAX) list.removeAt(list.size - 1)
        defaults.put(FIELD, JSONArray(list))
        row.put("defaults", defaults)
        LocalStore.saveProfile(uid, row)
    }
}
