package com.kregosh.mtglifetracker.network.schema

import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.shared.StatTarget
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState

/**
 * How sessions are laid out in the Realtime Database, as plain maps (the shape of
 * `DataSnapshot.getValue()`). No Firebase types here, so this is unit-testable and
 * the only place that knows the field names. Keep it in sync with database.rules.json.
 */
object SessionSchema {

    fun sessionPath(sessionId: String) = "sessions/$sessionId"
    fun codePath(code: String)         = "sessionCodes/$code"
    fun presencePath(userId: String)   = "presence/$userId"
    fun seatPath(userId: String)       = "users/$userId"

    /** Seat field holding a stat, relative to the seat. */
    fun statField(target: StatTarget): String = when (target) {
        StatTarget.Life                  -> "life"
        is StatTarget.CommanderDamage    -> "commanderDamage/${target.fromUserId}"
        is StatTarget.Custom             -> "customStats/${target.name}"
    }

    data class ParsedSession(
        val code      : String?,
        val createdAt : Long?,
        val state     : ServerMessage.State,
    )

    /** Null when the session doesn't exist. [defaultLife] fills in seats without a life total. */
    fun parseSession(value: Any?, defaultLife: UInt): ParsedSession? {
        val session = value.asMap() ?: return null
        val users = session["users"].asMap().orEmpty().mapNotNull { (id, seat) ->
            seat.asMap()?.let { parseSeat(id, it, defaultLife) }
        }
        val statDefs = session["customStatNames"].asMap().orEmpty()
            .mapValues { (_, type) -> parseStatType(type as? String) }
        val state = ServerMessage.State(
            users       = users,
            statDefs    = statDefs,
            globalStats = session["globalStats"].asUIntMap(),
            hostUserId  = session["hostUserId"] as? String,
            settings    = parseSettings(session["settings"]),
            game        = session["game"].asLong() ?: 0L,
        )
        return ParsedSession(session["code"] as? String, session["createdAt"].asLong(), state)
    }

    fun parseInfo(sessionId: String, value: Any?): SessionInfoResponse? {
        val session = value.asMap() ?: return null
        val userIds = session["users"].asMap().orEmpty().keys
        return SessionInfoResponse(
            sessionId      = sessionId,
            sessionCode    = session["code"] as? String ?: "",
            connectedUsers = userIds.size,
            settings       = parseSettings(session["settings"]),
            userIds        = userIds,
        )
    }

    private fun parseSeat(id: String, seat: Map<String, Any?>, defaultLife: UInt) = UserState(
        id              = id,
        displayName     = seat["displayName"] as? String ?: "",
        life            = seat["life"].asLong()?.toUInt() ?: defaultLife,
        customStats     = seat["customStats"].asUIntMap(),
        conceded        = seat["conceded"] as? Boolean ?: false,
        online          = seat["online"] as? Boolean ?: true,
        commanderDamage = seat["commanderDamage"].asUIntMap(),
        game            = seat["game"].asLong() ?: 0L,
    )

    fun parseStatType(value: String?): StatType =
        StatType.entries.firstOrNull { it.name == value } ?: StatType.NUMERIC

    /** Null for sessions created before rules were shared. */
    fun parseSettings(value: Any?): SessionSettings? {
        val map = value.asMap() ?: return null
        val defaults = SessionSettings()
        return SessionSettings(
            startLife               = map["startLife"].asLong()?.toUInt() ?: defaults.startLife,
            commanderDeathThreshold = map["commanderDeathThreshold"].asLong()?.toUInt() ?: defaults.commanderDeathThreshold,
            infectDeathThreshold    = map["infectDeathThreshold"].asLong()?.toUInt() ?: defaults.infectDeathThreshold,
            maxPlayers              = map["maxPlayers"].asLong()?.toInt() ?: defaults.maxPlayers,
        )
    }

    fun settingsToMap(settings: SessionSettings): Map<String, Any> = mapOf(
        "startLife"               to settings.startLife.toLong(),
        "commanderDeathThreshold" to settings.commanderDeathThreshold.toLong(),
        "infectDeathThreshold"    to settings.infectDeathThreshold.toLong(),
        "maxPlayers"              to settings.maxPlayers.toLong(),
    )

    /** A new session document; the backend adds its own server-side createdAt. */
    fun newSession(code: String, hostUserId: String, settings: SessionSettings): Map<String, Any> = mapOf(
        "code"       to code,
        "hostUserId" to hostUserId,
        "game"       to 0L,
        "settings"   to settingsToMap(settings),
    )

    /** A whole seat, as written when a new game starts. */
    fun seatToMap(seat: UserState): Map<String, Any> = buildMap {
        put("displayName", seat.displayName)
        put("life", seat.life.toLong())
        put("online", seat.online)
        put("game", seat.game)
        if (seat.conceded) put("conceded", true)
        if (seat.customStats.isNotEmpty()) put("customStats", seat.customStats.mapValues { it.value.toLong() })
        if (seat.commanderDamage.isNotEmpty()) put("commanderDamage", seat.commanderDamage.mapValues { it.value.toLong() })
    }

    /** Writes that clear [userIds]' seats in one multi-path update. */
    fun clearSeats(userIds: List<String>): Map<String, Any?> = userIds.associate { seatPath(it) to null }

    /** Writes that remove [stat] from every seat in one multi-path update. */
    fun clearStatFromSeats(userIds: Collection<String>, stat: String): Map<String, Any?> =
        userIds.associate { "${seatPath(it)}/${statField(StatTarget.Custom(stat))}" to null }
}

// Firebase returns maps whose keys are all small integers as lists, and numbers as Long or Double.
private fun Any?.asMap(): Map<String, Any?>? = when (this) {
    is Map<*, *> -> entries.associate { (k, v) -> k.toString() to v }
    is List<*>   -> withIndex().filter { it.value != null }.associate { it.index.toString() to it.value }
    else         -> null
}

private fun Any?.asLong(): Long? = (this as? Number)?.toLong()

private fun Any?.asUIntMap(): Map<String, UInt> =
    asMap().orEmpty().mapNotNull { (k, v) -> v.asLong()?.let { k to it.coerceAtLeast(0L).toUInt() } }.toMap()
