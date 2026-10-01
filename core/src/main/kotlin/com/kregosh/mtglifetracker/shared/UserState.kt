package com.kregosh.mtglifetracker.shared

data class UserState(
    val id          : String,
    val displayName : String,
    val life        : UInt              = 20u,
    val customStats : Map<String, UInt> = emptyMap(),
    val conceded    : Boolean           = false,
    val online      : Boolean           = true,
)
