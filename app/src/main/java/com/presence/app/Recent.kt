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

    /** A session with no name of its own shows as "Recent session", so those count as one name. */
    private fun nameOf(name: String): String = name.trim().let { if (it.isEmpty() || it == "Session") "Recent session" else it.lowercase() }

    /**
     * Which list this is. A named list is identified by its name: changing its settings or trainings does not
     * make it a new list. A list with no name is identified by the order of its trainings (not their settings).
     */
    private fun listKey(name: String, items: JSONArray?): String {
        val n = nameOf(name)
        if (n != "Recent session") return "n#$n"
        val order = items?.objects()?.joinToString(",") { it.optString("key") } ?: ""
        return "u#$order"
    }

    /** A whole session finished: it goes to the top as one session. Doing the same session again moves it up, not a second copy. */
    fun rememberSession(uid: String, cfgs: List<Config>, name: String = "Session") {
        if (uid.isEmpty() || cfgs.size < 2) return
        // an unnamed (never saved) session is not remembered
        if (nameOf(name) == "Recent session") return
        val items = list(uid)
        val key = listKey(name, JSONArray(cfgs.map { cfgJson(it) }))
        items.removeAll { it.optString("kind") == "session" && listKey(it.optString("name"), it.optJSONArray("items")) == key }
        items.add(0, JSONObject()
            .put("kind", "session")
            .put("name", name)
            .put("items", JSONArray(cfgs.map { cfgJson(it) })))
        save(uid, items)
    }

    /** Quick start: recent trainings and sessions first, then saved sessions and trainings. Three at most. */
    fun quick(uid: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val saved = SavedLists.all(uid)
        fun savedSession(o: JSONObject): JSONObject = JSONObject()
            .put("kind", "saved-session")
            .put("id", o.optString("id"))
            .put("name", o.optString("name"))
            .put("items", o.optJSONArray("items") ?: JSONArray())
        // the saved list is the source of truth: a recent run of a saved list shows the list as it is now
        val savedByKey = HashMap<String, JSONObject>()
        for (o in saved) if (o.optString("kind") == "session") savedByKey.putIfAbsent(listKey(o.optString("name"), o.optJSONArray("items")), o)
        // a list shows once, however many times it was done, and also when it is in Saved list
        val seen = HashSet<String>()
        for (o in list(uid)) {
            if (o.optString("kind") == "session") {
                val k = listKey(o.optString("name"), o.optJSONArray("items"))
                if (!seen.add(k)) continue
                // a session that was never saved is not shown: only saved sessions appear in Quick start
                val s = savedByKey[k] ?: continue
                out.add(savedSession(s))
            } else out.add(o)
        }
        for (o in saved) {
            if (o.optString("kind") == "session") {
                if (!seen.add(listKey(o.optString("name"), o.optJSONArray("items")))) continue
                out.add(savedSession(o))
            } else {
                out.add(JSONObject(o.toString()).put("kind", "saved-single"))
            }
        }
        return out.take(3)
    }
}
