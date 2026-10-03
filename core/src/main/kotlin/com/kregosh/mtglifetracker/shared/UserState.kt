package com.kregosh.mtglifetracker.shared

data class UserState(
    val id              : String,
    val displayName     : String,
    val life            : UInt              = 20u,
    val customStats     : Map<String, UInt> = emptyMap(),
    val conceded        : Boolean           = false,
    val online          : Boolean           = true,
    /** The counters this player tracks; each player picks their own. */
    val stats           : Map<String, StatType> = emptyMap(),
    /** The game (see [ServerMessage.State.game]) this seat's stats belong to. */
    val game            : Long              = 0,
)
