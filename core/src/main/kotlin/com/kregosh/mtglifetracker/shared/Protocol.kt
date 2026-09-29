package com.kregosh.mtglifetracker.shared

enum class StatType { NUMERIC, TOGGLE, RING_STAGE }

sealed interface ServerMessage {
    data class State(
        val users       : List<UserState>,
        val statDefs    : Map<String, StatType> = emptyMap(),
        val globalStats : Map<String, UInt>     = emptyMap(),
    ) : ServerMessage

    data class Joined(
        val userId      : String,
        val sessionCode : String,
    ) : ServerMessage

    data class Error(val message: String) : ServerMessage
}

data class CreateSessionResponse(val sessionId: String, val sessionCode: String)
data class SessionInfoResponse(val sessionId: String, val sessionCode: String, val connectedUsers: Int)
