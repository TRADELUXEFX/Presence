package com.presence.app

import org.json.JSONObject
import java.util.Calendar

/** The last seven calendar days, including today. Works from the online training history. */
object Weekly {
    class Summary(
        val count: Int,
        val totalSec: Long,
        val days: Int,
        val perMod: Map<String, Int>,
        val entries: List<JSONObject>
    )

    fun summarize(rows: List<JSONObject>, now: Long = System.currentTimeMillis()): Summary {
        val start = weekStart(now)
        val mine = rows.filter { it.optLong("ts", 0L) >= start }
        val days = mine.map { dayKey(it.optLong("ts", 0L)) }.toSet().size
        val perMod = Trainings.ALL.associateWith { k -> mine.count { it.optString("mod") == k } }
        val total = mine.sumOf { it.optLong("sec", 0L) }
        return Summary(mine.size, total, days, perMod, mine)
    }

    /** Local midnight six days ago. */
    fun weekStart(now: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_MONTH, -6)
        return c.timeInMillis
    }

    private fun dayKey(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }
}
