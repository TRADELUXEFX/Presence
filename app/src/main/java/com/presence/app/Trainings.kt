package com.presence.app

import android.graphics.Color
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Settings for one training, as chosen on its config screen. */
data class Config(
    val key: String,
    val mode: String,
    val repSec: Int,
    val sets: Int,
    val restSec: Int,
    val medMin: Int,
    val voice: Boolean
)

/** Extra columns saved with a finished training (same names as the website's sessions table). */
class Meta(
    val sets: Int? = null,
    val repSec: Int? = null,
    val restSec: Int? = null,
    val mode: String? = null,
    val medMin: Int? = null
)

/** Website rules for the four trainings: names, defaults, timings, voice lines, quotes. */
object Trainings {
    const val BLINK = "blink"
    const val FIDGET = "fidget"
    const val BREATH = "breath"
    const val MEDITATE = "meditate"
    val ALL = listOf(BLINK, FIDGET, BREATH, MEDITATE)

    /** Website MODE_DEF: default seconds per rep and sets for each mode. */
    private val MODE_DEF = mapOf("open" to Pair(30, 5), "closed" to Pair(45, 4), "mirror" to Pair(60, 3))

    class BreathPhase(val label: String, val instr: String, val color: Int)

    val BREATH_PHASES = listOf(
        BreathPhase("Inhale", "Breathe in slowly through your nose.", Color.parseColor("#8b5cf6")),
        BreathPhase("Hold", "Lungs full. Hold completely still.", Color.parseColor("#d97706")),
        BreathPhase("Exhale", "Release slowly through your mouth.", Color.parseColor("#2EA043")),
        BreathPhase("Hold", "Lungs empty. Rest here.", Color.parseColor("#64748b"))
    )

    val MED_CUES = listOf(
        "Breathe in slowly.", "And breathe out.", "Relax your shoulders.", "Clear your mind.",
        "Stay present.", "Breathe in.", "Breathe out.", "You are calm."
    )

    fun name(k: String): String = when (k) {
        BLINK -> "No Blinking"
        FIDGET -> "No Fidgeting"
        BREATH -> "Box Breathing"
        else -> "Meditation"
    }

    fun shortName(k: String): String = when (k) {
        BLINK -> "Blink"
        FIDGET -> "Fidget"
        BREATH -> "Breath"
        else -> "Meditate"
    }

    fun blurb(k: String): String = when (k) {
        BLINK -> "Hold your gaze without blinking. Control your eyes."
        FIDGET -> "Zero movement. Total body stillness. Iron discipline."
        BREATH -> "4-4-4-4 box method. Reset your nervous system."
        else -> "Sit with yourself. Still mind, still body. Choose your duration."
    }

    /** Colour that marks each training (ring, dot, rest). Chrome and buttons use the kit's blue. */
    fun accent(k: String): Int = when (k) {
        BLINK -> Color.parseColor("#06b6d4")
        FIDGET -> Color.parseColor("#2EA043")
        BREATH -> Color.parseColor("#8b5cf6")
        else -> Color.parseColor("#ec4899")
    }

    fun modeLabel(mode: String): String = when (mode) {
        "closed" -> "Eyes Closed"
        "mirror" -> "Mirror"
        else -> "Eyes Open"
    }

    fun modeDesc(mode: String): String = when (mode) {
        "closed" -> "Eyes closed. Focus on the darkness. Train internal stillness."
        "mirror" -> "Stand before a mirror. Hold eye contact with your reflection. Hardest mode."
        else -> "Eyes open. Fix your gaze on a point ahead. Train without blinking."
    }

    fun instruction(key: String, mode: String): String {
        if (key == BLINK) {
            return when (mode) {
                "closed" -> "Close your eyes. Focus on the darkness behind your lids."
                "mirror" -> "Hold eye contact with your own reflection. Do not look away."
                else -> "Keep your eyes fixed on a single point. Do not blink."
            }
        }
        return when (mode) {
            "closed" -> "Eyes closed. Feel your body. Release every tension."
            "mirror" -> "Eyes on your reflection. Observe without moving."
            else -> "Eyes open. Body completely still. Zero movement."
        }
    }

    fun quotes(k: String): List<String> = when (k) {
        BLINK -> listOf(
            "The eyes reveal what the mouth conceals. Control them.",
            "No blink. No flinch. No surrender.",
            "Unbroken gaze is unbroken will."
        )
        FIDGET -> listOf(
            "The mountain does not tremble for the storm.",
            "Stillness is the loudest signal of calm.",
            "Control your body, then you can control the room."
        )
        BREATH -> listOf(
            "Control the breath. Control the state.",
            "Four in. Four still. Four out. Four still.",
            "This is the off switch for anxiety."
        )
        else -> listOf(
            "The mind that is still can hear everything.",
            "Presence is not found in the future. Only here.",
            "You cannot pour from an empty cup. Fill yours."
        )
    }

    fun doneTitle(k: String): String = when (k) {
        BLINK -> "Eyes Locked."
        FIDGET -> "Body Held."
        BREATH -> "Mind Reset."
        else -> "Mind Still."
    }

    /** Settings a training starts with: the saved default if there is one, otherwise the website's defaults. */
    fun defaultConfig(key: String, defaults: JSONObject): Config {
        val d = defaults.optJSONObject(key) ?: JSONObject()
        return when (key) {
            BLINK, FIDGET -> {
                val mode = d.str("mode") ?: (if (key == BLINK) "open" else "closed")
                val md = MODE_DEF[mode] ?: Pair(30, 5)
                Config(key, mode, d.posInt("repSec", md.first), d.posInt("sets", md.second), d.posInt("restSec", 5), 10, true)
            }
            BREATH -> Config(key, "open", 16, d.posInt("sets", 6), d.posInt("restSec", 10), 10, true)
            else -> Config(key, "open", 0, 0, 0, d.posInt("medMin", 10), true)
        }
    }

