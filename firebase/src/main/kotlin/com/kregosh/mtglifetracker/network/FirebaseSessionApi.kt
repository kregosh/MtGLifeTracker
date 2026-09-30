package com.kregosh.mtglifetracker.network

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "FirebaseSessionApi"
private const val TIMEOUT_MS = 10_000L

private val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

class FirebaseSessionApi : SessionApi {

    private val db get() = FirebaseDatabase.getInstance(
        "https://mtg-lifetracker-7866f-default-rtdb.europe-west1.firebasedatabase.app"
    )

    override suspend fun createSession(): CreateSessionResponse {
        val sessionId = UUID.randomUUID().toString()
        val code      = (1..6).map { CODE_CHARS.random() }.joinToString("")
        Log.d(TAG, "createSession: id=$sessionId code=$code")

        withTimeout(TIMEOUT_MS) {
            Log.d(TAG, "createSession: writing sessions/$sessionId …")
            db.getReference("sessions/$sessionId").setValue(
                mapOf("code" to code, "createdAt" to ServerValue.TIMESTAMP)
            ).await()
            Log.d(TAG, "createSession: writing sessionCodes/$code …")
            db.getReference("sessionCodes/$code").setValue(sessionId).await()
        }

        Log.d(TAG, "createSession: done")
        return CreateSessionResponse(sessionId = sessionId, sessionCode = code)
    }

    override suspend fun getSessionByCode(code: String): SessionInfoResponse {
        val upper = code.uppercase()
        Log.d(TAG, "getSessionByCode: code=$upper")

        return withTimeout(TIMEOUT_MS) {
            val snapshot  = db.getReference("sessionCodes/$upper").get().await()
            val sessionId = snapshot.getValue(String::class.java)
                ?: throw Exception("Session '$upper' not found")
            Log.d(TAG, "getSessionByCode: resolved sessionId=$sessionId")

            val sessionSnap    = db.getReference("sessions/$sessionId").get().await()
            val connectedUsers = sessionSnap.child("users").childrenCount.toInt()
            SessionInfoResponse(sessionId = sessionId, sessionCode = upper, connectedUsers = connectedUsers)
        }
    }

    override suspend fun getSessionById(sessionId: String): SessionInfoResponse {
        Log.d(TAG, "getSessionById: id=$sessionId")
        return withTimeout(TIMEOUT_MS) {
            val snapshot       = db.getReference("sessions/$sessionId").get().await()
            val code           = snapshot.child("code").getValue(String::class.java) ?: ""
            val connectedUsers = snapshot.child("users").childrenCount.toInt()
            SessionInfoResponse(sessionId = sessionId, sessionCode = code, connectedUsers = connectedUsers)
        }
    }

    override fun close() {}
}
