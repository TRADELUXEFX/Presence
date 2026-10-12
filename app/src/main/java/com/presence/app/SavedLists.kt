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
        return List(arr.length()) { arr.getJSONObject(it) }
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

    fun remove(uid: String, ids: Set<String>) {
        save(uid) { list -> list.removeAll { ids.contains(it.optString("id")) } }
    }

    private fun save(uid: String, change: (MutableList<JSONObject>) -> Unit) {
        val row = LocalStore.profile(uid)
        val defaults = row.optJSONObject("defaults") ?: JSONObject()
        val arr = defaults.optJSONArray(FIELD) ?: JSONArray()
        val list = MutableList(arr.length()) { arr.getJSONObject(it) }
        change(list)
        defaults.put(FIELD, JSONArray(list))
        row.put("defaults", defaults)
        LocalStore.saveProfile(uid, row)
        Sync.flush()
    }

    /** Save pop-up: two full-width buttons stacked, Save as default above Save as list. */
    fun chooseSave(a: Activity, onDefault: () -> Unit, onList: () -> Unit) {
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

        val asList = Ui.pill(a, "Save as list")
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
    fun askName(a: Activity, onSave: (String) -> Unit) {
        if (a.isFinishing) return
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = Ui.card(a)
        card.setPadding(Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 18))
        card.addView(Ui.text(a, "Name your list", 18f, Ui.TEXT, Ui.BOLD))
        val hint = Ui.text(a, "Name this training so you can start it from your saved list.", 14f, Ui.MUTED, Ui.REG)
        hint.setLineSpacing(0f, 1.25f)
        card.addTo(hint, 8)

        val field = Ui.input(a, "e.g. Morning focus", false)
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
        val save = Ui.pill(a, "Save")
        save.setOnClickListener {
            val name = field.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(a, "Write a name first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
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
