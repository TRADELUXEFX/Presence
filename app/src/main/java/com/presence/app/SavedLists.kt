package com.presence.app

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.Toast
import com.presence.app.Ui.addTo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Named trainings the user saved ("Saved list"). Kept inside the account's defaults,
 * so they travel with the profile data the app already keeps and syncs.
 */
object SavedLists {
    private const val FIELD = "saved"

    fun all(uid: String): List<JSONObject> {
        val arr = LocalStore.profile(uid).optJSONObject("defaults")?.optJSONArray(FIELD) ?: JSONArray()
        return arr.objects()
    }

    /** A name for a list the person did not name: "Box Breathing 1", "Session 2", the first number not already used. */
    fun autoName(uid: String, base: String): String {
        val taken = all(uid).map { it.optString("name").trim().lowercase() }.toSet()
        var i = 1
        while (taken.contains("$base $i".lowercase())) i++
        return "$base $i"
    }

    fun toConfig(o: JSONObject): Config = Config(
        o.optString("key"),
        o.optString("mode", "open"),
        o.optInt("repSec", 0),
        o.optInt("sets", 0),
        o.optInt("restSec", 0),
        o.optInt("medMin", 10),
        o.optBoolean("voice", true)
    )

    fun add(uid: String, name: String, cfg: Config) {
        val o = JSONObject()
            .put("id", System.currentTimeMillis().toString())
            .put("name", name)
            .put("key", cfg.key)
            .put("mode", cfg.mode)
            .put("repSec", cfg.repSec)
            .put("sets", cfg.sets)
            .put("restSec", cfg.restSec)
            .put("medMin", cfg.medMin)
            .put("voice", cfg.voice)
        save(uid) { list -> list.add(o) }
    }

    private fun cfgJson(cfg: Config): JSONObject = JSONObject()
        .put("key", cfg.key)
        .put("mode", cfg.mode)
        .put("repSec", cfg.repSec)
        .put("sets", cfg.sets)
        .put("restSec", cfg.restSec)
        .put("medMin", cfg.medMin)
        .put("voice", cfg.voice)

    /** The trainings inside a saved session, in order. */
    fun sessionItems(o: JSONObject): List<Config> {
        val arr = o.optJSONArray("items") ?: return emptyList()
        return arr.objects().map { toConfig(it) }
    }

    /** Saves a named session: several trainings in order, kept in Saved list. */
    fun addSession(uid: String, name: String, items: List<Config>) {
        val o = JSONObject()
            .put("id", System.currentTimeMillis().toString())
            .put("name", name)
            .put("kind", "session")
            .put("key", items.firstOrNull()?.key ?: "")
            .put("items", JSONArray(items.map { cfgJson(it) }))
        save(uid) { list -> list.add(o) }
    }

    /** Saves changes to a session that is already in Saved list. */
    fun updateSession(uid: String, id: String, name: String, items: List<Config>) {
        val o = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("kind", "session")
            .put("key", items.firstOrNull()?.key ?: "")
            .put("items", JSONArray(items.map { cfgJson(it) }))
        save(uid) { list ->
            val i = list.indexOfFirst { it.optString("id") == id }
            if (i >= 0) list[i] = o
        }
    }

    /** Saves changes to an existing saved list, keeping its place. */
    fun update(uid: String, id: String, name: String, cfg: Config) {
        val o = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("key", cfg.key)
            .put("mode", cfg.mode)
            .put("repSec", cfg.repSec)
            .put("sets", cfg.sets)
            .put("restSec", cfg.restSec)
            .put("medMin", cfg.medMin)
            .put("voice", cfg.voice)
        save(uid) { list ->
            val i = list.indexOfFirst { it.optString("id") == id }
            if (i >= 0) list[i] = o
        }
    }

    fun remove(uid: String, ids: Set<String>) {
        save(uid, ids) { list -> list.removeAll { ids.contains(it.optString("id")) } }
    }