    /** The website's "Training time" estimate, word for word. */
    fun estimate(c: Config): String {
        if (c.key == MEDITATE) return "${c.medMin} minute" + (if (c.medMin == 1) "" else "s")
        val active = (if (c.key == BREATH) 16 else c.repSec) * c.sets
        val rest = c.restSec * (c.sets - 1)
        val total = active + rest
        val m = total / 60
        val s = total % 60
        val base = if (m > 0) (if (s > 0) "${m}m ${s}s" else "$m min") else "${s}s"
        return base + (if (rest > 0) " (incl. ${rest}s rest)" else "")
    }

    /** Seconds recorded for a finished training. Rest is not counted, as on the website. */
    fun totalSec(c: Config): Int = when (c.key) {
        MEDITATE -> c.medMin * 60
        BREATH -> 16 * c.sets
        else -> c.repSec * c.sets
    }

    fun meta(c: Config): Meta = when (c.key) {
        MEDITATE -> Meta(medMin = c.medMin)
        BREATH -> Meta(sets = c.sets, restSec = c.restSec)
        else -> Meta(sets = c.sets, repSec = c.repSec, restSec = c.restSec, mode = c.mode)
    }
}

/**
 * The profile row, the same JSON the website stores in Supabase `profiles`. Dates use the website's
 * format ("Mon Oct 12 2026", JavaScript toDateString), so existing streaks carry over unchanged.
 */
object Profile {
    private fun dayOf(d: Date): String = SimpleDateFormat("EEE MMM dd yyyy", Locale.US).format(d)

    fun today(): String = dayOf(Date())

    fun yesterday(): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -1)
        return dayOf(c.time)
    }

    fun fresh(uid: String): JSONObject = JSONObject()
        .put("id", uid)
        .put("sessions", 0)
        .put("total_sec", 0)
        .put("streak", 0)
        .put("full_streak", 0)
        .put("last_date", JSONObject.NULL)
        .put("today_done", JSONObject())
        .put("today_done_date", JSONObject.NULL)
        .put("today_full_counted", false)
        .put("mods", JSONObject().put("blink", 0).put("fidget", 0).put("breath", 0).put("meditate", 0))
        .put("defaults", JSONObject())

    /** The rules the website applies when it loads a profile: a broken streak resets, today's ticks clear on a new day. */
    fun normalize(row: JSONObject): JSONObject {
        val p = JSONObject(row.toString())
        val last = p.str("last_date")
        if (last != null && last != today() && last != yesterday()) p.put("streak", 0)
        if (p.str("today_done_date") != today()) {
            p.put("today_done", JSONObject())
            p.put("today_full_counted", false)
        }
        if (p.optJSONObject("mods") == null) p.put("mods", JSONObject())
        if (p.optJSONObject("defaults") == null) p.put("defaults", JSONObject())
        if (p.optJSONObject("today_done") == null) p.put("today_done", JSONObject())
        return p
    }

    /** Applies one finished training exactly as the website's recordSession does. Returns the new row. */
    fun record(row: JSONObject, mod: String, sec: Int): JSONObject {
        val p = normalize(row)
        val today = today()
        p.put("sessions", p.optInt("sessions", 0) + 1)
        p.put("total_sec", p.optLong("total_sec", 0L) + sec)
        val mods = p.optJSONObject("mods") ?: JSONObject()
        mods.put(mod, mods.optInt(mod, 0) + 1)
        p.put("mods", mods)
        if (p.str("last_date") != today) {
            val streak = if (p.str("last_date") == yesterday()) p.optInt("streak", 0) + 1 else 1
            p.put("streak", streak)
            p.put("last_date", today)
            p.put("today_done", JSONObject())
        }
        p.put("today_done_date", today)
        val done = p.optJSONObject("today_done") ?: JSONObject()
        done.put(mod, true)
        p.put("today_done", done)
        if (Trainings.ALL.all { done.optBoolean(it, false) } && !p.optBoolean("today_full_counted", false)) {
            p.put("full_streak", p.optInt("full_streak", 0) + 1)
            p.put("today_full_counted", true)
        }
        return p
    }

    /** One row for the website's sessions table. */
    fun sessionRow(uid: String, mod: String, sec: Int, meta: Meta, ts: Long): JSONObject = JSONObject()
        .put("user_id", uid)
        .put("mod", mod)
        .put("sec", sec)
        .put("sets", meta.sets ?: JSONObject.NULL)
        .put("rep_sec", meta.repSec ?: JSONObject.NULL)
        .put("rest_sec", meta.restSec ?: JSONObject.NULL)
        .put("mode", meta.mode ?: JSONObject.NULL)
        .put("med_min", meta.medMin ?: JSONObject.NULL)
        .put("ts", ts)
}

/** Text helpers for the screens. "3h 59m 20s" style, as the Progress screen needs it. */
object Fmt {
    fun duration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "${h}h ${m}m ${s}s"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}

/** JSON null comes back as null here (org.json would otherwise return the text "null"). */
fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k, "").ifEmpty { null }

/** A positive whole number, or the default when the value is missing or zero (the website's ||). */
fun JSONObject.posInt(k: String, def: Int): Int {
    val v = optInt(k, 0)
    return if (v > 0) v else def
}
