package com.kregosh.mtglifetracker.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import java.util.UUID
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val TAG = "FirebaseSessionApi"
private const val TIMEOUT_MS = 10_000L

private val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

class FirebaseSessionApi : SessionApi {

    private val db   get() = FirebaseDatabase.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    // Ensure an anonymous Firebase Auth session exists before any DB write.
    // If already signed in this is a no-op; it uses the existing credential.
    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            withTimeout(TIMEOUT_MS) { auth.signInAnonymously().await() }
        }
    }

    override suspend fun createSession(): CreateSessionResponse {
        ensureSignedIn()
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
        ensureSignedIn()
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
        ensureSignedIn()
        Log.d(TAG, "getSessionById: id=$sessionId")
        return withTimeout(TIMEOUT_MS) {
            val snapshot       = db.getReference("sessions/$sessionId").get().await()
            val code           = snapshot.child("code").getValue(String::class.java) ?: ""
            val connectedUsers = snapshot.child("users").childrenCount.toInt()
            SessionInfoResponse(sessionId = sessionId, sessionCode = code, connectedUsers = connectedUsers)
        }
    }

    override fun observeFriendPresence(friendUserIds: List<String>): Flow<Map<String, String?>> {
        if (friendUserIds.isEmpty()) return flowOf(emptyMap())
        return callbackFlow {
            val presence  = friendUserIds.associateWithTo(mutableMapOf()) { null as String? }
            val listeners = mutableMapOf<String, ValueEventListener>()
            friendUserIds.forEach { uid ->
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        presence[uid] = snapshot.getValue(String::class.java)
                        trySend(presence.toMap())
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                listeners[uid] = listener
                db.getReference("presence/$uid").addValueEventListener(listener)
            }
            awaitClose {
                friendUserIds.forEach { uid ->
                    listeners[uid]?.let { db.getReference("presence/$uid").removeEventListener(it) }
                }
            }
        }
    }

    override fun close() {}
}
