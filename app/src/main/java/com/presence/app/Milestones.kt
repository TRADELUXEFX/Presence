package com.presence.app

/** Short messages for the day-streak milestones shown on the finish screen. */
object Milestones {
    fun message(streak: Int): String? = when (streak) {
        7 -> "7 days in a row. A whole week of showing up."
        30 -> "30 days in a row. This is becoming a habit."
        100 -> "100 days in a row. Remarkable."
        else -> null
    }
}
