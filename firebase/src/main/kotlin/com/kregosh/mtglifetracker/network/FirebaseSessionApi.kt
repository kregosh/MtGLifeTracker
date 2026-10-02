package com.kregosh.mtglifetracker.network

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.kregosh.mtglifetracker.domain.CodeRegistry
import com.kregosh.mtglifetracker.domain.SessionCodes
import com.kregosh.mtglifetracker.network.schema.SessionSchema
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import com.kregosh.mtglifetracker.shared.SessionSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TIMEOUT_MS = 10_000L

/** Firebase adapter for [SessionApi]: anonymous auth plus the Realtime Database. */
class FirebaseSessionApi : SessionApi {

    private val db   get() = FirebaseDatabase.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    // The anonymous auth uid is the player ID, so the rules can check ownership
    // directly. It persists across restarts until the app's data is cleared.
    override suspend fun signIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = withTimeout(TIMEOUT_MS) { auth.signInAnonymously().await() }
        return result.user?.uid ?: throw Exception("Anonymous sign-in returned no user")
    }

    override suspend fun createSession(hostUserId: String, settings: SessionSettings): CreateSessionResponse {
        signIn()
        val sessionId = UUID.randomUUID().toString()
        return withTimeout(TIMEOUT_MS) {
            val code = SessionCodes.allocate(registry, sessionId, now = System.currentTimeMillis())
                ?: throw Exception("Could not find a free session code")
            val document = SessionSchema.newSession(code, hostUserId, settings) + ("createdAt" to ServerValue.TIMESTAMP)
            db.getReference(SessionSchema.sessionPath(sessionId)).setValue(document).await()
            CreateSessionResponse(sessionId = sessionId, sessionCode = code)
        }
    }

    private val registry = object : CodeRegistry {
        override suspend fun sessionFor(code: String): String? =
            db.getReference(SessionSchema.codePath(code)).get().await().getValue(String::class.java)

        override suspend fun sessionCreatedAt(sessionId: String): Long? {
            val parsed = SessionSchema.parseSession(read(SessionSchema.sessionPath(sessionId)), 0u) ?: return null
            // A session without a creation time is never treated as abandoned.
            return parsed.createdAt ?: Long.MAX_VALUE
        }

        override suspend fun deleteSession(sessionId: String) =
            succeeds { db.getReference(SessionSchema.sessionPath(sessionId)).removeValue().await() }

        override suspend fun releaseCode(code: String) =
            succeeds { db.getReference(SessionSchema.codePath(code)).removeValue().await() }

        override suspend fun claimCode(code: String, sessionId: String) =
            succeeds { db.getReference(SessionSchema.codePath(code)).setValue(sessionId).await() }
    }

    override suspend fun getSessionByCode(code: String): SessionInfoResponse {
        signIn()
        val upper = code.uppercase()
        return withTimeout(TIMEOUT_MS) {
            val sessionId = registry.sessionFor(upper)
                ?: throw SessionNotFoundException("Session '$upper' not found")
            SessionSchema.parseInfo(sessionId, read(SessionSchema.sessionPath(sessionId)))
                ?: throw SessionNotFoundException("Session '$upper' not found")
        }
    }

    override suspend fun getSessionById(sessionId: String): SessionInfoResponse {
        signIn()
        return withTimeout(TIMEOUT_MS) {
            SessionSchema.parseInfo(sessionId, read(SessionSchema.sessionPath(sessionId)))
                ?: throw SessionNotFoundException("Session no longer exists")
        }
    }

    override fun observeFriendPresence(friendUserIds: List<String>): Flow<Map<String, String?>> {
        if (friendUserIds.isEmpty()) return flowOf(emptyMap())
        return callbackFlow {
            signIn()
            val presence  = friendUserIds.associateWithTo(mutableMapOf()) { null as String? }
            val listeners = friendUserIds.associateWith { uid ->
                db.getReference(SessionSchema.presencePath(uid)).addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        presence[uid] = snapshot.getValue(String::class.java)
                        trySend(presence.toMap())
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
            awaitClose {
                listeners.forEach { (uid, listener) ->
                    db.getReference(SessionSchema.presencePath(uid)).removeEventListener(listener)
                }
            }
        }
    }

    override fun close() {}

    private suspend fun read(path: String): Any? = db.getReference(path).get().await().value

    private suspend fun succeeds(block: suspend () -> Unit): Boolean =
        runCatching { block() }.isSuccess
}
