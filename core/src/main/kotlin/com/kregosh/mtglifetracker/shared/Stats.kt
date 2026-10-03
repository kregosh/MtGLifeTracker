package com.kregosh.mtglifetracker.shared

/**
 * Per-player stats offered in the stat picker. The [id] is what's stored in
 * Firebase; display labels live in the app's string resources.
 */
enum class PredefinedStat(val id: String, val type: StatType) {
    COMMANDER ("commander",  StatType.NUMERIC),
    POISON    ("poison",     StatType.NUMERIC),
    ENERGY    ("energy",     StatType.NUMERIC),
    EXPERIENCE("experience", StatType.NUMERIC),
    STORM     ("storm",      StatType.NUMERIC),
    TAX       ("tax",        StatType.NUMERIC),
    RING      ("ring",       StatType.RING_STAGE),
    INITIATIVE("initiative", StatType.TOGGLE),
    BLESSING  ("blessing",   StatType.TOGGLE);

    companion object {
        fun fromId(id: String): PredefinedStat? = entries.firstOrNull { it.id == id }
    }
}

const val LIFE_STAT      = "life"
val COMMANDER_STAT       = PredefinedStat.COMMANDER.id
val POISON_STAT          = PredefinedStat.POISON.id

/**
 * Session-wide toggle stored in globalStats rather than per player. The monarch is
 * session-wide too, but names a player (see [ServerMessage.State.monarch]).
 */
const val DAY_NIGHT_GLOBAL = "daynight"

/**
 * Where a stat key's value lives on a player's seat. Commander damage is an ordinary
 * counter ([COMMANDER_STAT]): one total, whichever commander dealt it.
 */
sealed interface StatTarget {
    data object Life : StatTarget
    data class Custom(val name: String) : StatTarget
}

fun statTarget(stat: String): StatTarget =
    if (stat == LIFE_STAT) StatTarget.Life else StatTarget.Custom(stat)

/** Counters never go below zero. */
fun applyDelta(current: Long?, delta: Int): Long = ((current ?: 0L) + delta).coerceAtLeast(0L)
