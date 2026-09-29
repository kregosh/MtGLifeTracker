package com.kregosh.mtglifetracker.shared

data class UserState(
    val id              : String,
    val displayName     : String,
    val life            : UInt              = 20u,
    val commanderDamage : UInt              = 0u,
    val poisonDamage    : UInt              = 0u,
    val customStats     : Map<String, UInt> = emptyMap(),
)
