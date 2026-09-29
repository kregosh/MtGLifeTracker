package com.kregosh.mtglifetracker.network

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import kotlinx.coroutines.tasks.await
import java.util.UUID

private val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

class FirebaseSessionApi : SessionApi {

    private val db get() = FirebaseDatabase.getInstance()

    override suspend fun createSession(): CreateSessionResponse {
        val sessionId = UUID.randomUUID().toString()
        val code      = (1..6).map { CODE_CHARS.random() }.joinToString("")

        db.getReference("sessions/$sessionId").setValue(
            mapOf("code" to code, "createdAt" to ServerValue.TIMESTAMP)
        ).await()

        db.getReference("sessionCodes/$code").setValue(sessionId).await()

        return CreateSessionResponse(sessionId = sessionId, sessionCode = code)
    }

    override suspend fun getSessionByCode(code: String): SessionInfoResponse {
        val upper    = code.uppercase()
        val snapshot = db.getReference("sessionCodes/$upper").get().await()
        val sessionId = snapshot.getValue(String::class.java)
            ?: throw Exception("Session '$upper' not found")

        val sessionSnap    = db.getReference("sessions/$sessionId").get().await()
        val connectedUsers = sessionSnap.child("users").childrenCount.toInt()
        return SessionInfoResponse(sessionId = sessionId, sessionCode = upper, connectedUsers = connectedUsers)
    }

    override suspend fun getSessionById(sessionId: String): SessionInfoResponse {
        val snapshot       = db.getReference("sessions/$sessionId").get().await()
        val code           = snapshot.child("code").getValue(String::class.java) ?: ""
        val connectedUsers = snapshot.child("users").childrenCount.toInt()
        return SessionInfoResponse(sessionId = sessionId, sessionCode = code, connectedUsers = connectedUsers)
    }

    override fun close() {}
}
