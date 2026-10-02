package com.kregosh.mtglifetracker.shared

enum class StatType { NUMERIC, TOGGLE, RING_STAGE }

/** Game rules shared by everyone in a session; only the host can change them. */
data class SessionSettings(
    val startLife               : UInt = 20u,
    val commanderDeathThreshold : UInt = 21u,
    val infectDeathThreshold    : UInt = 10u,
    /** 0 means no limit. */
    val maxPlayers              : Int  = 0,
)

sealed interface ServerMessage {
    data class State(
        val users       : List<UserState>,
        val globalStats : Map<String, UInt>     = emptyMap(),
        /** The player who is the monarch; null while nobody is (or the mechanic isn't in play). */
        val monarch     : String?               = null,
        /** People watching without a seat, by ID, with their display names. */
        val observers   : Map<String, String>   = emptyMap(),
        val hostUserId  : String?               = null,
        val settings    : SessionSettings?      = null,
        /** Bumped by the host to start a new game in the same session. */
        val game        : Long                  = 0,
    ) : ServerMessage

    data class Error(val message: String) : ServerMessage

    /** The session no longer exists: everyone left, or it was cleaned up. */
    data object SessionGone : ServerMessage

    /** Received when someone in the session sends us a friend request. */
    data class FriendRequest(
        val fromUserId      : String,
        val fromDisplayName : String,
    ) : ServerMessage

    /** Received when someone accepts a request we sent them. */
    data class FriendAccepted(
        val fromUserId      : String,
        val fromDisplayName : String,
    ) : ServerMessage
}

data class CreateSessionResponse(val sessionId: String, val sessionCode: String)
data class SessionInfoResponse(
    val sessionId      : String,
    val sessionCode    : String,
    val connectedUsers : Int,
    val settings       : SessionSettings? = null,
    val userIds        : Set<String>      = emptySet(),
)
