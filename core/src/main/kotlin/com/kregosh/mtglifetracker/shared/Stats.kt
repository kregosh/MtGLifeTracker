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
    MONARCH   ("monarch",    StatType.TOGGLE),
    INITIATIVE("initiative", StatType.TOGGLE),
    BLESSING  ("blessing",   StatType.TOGGLE);

    companion object {
        fun fromId(id: String): PredefinedStat? = entries.firstOrNull { it.id == id }
    }
}

const val LIFE_STAT      = "life"
val COMMANDER_STAT       = PredefinedStat.COMMANDER.id
val POISON_STAT          = PredefinedStat.POISON.id

/** Session-wide toggle stored in globalStats rather than per player. */
const val DAY_NIGHT_GLOBAL = "daynight"

// Commander damage is tracked per opponent. The ':' can't appear in a custom stat
// name, so these keys never collide with one.
private const val COMMANDER_DAMAGE_PREFIX = "commander:"

/** Stat key for commander damage dealt by [fromUserId]'s commander. */
fun commanderDamageStat(fromUserId: String): String = "$COMMANDER_DAMAGE_PREFIX$fromUserId"

/** Where a stat key's value lives on a player's seat. */
sealed interface StatTarget {
    data object Life : StatTarget
    data class CommanderDamage(val fromUserId: String) : StatTarget
    data class Custom(val name: String) : StatTarget
}

fun statTarget(stat: String): StatTarget {
    if (stat == LIFE_STAT) return StatTarget.Life
    return commanderDamageSource(stat)?.let(StatTarget::CommanderDamage) ?: StatTarget.Custom(stat)
}

/** Counters never go below zero. */
fun applyDelta(current: Long?, delta: Int): Long = ((current ?: 0L) + delta).coerceAtLeast(0L)

/** The opponent a commander-damage stat key refers to, or null for any other stat. */
fun commanderDamageSource(stat: String): String? =
    if (stat.startsWith(COMMANDER_DAMAGE_PREFIX)) stat.removePrefix(COMMANDER_DAMAGE_PREFIX) else null
