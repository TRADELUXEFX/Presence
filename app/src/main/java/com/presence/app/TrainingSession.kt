package com.presence.app

/** Trainings done one after another. Kept in memory while the session runs. */
object TrainingSession {
    val items = ArrayList<Config>()
    var index = 0

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
