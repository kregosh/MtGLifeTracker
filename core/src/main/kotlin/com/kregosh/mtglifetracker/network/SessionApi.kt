package com.kregosh.mtglifetracker.network

import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse

interface SessionApi {
    suspend fun createSession(): CreateSessionResponse
    suspend fun getSessionByCode(code: String): SessionInfoResponse
    suspend fun getSessionById(sessionId: String): SessionInfoResponse
    fun close()
}
