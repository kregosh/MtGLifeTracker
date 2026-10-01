package com.kregosh.mtglifetracker.network

import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import com.kregosh.mtglifetracker.shared.SessionSettings
import kotlinx.coroutines.flow.Flow

interface SessionApi {
    suspend fun createSession(hostUserId: String, settings: SessionSettings): CreateSessionResponse
    suspend fun getSessionByCode(code: String): SessionInfoResponse
    suspend fun getSessionById(sessionId: String): SessionInfoResponse
    fun observeFriendPresence(friendUserIds: List<String>): Flow<Map<String, String?>>
    fun close()
}