    /** [removed]: ids deleted by this change. They are remembered so another phone's copy cannot bring them back. */
    private fun save(uid: String, removed: Set<String> = emptySet(), change: (MutableList<JSONObject>) -> Unit) {
        val row = LocalStore.profile(uid)
        val defaults = row.optJSONObject("defaults") ?: JSONObject()
        if (removed.isNotEmpty()) {
            val tomb = defaults.optJSONArray("saved_removed") ?: JSONArray()
            for (id in removed) tomb.put(id)
            while (tomb.length() > 200) tomb.remove(0)
            defaults.put("saved_removed", tomb)
        }
        val arr = defaults.optJSONArray(FIELD) ?: JSONArray()
        val list = arr.objects().toMutableList()
        change(list)
        defaults.put(FIELD, JSONArray(list))
        row.put("defaults", defaults)
        LocalStore.saveProfile(uid, row)
        Sync.flush()
    }

    /** Save pop-up: two full-width buttons stacked, Save as default above Save as list. */
    fun chooseSave(a: Activity, onDefault: () -> Unit, onList: () -> Unit, accent: Int = Ui.BLUE) {
        if (a.isFinishing) return
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = Ui.card(a)
        card.setPadding(Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 22))
        card.addView(Ui.text(a, "Save", 18f, Ui.TEXT, Ui.BOLD))
        val hint = Ui.text(a, "Save these settings as this training's default, or keep them as a named list.", 14f, Ui.MUTED, Ui.REG)
        hint.setLineSpacing(0f, 1.25f)
        card.addTo(hint, 8)

        val asDefault = Ui.pill(a, "Save as default", Ui.SOFT, Ui.TEXT)
        asDefault.setOnClickListener {
            d.dismiss()
            onDefault()
        }
        card.addTo(asDefault, 20)

        val asList = if (accent == Ui.BLUE) Ui.pill(a, "Save as list") else Ui.pill(a, "Save as list", accent, Ui.onColor(accent))
        asList.setOnClickListener {
            d.dismiss()
            onList()
        }
        card.addTo(asList, 10)

        Ui.noClipDeep(card)
        d.setContentView(card)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((a.resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    /** Small pop-up: type a name, then Save. Calls [onSave] with the trimmed name. */
    /** Changes only the name of a saved training or session. */
    fun rename(uid: String, id: String, name: String) {
        save(uid) { list ->
            val i = list.indexOfFirst { it.optString("id") == id }
            if (i >= 0) list[i] = JSONObject(list[i].toString()).put("name", name)
        }
    }

    fun askName(a: Activity, accent: Int = Ui.BLUE, defaultName: String? = null, onSave: (String) -> Unit) {
        if (a.isFinishing) return
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = Ui.card(a)
        card.setPadding(Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 18))
        card.addView(Ui.text(a, "Name your list", 18f, Ui.TEXT, Ui.BOLD))
        // a name is optional: left empty, the list gets [defaultName]
        val hintText = if (defaultName != null) "Name this list, or leave it empty and it is saved as \"$defaultName\"."
        else "Name this training so you can start it from your saved list."
        val hint = Ui.text(a, hintText, 14f, Ui.MUTED, Ui.REG)
        hint.setLineSpacing(0f, 1.25f)
        card.addTo(hint, 8)

        val field = Ui.input(a, defaultName ?: "e.g. Morning focus", false)
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        card.addTo(field, 16)

        val row = LinearLayout(a)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val cancel = Ui.pill(a, "Cancel", Ui.SOFT, Ui.TEXT)
        cancel.setOnClickListener { d.dismiss() }
        val cancelLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        cancelLp.rightMargin = Ui.dp(a, 6)
        row.addView(cancel, cancelLp)
        val save = if (accent == Ui.BLUE) Ui.pill(a, "Save") else Ui.pill(a, "Save", accent, Ui.onColor(accent))
        save.setOnClickListener {
            var name = field.text.toString().trim()
            if (name.isEmpty()) {
                if (defaultName == null) {
                    Toast.makeText(a, "Write a name first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                name = defaultName
            }
            d.dismiss()
            onSave(name)
        }
        val saveLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        saveLp.leftMargin = Ui.dp(a, 6)
        row.addView(save, saveLp)
        card.addTo(row, 20)

        Ui.noClipDeep(card)
        d.setContentView(card)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((a.resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }
}
