package com.kregosh.mtglifetracker.network

import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import com.kregosh.mtglifetracker.shared.SessionSettings
import kotlinx.coroutines.flow.Flow

/** The session (or code) doesn't exist, as opposed to a network or sign-in failure. */
class SessionNotFoundException(message: String) : Exception(message)

interface SessionApi {
    /**
     * Signs in (or reuses the current sign-in) and returns the player ID the backend
     * knows this device by. Friends, seats and presence are all keyed by it.
     */
    suspend fun signIn(): String

    suspend fun createSession(hostUserId: String, settings: SessionSettings): CreateSessionResponse
    suspend fun getSessionByCode(code: String): SessionInfoResponse
    suspend fun getSessionById(sessionId: String): SessionInfoResponse
    fun observeFriendPresence(friendUserIds: List<String>): Flow<Map<String, String?>>
    fun close()
}
