package com.kregosh.mtglifetracker.shared

data class UserState(
    val id              : String,
    val displayName     : String,
    val life            : UInt              = 20u,
    val customStats     : Map<String, UInt> = emptyMap(),
    val conceded        : Boolean           = false,
    val online          : Boolean           = true,
    /** Commander damage taken, keyed by the user ID of the opponent who dealt it. */
    val commanderDamage : Map<String, UInt> = emptyMap(),
)
