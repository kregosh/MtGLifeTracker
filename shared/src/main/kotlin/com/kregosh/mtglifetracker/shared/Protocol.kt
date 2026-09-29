package com.kregosh.mtglifetracker.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Shared domain objects
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class UserState(
    val id: String,
    val displayName: String,
    val life: UInt = 20u,
    val commanderDamage: UInt = 0u,
    val poisonDamage: UInt = 0u,
    val customStats: Map<String, UInt> = emptyMap(),
)

// ─────────────────────────────────────────────────────────────────────────────
// Server → Client messages
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
sealed class ServerMessage {

    /** Full snapshot of every player in the session plus the session's custom stat names. */
    @Serializable
    @SerialName("state")
    data class State(
        val users: List<UserState>,
        val customStatNames: List<String> = emptyList(),
    ) : ServerMessage()

    /** Sent to the joining client to confirm its userId and the session code. */
    @Serializable
    @SerialName("joined")
    data class Joined(val userId: String, val sessionCode: String) : ServerMessage()

    /** Error feedback for the client that triggered the problem. */
    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ServerMessage()
}

// ─────────────────────────────────────────────────────────────────────────────
// Client → Server messages
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
sealed class ClientMessage {

    /** First message after connecting; registers the user in the session. */
    @Serializable
    @SerialName("join")
    data class Join(
        val userId: String,
        val displayName: String,
        val startLife: UInt = 20u,
    ) : ClientMessage()

    /**
     * Adjust one stat for the sender by [delta] (positive or negative).
     *
     * [stat] is one of: "life", "commander", "poison", or a custom stat name
     * that was previously added via [AddCustomStat].
     */
    @Serializable
    @SerialName("adjust")
    data class Adjust(val stat: String, val delta: Int) : ClientMessage()

    /** Register a new custom-stat column for the whole session. */
    @Serializable
    @SerialName("add_custom_stat")
    data class AddCustomStat(val name: String) : ClientMessage()
}

// ─────────────────────────────────────────────────────────────────────────────
// REST DTOs
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class CreateSessionResponse(
    val sessionId: String,
    val sessionCode: String,
)

@Serializable
data class SessionInfoResponse(
    val sessionId: String,
    val sessionCode: String,
    val connectedUsers: Int,
)
