package com.kregosh.mtglifetracker.shared

const val LIFE_STAT      = "life"
const val COMMANDER_STAT = "commander"
const val POISON_STAT    = "poison"

// Commander damage is tracked per opponent. The ':' can't appear in a custom stat
// name, so these keys never collide with one.
private const val COMMANDER_DAMAGE_PREFIX = "$COMMANDER_STAT:"

/** Stat key for commander damage dealt by [fromUserId]'s commander. */
fun commanderDamageStat(fromUserId: String): String = "$COMMANDER_DAMAGE_PREFIX$fromUserId"

/** The opponent a commander-damage stat key refers to, or null for any other stat. */
fun commanderDamageSource(stat: String): String? =
    if (stat.startsWith(COMMANDER_DAMAGE_PREFIX)) stat.removePrefix(COMMANDER_DAMAGE_PREFIX) else null
