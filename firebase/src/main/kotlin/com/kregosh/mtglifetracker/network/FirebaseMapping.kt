package com.kregosh.mtglifetracker.network

import com.google.firebase.database.DataSnapshot
import com.kregosh.mtglifetracker.shared.SessionSettings

internal fun SessionSettings.toMap(): Map<String, Any> = mapOf(
    "startLife"               to startLife.toLong(),
    "commanderDeathThreshold" to commanderDeathThreshold.toLong(),
    "infectDeathThreshold"    to infectDeathThreshold.toLong(),
    "maxPlayers"              to maxPlayers.toLong(),
)

/** Null for sessions created before settings were shared. */
internal fun DataSnapshot.toSessionSettings(): SessionSettings? {
    if (!exists()) return null
    val defaults = SessionSettings()
    fun uint(key: String, default: UInt) = child(key).getValue(Long::class.java)?.toUInt() ?: default
    return SessionSettings(
        startLife               = uint("startLife", defaults.startLife),
        commanderDeathThreshold = uint("commanderDeathThreshold", defaults.commanderDeathThreshold),
        infectDeathThreshold    = uint("infectDeathThreshold", defaults.infectDeathThreshold),
        maxPlayers              = child("maxPlayers").getValue(Long::class.java)?.toInt() ?: defaults.maxPlayers,
    )
}

internal fun DataSnapshot.toUIntMap(): Map<String, UInt> =
    children.associate { (it.key ?: "") to (it.getValue(Long::class.java) ?: 0L).toUInt() }
