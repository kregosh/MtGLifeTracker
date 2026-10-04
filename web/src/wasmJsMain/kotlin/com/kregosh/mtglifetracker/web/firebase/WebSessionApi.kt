package com.kregosh.mtglifetracker.web.firebase

import com.kregosh.mtglifetracker.domain.CodeRegistry
import com.kregosh.mtglifetracker.domain.SessionCodes
import com.kregosh.mtglifetracker.network.SessionApi
import com.kregosh.mtglifetracker.network.SessionNotFoundException
import com.kregosh.mtglifetracker.network.schema.SessionSchema
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import com.kregosh.mtglifetracker.shared.SessionSettings
import com.kregosh.mtglifetracker.web.firebase.js.*
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout

private const val TIMEOUT_MS = 10_000L

/** The browser's [SessionApi]: the same anonymous auth and database layout as the Android app. */
class WebSessionApi : SessionApi {

    private val db   get() = WebFirebase.db
    private val auth get() = WebFirebase.auth

    // Firebase Auth keeps the anonymous user in the browser's IndexedDB, so the player ID
    // survives reloads and restarts just like on Android.
    override suspend fun signIn(): String {
        auth.authStateReady().await<JsAny?>()
        auth.currentUser?.let { return it.uid }
        return withTimeout(TIMEOUT_MS) { signInAnonymously(auth).await<UserCredential>().user.uid }
    }

    override suspend fun createSession(hostUserId: String, settings: SessionSettings): CreateSessionResponse {
        signIn()
        val sessionId = randomId()
        return withTimeout(TIMEOUT_MS) {
            val code = SessionCodes.allocate(registry, sessionId, now = nowMillis().toLong())
                ?: throw Exception("Could not find a free session code")
            val document = SessionSchema.newSession(code, hostUserId, settings).toJs()!!
            setProperty(document, "createdAt", serverTimestamp())
            set(ref(db, SessionSchema.sessionPath(sessionId)), document).await<JsAny?>()
            CreateSessionResponse(sessionId = sessionId, sessionCode = code)
        }
    }

    private val registry = object : CodeRegistry {
        override suspend fun sessionFor(code: String): String? = read(SessionSchema.codePath(code)) as? String

        override suspend fun sessionCreatedAt(sessionId: String): Long? {
            val parsed = SessionSchema.parseSession(read(SessionSchema.sessionPath(sessionId)), 0u) ?: return null
            // A session without a creation time is never treated as abandoned.
            return parsed.createdAt ?: Long.MAX_VALUE
        }

        override suspend fun deleteSession(sessionId: String) =
            succeeds { remove(ref(db, SessionSchema.sessionPath(sessionId))).await<JsAny?>() }

        override suspend fun releaseCode(code: String) =
            succeeds { remove(ref(db, SessionSchema.codePath(code))).await<JsAny?>() }

        override suspend fun claimCode(code: String, sessionId: String) =
            succeeds { set(ref(db, SessionSchema.codePath(code)), sessionId.toJs()).await<JsAny?>() }
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
            val presence = friendUserIds.associateWithTo(mutableMapOf()) { null as String? }
            val stops = friendUserIds.map { uid ->
                onValue(ref(db, SessionSchema.presencePath(uid)), { snapshot ->
                    presence[uid] = snapshot.`val`().toKotlin() as? String
                    trySend(presence.toMap())
                }, {})
            }
            awaitClose { stops.forEach(::callFunction) }
        }
    }

    override fun close() {}

    private suspend fun read(path: String): Any? = get(ref(db, path)).await<DataSnapshot>().`val`().toKotlin()

    private suspend fun succeeds(block: suspend () -> Unit): Boolean =
        runCatching { block() }.isSuccess
}
