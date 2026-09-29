package com.kregosh.mtglifetracker.backend.session

import com.kregosh.mtglifetracker.shared.ServerMessage
import com.kregosh.mtglifetracker.shared.UserState
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val log = LoggerFactory.getLogger("SessionManager")

// ─────────────────────────────────────────────────────────────────────────────
// Connected user handle
// ─────────────────────────────────────────────────────────────────────────────

data class ConnectedUser(
    val userId: String,
    var displayName: String,
    val socket: DefaultWebSocketSession,
    var value: UInt = 0u,
)

// ─────────────────────────────────────────────────────────────────────────────
// Session room
// ─────────────────────────────────────────────────────────────────────────────

class SessionRoom(val sessionId: String, val code: String) {

    private val mutex = Mutex()
    private val users = ConcurrentHashMap<String, ConnectedUser>()

    suspend fun addUser(user: ConnectedUser) = mutex.withLock {
        users[user.userId] = user
        log.debug("Session $code: {} joined ({} total)", user.displayName, users.size)
    }

    suspend fun removeUser(userId: String) = mutex.withLock {
        users.remove(userId)
        log.debug("Session $code: user {} left ({} total)", userId, users.size)
    }

    suspend fun increment(userId: String) = mutex.withLock {
        val u = users[userId] ?: return@withLock
        if (u.value < UInt.MAX_VALUE) u.value++
    }

    suspend fun decrement(userId: String) = mutex.withLock {
        val u = users[userId] ?: return@withLock
        if (u.value > 0u) u.value--
    }

    suspend fun broadcastState() {
        val snapshot = users.values.map { UserState(it.userId, it.displayName, it.value) }
        val msg = ServerMessage.State(snapshot)
        val json = sharedJson.encodeToString(ServerMessage.serializer(), msg)
        users.values.forEach { u ->
            try { u.socket.send(Frame.Text(json)) }
            catch (e: Exception) { /* closed mid-send — will be cleaned up by the handler */ }
        }
    }

    fun isEmpty() = users.isEmpty()
    fun userCount() = users.size
    fun currentState() = users.values.map { UserState(it.userId, it.displayName, it.value) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Singleton manager
// ─────────────────────────────────────────────────────────────────────────────

class SessionManager {

    private val byId   = ConcurrentHashMap<String, SessionRoom>()
    private val byCode = ConcurrentHashMap<String, SessionRoom>() // code → room

    fun create(): SessionRoom {
        val id   = java.util.UUID.randomUUID().toString()
        val code = generateCode()
        val room = SessionRoom(id, code)
        byId[id]     = room
        byCode[code] = room
        log.info("Created session {} (code={})", id.take(8), code)
        return room
    }

    fun getById(sessionId: String)   = byId[sessionId]
    fun getByCode(code: String)      = byCode[code.uppercase()]

    /** Remove empty sessions to free memory. */
    fun pruneIfEmpty(room: SessionRoom) {
        if (room.isEmpty()) {
            byId.remove(room.sessionId)
            byCode.remove(room.code)
            log.info("Pruned empty session {}", room.sessionId.take(8))
        }
    }

    // 6-char uppercase alphanumeric codes, excluding visually ambiguous chars
    private val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private fun generateCode(): String {
        var code: String
        do { code = (1..6).map { codeChars[Random.nextInt(codeChars.length)] }.joinToString("") }
        while (byCode.containsKey(code))
        return code
    }
}
