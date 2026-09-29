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
    var life: UInt = 20u,
    var commanderDamage: UInt = 0u,
    var poisonDamage: UInt = 0u,
    val customStats: MutableMap<String, UInt> = mutableMapOf(),
)

// ─────────────────────────────────────────────────────────────────────────────
// Session room
// ─────────────────────────────────────────────────────────────────────────────

class SessionRoom(val sessionId: String, val code: String) {

    private val mutex = Mutex()
    private val users = ConcurrentHashMap<String, ConnectedUser>()
    private val customStatNames = mutableListOf<String>()

    suspend fun addUser(user: ConnectedUser) = mutex.withLock {
        // seed any existing custom stats at 0 for the new user
        customStatNames.forEach { stat -> user.customStats.putIfAbsent(stat, 0u) }
        users[user.userId] = user
        log.debug("Session $code: {} joined ({} total)", user.displayName, users.size)
    }

    suspend fun removeUser(userId: String) = mutex.withLock {
        users.remove(userId)
        log.debug("Session $code: user {} left ({} total)", userId, users.size)
    }

    /**
     * Adjust [stat] for [userId] by [delta]. Clamped to [0, UInt.MAX_VALUE].
     * Unknown stat names for custom stats are silently ignored.
     */
    suspend fun adjust(userId: String, stat: String, delta: Int) = mutex.withLock {
        val u = users[userId] ?: return@withLock
        when (stat) {
            "life"      -> u.life            = clampAdd(u.life, delta)
            "commander" -> u.commanderDamage = clampAdd(u.commanderDamage, delta)
            "poison"    -> u.poisonDamage    = clampAdd(u.poisonDamage, delta)
            else        -> {
                if (customStatNames.contains(stat)) {
                    u.customStats[stat] = clampAdd(u.customStats.getOrDefault(stat, 0u), delta)
                }
            }
        }
    }

    /**
     * Add a new custom stat column to this session. Returns true if newly added,
     * false if it already existed.
     */
    suspend fun addCustomStat(name: String): Boolean = mutex.withLock {
        if (customStatNames.contains(name)) return@withLock false
        customStatNames.add(name)
        users.values.forEach { u -> u.customStats.putIfAbsent(name, 0u) }
        log.debug("Session $code: added custom stat '{}'", name)
        true
    }

    suspend fun broadcastState() {
        val statNames = customStatNames.toList()
        val snapshot = users.values.map { u ->
            UserState(
                id             = u.userId,
                displayName    = u.displayName,
                life           = u.life,
                commanderDamage = u.commanderDamage,
                poisonDamage   = u.poisonDamage,
                customStats    = statNames.associateWith { u.customStats.getOrDefault(it, 0u) },
            )
        }
        val msg  = ServerMessage.State(snapshot, statNames)
        val json = sharedJson.encodeToString(ServerMessage.serializer(), msg)
        users.values.forEach { u ->
            try { u.socket.send(Frame.Text(json)) }
            catch (e: Exception) { /* closed mid-send — cleaned up by the handler */ }
        }
    }

    fun isEmpty()    = users.isEmpty()
    fun userCount()  = users.size

    fun currentState(): List<UserState> {
        val statNames = customStatNames.toList()
        return users.values.map { u ->
            UserState(
                id             = u.userId,
                displayName    = u.displayName,
                life           = u.life,
                commanderDamage = u.commanderDamage,
                poisonDamage   = u.poisonDamage,
                customStats    = statNames.associateWith { u.customStats.getOrDefault(it, 0u) },
            )
        }
    }

    // ── helpers ───────────────────────────────────────────────────────

    private fun clampAdd(value: UInt, delta: Int): UInt = when {
        delta >= 0 -> {
            val d = delta.toUInt()
            if (value > UInt.MAX_VALUE - d) UInt.MAX_VALUE else value + d
        }
        else -> {
            val d = (-delta).toUInt()
            if (value < d) 0u else value - d
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Manager
// ─────────────────────────────────────────────────────────────────────────────

class SessionManager {

    private val byId   = ConcurrentHashMap<String, SessionRoom>()
    private val byCode = ConcurrentHashMap<String, SessionRoom>()

    fun create(): SessionRoom {
        val id   = java.util.UUID.randomUUID().toString()
        val code = generateCode()
        val room = SessionRoom(id, code)
        byId[id]     = room
        byCode[code] = room
        log.info("Created session {} (code={})", id.take(8), code)
        return room
    }

    fun getById(sessionId: String)  = byId[sessionId]
    fun getByCode(code: String)     = byCode[code.uppercase()]

    fun pruneIfEmpty(room: SessionRoom) {
        if (room.isEmpty()) {
            byId.remove(room.sessionId)
            byCode.remove(room.code)
            log.info("Pruned empty session {}", room.sessionId.take(8))
        }
    }

    private val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private fun generateCode(): String {
        var code: String
        do { code = (1..6).map { codeChars[Random.nextInt(codeChars.length)] }.joinToString("") }
        while (byCode.containsKey(code))
        return code
    }
}
