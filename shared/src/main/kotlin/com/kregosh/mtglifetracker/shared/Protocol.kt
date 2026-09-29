package com.kregosh.mtglifetracker.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Shared domain objects
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single player's current state inside a session.
 *
 * [value] is an unsigned 32-bit integer — it cannot go below 0.
 */
@Serializable
data class UserState(
    val id: String,
    val displayName: String,
    val value: UInt = 0u,
)

// ─────────────────────────────────────────────────────────────────────────────
// Server → Client messages
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
sealed class ServerMessage {

    /** Full snapshot of every player in the session. Sent after any change. */
    @Serializable
    @SerialName("state")
    data class State(val users: List<UserState>) : ServerMessage()

    /** Sent to the joining client to confirm its assigned userId. */
    @Serializable
    @SerialName("joined")
    data class Joined(val userId: String, val sessionCode: String) : ServerMessage()

    /** Error feedback for the client that caused the problem. */
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
        /** Stable UUID generated once per device install and stored locally. */
        val userId: String,
        val displayName: String,
    ) : ClientMessage()

    /** Increment the caller's [UserState.value] by 1. */
    @Serializable
    @SerialName("increment")
    object Increment : ClientMessage()

    /** Decrement the caller's [UserState.value] by 1 (clamped at 0). */
    @Serializable
    @SerialName("decrement")
    object Decrement : ClientMessage()
}

// ─────────────────────────────────────────────────────────────────────────────
// REST DTOs
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class CreateSessionResponse(
    val sessionId: String,
    /** Short, human-readable invite code (e.g. "ABC123"). */
    val sessionCode: String,
)

@Serializable
data class SessionInfoResponse(
    val sessionId: String,
    val sessionCode: String,
    val connectedUsers: Int,
)
