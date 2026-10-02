package com.kregosh.mtglifetracker.data

interface UserPrefs {
    var displayName : String
    var backgroundImageUri      : String?
    var cardBackgroundImageUri  : String?
    var startLife               : UInt
    var commanderDeathThreshold : UInt
    var infectDeathThreshold    : UInt
    var colorScheme             : AppColorScheme
    var commanderDefaultEnabled : Boolean
    var timerVisible            : Boolean
    var timerCountDown          : Boolean
    var timerLimitMinutes       : UInt
    var lastSessionId           : String?

    // ── Known players (auto-populated, LRU, max 10) ───────────────────────
    // Ordered newest-first by last-seen time.
    val knownPlayers: List<KnownPlayer>
    fun touchKnownPlayer(userId: String, displayName: String)
    fun forgetKnownPlayer(userId: String)

    // ── Friends (explicit, device-local, no size limit) ───────────────────
    val friendList: List<Friend>
    fun addFriend(userId: String, displayName: String)
    fun removeFriend(userId: String)
}

data class KnownPlayer(val userId: String, val displayName: String, val lastSeen: Long)

data class Friend(val userId: String, val displayName: String)
