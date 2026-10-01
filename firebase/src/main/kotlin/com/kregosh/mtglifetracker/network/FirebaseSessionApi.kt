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
import com.kregosh.mtglifetracker.shared.SessionSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val TAG = "FirebaseSessionApi"
private const val TIMEOUT_MS = 10_000L

// 32^8 ≈ 10^12 codes: guessing a live one is impractical.
private const val CODE_LENGTH   = 8
private const val CODE_ATTEMPTS = 5
private const val ABANDONED_AFTER_MS = 24 * 60 * 60 * 1000L
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

    override suspend fun createSession(hostUserId: String, settings: SessionSettings): CreateSessionResponse {
        ensureSignedIn()
        val sessionId = UUID.randomUUID().toString()

        return withTimeout(TIMEOUT_MS) {
            // The rules check that the host ID belongs to this sign-in.
            db.getReference("userAuth/$hostUserId").setValue(auth.currentUser?.uid).await()
            repeat(CODE_ATTEMPTS) {
                val code = (1..CODE_LENGTH).map { CODE_CHARS.random() }.joinToString("")
                if (claimCode(code, sessionId)) {
                    db.getReference("sessions/$sessionId").setValue(
                        mapOf(
                            "code"       to code,
                            "createdAt"  to ServerValue.TIMESTAMP,
                            "hostUserId" to hostUserId,
                            "game"       to 0L,
                            "settings"   to settings.toMap(),
                        )
                    ).await()
                    Log.d(TAG, "createSession: created")
                    return@withTimeout CreateSessionResponse(sessionId = sessionId, sessionCode = code)
                }
            }
            throw Exception("Could not find a free session code")
        }
    }

    private suspend fun claimCode(code: String, sessionId: String): Boolean {
        val codeRef  = db.getReference("sessionCodes/$code")
        val existing = codeRef.get().await().getValue(String::class.java)
        if (existing != null && !releaseIfAbandoned(code, existing)) return false
        return runCatching { codeRef.setValue(sessionId).await() }.isSuccess
    }

    // Sessions that everyone abandoned without leaving are reclaimed lazily, when
    // a new session happens to draw their code.
    private suspend fun releaseIfAbandoned(code: String, sessionId: String): Boolean {
        val sessionRef = db.getReference("sessions/$sessionId")
        val snapshot   = sessionRef.get().await()
        if (snapshot.exists()) {
            val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: return false
            if (System.currentTimeMillis() - createdAt < ABANDONED_AFTER_MS) return false
            if (runCatching { sessionRef.removeValue().await() }.isFailure) return false
        }
        return runCatching { db.getReference("sessionCodes/$code").removeValue().await() }.isSuccess
    }

    override suspend fun getSessionByCode(code: String): SessionInfoResponse {
        ensureSignedIn()
        val upper = code.uppercase()

        return withTimeout(TIMEOUT_MS) {
            val snapshot  = db.getReference("sessionCodes/$upper").get().await()
            val sessionId = snapshot.getValue(String::class.java)
                ?: throw Exception("Session '$upper' not found")

            val sessionSnap = db.getReference("sessions/$sessionId").get().await()
            if (!sessionSnap.exists()) throw Exception("Session '$upper' not found")
            sessionSnap.toInfo(sessionId)
        }
    }

    override suspend fun getSessionById(sessionId: String): SessionInfoResponse {
        ensureSignedIn()
        return withTimeout(TIMEOUT_MS) {
            val snapshot       = db.getReference("sessions/$sessionId").get().await()
            if (!snapshot.exists()) throw Exception("Session no longer exists")
            snapshot.toInfo(sessionId)
        }
    }

    private fun DataSnapshot.toInfo(sessionId: String): SessionInfoResponse {
        val users = child("users")
        return SessionInfoResponse(
            sessionId      = sessionId,
            sessionCode    = child("code").getValue(String::class.java) ?: "",
            connectedUsers = users.childrenCount.toInt(),
            settings       = child("settings").toSessionSettings(),
            userIds        = users.children.mapNotNull { it.key }.toSet(),
        )
    }

    override fun observeFriendPresence(friendUserIds: List<String>): Flow<Map<String, String?>> {
        if (friendUserIds.isEmpty()) return flowOf(emptyMap())
        return callbackFlow {
            ensureSignedIn()
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
