package com.kregosh.mtglifetracker.web

import com.kregosh.mtglifetracker.data.AppColorScheme
import com.kregosh.mtglifetracker.data.Friend
import com.kregosh.mtglifetracker.data.KnownPlayer
import com.kregosh.mtglifetracker.data.UserPrefs
import com.kregosh.mtglifetracker.web.firebase.js.nowMillis
import kotlinx.serialization.json.*

private fun storageGet(key: String): String? = js("{ try { return localStorage.getItem(key); } catch (e) { return null; } }")
private fun storageSet(key: String, value: String?): Unit =
    js("{ try { if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, value); } catch (e) {} }")

private const val MAX_KNOWN_PLAYERS = 10

private data class StoredPlayer(val userId: String, val displayName: String, val lastSeen: Long = 0)

/** [UserPrefs] kept in the browser's localStorage, with the Android app's defaults. */
class BrowserPrefs : UserPrefs {

    private fun string(key: String) = storageGet("mtg.$key")
    private fun store(key: String, value: Any?) = storageSet("mtg.$key", value?.toString())
    private fun uint(key: String, default: UInt) = string(key)?.toUIntOrNull() ?: default
    private fun bool(key: String, default: Boolean) = string(key)?.toBooleanStrictOrNull() ?: default

    override var displayName: String
        get() = string("displayName") ?: "Player"
        set(v) = store("displayName", v)
    // Backgrounds aren't part of the web version yet.
    override var backgroundImageUri: String? = null
    override var cardBackgroundImageUri: String? = null
    override var startLife: UInt
        get() = uint("startLife", 20u)
        set(v) = store("startLife", v)
    override var commanderDeathThreshold: UInt
        get() = uint("commanderDeathThreshold", 21u)
        set(v) = store("commanderDeathThreshold", v)
    override var infectDeathThreshold: UInt
        get() = uint("infectDeathThreshold", 10u)
        set(v) = store("infectDeathThreshold", v)
    override var colorScheme: AppColorScheme
        get() = string("colorScheme")?.let { runCatching { AppColorScheme.valueOf(it) }.getOrNull() } ?: AppColorScheme.SYSTEM
        set(v) = store("colorScheme", v.name)
    override var commanderDefaultEnabled: Boolean
        get() = bool("commanderDefaultEnabled", false)
        set(v) = store("commanderDefaultEnabled", v)
    override var timerVisible: Boolean
        get() = bool("timerVisible", true)
        set(v) = store("timerVisible", v)
    override var timerCountDown: Boolean
        get() = bool("timerCountDown", false)
        set(v) = store("timerCountDown", v)
    override var timerLimitMinutes: UInt
        get() = uint("timerLimitMinutes", 60u)
        set(v) = store("timerLimitMinutes", v)
    override var lastSessionId: String?
        get() = string("lastSessionId")
        set(v) = store("lastSessionId", v)
    override var lastSessionObserving: Boolean
        get() = bool("lastSessionObserving", false)
        set(v) = store("lastSessionObserving", v)

    private fun players(key: String): List<StoredPlayer> = runCatching {
        Json.parseToJsonElement(string(key) ?: "[]").jsonArray.map {
            val o = it.jsonObject
            StoredPlayer(o.getValue("userId").jsonPrimitive.content, o.getValue("displayName").jsonPrimitive.content,
                         o["lastSeen"]?.jsonPrimitive?.longOrNull ?: 0)
        }
    }.getOrDefault(emptyList())

    private fun savePlayers(key: String, players: List<StoredPlayer>) = store(key, JsonArray(players.map {
        buildJsonObject { put("userId", it.userId); put("displayName", it.displayName); put("lastSeen", it.lastSeen) }
    }).toString())

    override val knownPlayers: List<KnownPlayer>
        get() = players("knownPlayers").map { KnownPlayer(it.userId, it.displayName, it.lastSeen) }

    override fun touchKnownPlayer(userId: String, displayName: String) {
        val others = players("knownPlayers").filter { it.userId != userId }
        savePlayers("knownPlayers", (listOf(StoredPlayer(userId, displayName, nowMillis().toLong())) + others).take(MAX_KNOWN_PLAYERS))
    }

    override fun forgetKnownPlayer(userId: String) =
        savePlayers("knownPlayers", players("knownPlayers").filter { it.userId != userId })

    override val friendList: List<Friend>
        get() = players("friends").map { Friend(it.userId, it.displayName) }

    override fun addFriend(userId: String, displayName: String) =
        savePlayers("friends", players("friends").filter { it.userId != userId } + StoredPlayer(userId, displayName))

    override fun removeFriend(userId: String) =
        savePlayers("friends", players("friends").filter { it.userId != userId })
}
