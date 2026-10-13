package com.presence.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the user last did: single trainings and whole sessions, kept in the account's defaults.
 * Quick start on Home is built from this and the saved sessions and trainings.
 */
object Recent {
    private const val FIELD = "recent"
    private const val MAX = 10

    private fun list(uid: String): MutableList<JSONObject> {
        val arr = LocalStore.profile(uid).optJSONObject("defaults")?.optJSONArray(FIELD) ?: return mutableListOf()
        return arr.objects().toMutableList()
    }

    private fun save(uid: String, items: MutableList<JSONObject>) {
        val row = LocalStore.profile(uid)
        val defaults = row.optJSONObject("defaults") ?: JSONObject()
        while (items.size > MAX) items.removeAt(items.size - 1)
        defaults.put(FIELD, JSONArray(items))
        row.put("defaults", defaults)
        LocalStore.saveProfile(uid, row)
    }

    private fun cfgJson(cfg: Config): JSONObject = JSONObject()
        .put("key", cfg.key)
        .put("mode", cfg.mode)
        .put("repSec", cfg.repSec)
        .put("sets", cfg.sets)
        .put("restSec", cfg.restSec)
        .put("medMin", cfg.medMin)
        .put("voice", cfg.voice)

    /** A single training finished: it goes to the top, once per training. */
    fun remember(uid: String, cfg: Config) {
        if (uid.isEmpty()) return
        val items = list(uid)
        items.removeAll { it.optString("kind") == "training" && it.optString("key") == cfg.key }
        items.add(0, cfgJson(cfg)
            .put("kind", "training")
            .put("name", Trainings.name(cfg.key)))
        save(uid, items)
    }

    /** A whole session finished: it goes to the top as one session. */
    fun rememberSession(uid: String, cfgs: List<Config>) {
        if (uid.isEmpty() || cfgs.size < 2) return
        val items = list(uid)
        items.add(0, JSONObject()
            .put("kind", "session")
            .put("name", "Session")
            .put("items", JSONArray(cfgs.map { cfgJson(it) })))
        save(uid, items)
    }

    /** Quick start: recent trainings and sessions first, then saved sessions and trainings. Three at most. */
    fun quick(uid: String): List<JSONObject> {
        val out = ArrayList<JSONObject>(list(uid))
        for (o in SavedLists.all(uid)) {
            if (o.optString("kind") == "session") {
                out.add(JSONObject()
                    .put("kind", "saved-session")
                    .put("id", o.optString("id"))
                    .put("name", o.optString("name"))
                    .put("items", o.optJSONArray("items") ?: JSONArray()))
            } else {
                out.add(JSONObject(o.toString()).put("kind", "saved-single"))
            }
        }
        return out.take(3)
    }
}
