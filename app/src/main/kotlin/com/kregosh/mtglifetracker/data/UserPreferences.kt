package com.kregosh.mtglifetracker.data

import android.content.Context
import java.util.UUID

private const val SEP = "\u001F"
private const val MAX_KNOWN = 10

class UserPreferences(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : UserPrefs {

    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    override val userId: String
        get() = prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString().also { id ->
            prefs.edit().putString(KEY_USER_ID, id).apply()
        }

    override var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DISPLAY_NAME, value).apply() }

    override var backgroundImageUri: String?
        get() = prefs.getString(KEY_BACKGROUND_IMAGE, null)
        set(value) {
            prefs.edit().apply {
                if (value != null) putString(KEY_BACKGROUND_IMAGE, value) else remove(KEY_BACKGROUND_IMAGE)
            }.apply()
        }

    override var cardBackgroundImageUri: String?
        get() = prefs.getString(KEY_CARD_BACKGROUND_IMAGE, null)
        set(value) {
            prefs.edit().apply {
                if (value != null) putString(KEY_CARD_BACKGROUND_IMAGE, value) else remove(KEY_CARD_BACKGROUND_IMAGE)
            }.apply()
        }

    override var startLife: UInt
        get() = prefs.getInt(KEY_START_LIFE, 20).toUInt()
        set(value) { prefs.edit().putInt(KEY_START_LIFE, value.toInt()).apply() }

    override var commanderDeathThreshold: UInt
        get() = prefs.getInt(KEY_COMMANDER_THRESHOLD, 21).toUInt()
        set(value) { prefs.edit().putInt(KEY_COMMANDER_THRESHOLD, value.toInt()).apply() }

    override var infectDeathThreshold: UInt
        get() = prefs.getInt(KEY_INFECT_THRESHOLD, 10).toUInt()
        set(value) { prefs.edit().putInt(KEY_INFECT_THRESHOLD, value.toInt()).apply() }

    override var colorScheme: String
        get() = prefs.getString(KEY_COLOR_SCHEME, "dark") ?: "dark"
        set(value) { prefs.edit().putString(KEY_COLOR_SCHEME, value).apply() }

    override var commanderDefaultEnabled: Boolean
        get() = prefs.getBoolean(KEY_COMMANDER_DEFAULT, false)
        set(value) { prefs.edit().putBoolean(KEY_COMMANDER_DEFAULT, value).apply() }

    override var timerVisible: Boolean
        get() = prefs.getBoolean(KEY_TIMER_VISIBLE, true)
        set(value) { prefs.edit().putBoolean(KEY_TIMER_VISIBLE, value).apply() }

    override var timerCountDown: Boolean
        get() = prefs.getBoolean(KEY_TIMER_COUNTDOWN, false)
        set(value) { prefs.edit().putBoolean(KEY_TIMER_COUNTDOWN, value).apply() }

    override var timerLimitMinutes: UInt
        get() = prefs.getInt(KEY_TIMER_LIMIT_MINUTES, 60).toUInt()
        set(value) { prefs.edit().putInt(KEY_TIMER_LIMIT_MINUTES, value.toInt()).apply() }

    // ── Known players ─────────────────────────────────────────────────────
    // Stored as StringSet; each entry: "$userId$SEP$displayName$SEP$lastSeenMillis"

    override val knownPlayers: List<KnownPlayer>
        get() = parseKnownSet(prefs.getStringSet(KEY_KNOWN_PLAYERS, emptySet()))
            .sortedByDescending { it.lastSeen }

    override fun touchKnownPlayer(userId: String, displayName: String) {
        val now     = clock()
        val current = parseKnownSet(prefs.getStringSet(KEY_KNOWN_PLAYERS, emptySet()))
            .filter { it.userId != userId }         // remove stale entry for this user
        val updated = (current + KnownPlayer(userId, displayName, now))
            .sortedByDescending { it.lastSeen }     // newest first
            .take(MAX_KNOWN)                        // cap at 10
        prefs.edit().putStringSet(KEY_KNOWN_PLAYERS, updated.toEncodedSet()).apply()
    }

    override fun forgetKnownPlayer(userId: String) {
        val updated = parseKnownSet(prefs.getStringSet(KEY_KNOWN_PLAYERS, emptySet()))
            .filter { it.userId != userId }
        prefs.edit().putStringSet(KEY_KNOWN_PLAYERS, updated.toEncodedSet()).apply()
    }

    // ── Friends ───────────────────────────────────────────────────────────
    // Stored as StringSet; each entry: "$userId$SEP$displayName"

    override val friendList: List<Friend>
        get() = parseFriendSet(prefs.getStringSet(KEY_FRIEND_LIST, emptySet()))
            .sortedBy { it.displayName }

    override fun addFriend(userId: String, displayName: String) {
        val current = parseFriendSet(prefs.getStringSet(KEY_FRIEND_LIST, emptySet()))
            .filter { it.userId != userId }         // idempotent: replace if already present
        val updated = current + Friend(userId, displayName)
        prefs.edit().putStringSet(KEY_FRIEND_LIST, updated.map { "${it.userId}$SEP${it.displayName}" }.toSet()).apply()
    }

    override fun removeFriend(userId: String) {
        val updated = parseFriendSet(prefs.getStringSet(KEY_FRIEND_LIST, emptySet()))
            .filter { it.userId != userId }
        prefs.edit().putStringSet(KEY_FRIEND_LIST, updated.map { "${it.userId}$SEP${it.displayName}" }.toSet()).apply()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun parseKnownSet(set: Set<String>?): List<KnownPlayer> =
        set.orEmpty().mapNotNull { entry ->
            val first = entry.indexOf(SEP)
            val last  = entry.lastIndexOf(SEP)
            if (first < 0 || last == first) null
            else KnownPlayer(
                entry.substring(0, first),
                entry.substring(first + 1, last),
                entry.substring(last + 1).toLongOrNull() ?: 0L
            )
        }

    private fun List<KnownPlayer>.toEncodedSet(): Set<String> =
        map { "${it.userId}$SEP${it.displayName}$SEP${it.lastSeen}" }.toSet()

    private fun parseFriendSet(set: Set<String>?): List<Friend> =
        set.orEmpty().mapNotNull { entry ->
            val idx = entry.indexOf(SEP)
            if (idx < 0) null else Friend(entry.substring(0, idx), entry.substring(idx + 1))
        }

    companion object {
        private const val KEY_USER_ID               = "user_id"
        private const val KEY_DISPLAY_NAME          = "display_name"
        private const val KEY_BACKGROUND_IMAGE      = "background_image_uri"
        private const val KEY_CARD_BACKGROUND_IMAGE = "card_background_image_uri"
        private const val KEY_START_LIFE            = "start_life"
        private const val KEY_COMMANDER_THRESHOLD   = "commander_death_threshold"
        private const val KEY_INFECT_THRESHOLD      = "infect_death_threshold"
        private const val KEY_COLOR_SCHEME          = "color_scheme"
        private const val KEY_COMMANDER_DEFAULT     = "commander_default_enabled"
        private const val KEY_TIMER_VISIBLE         = "timer_visible"
        private const val KEY_TIMER_COUNTDOWN       = "timer_countdown"
        private const val KEY_TIMER_LIMIT_MINUTES   = "timer_limit_minutes"
        private const val KEY_KNOWN_PLAYERS         = "known_players"
        private const val KEY_FRIEND_LIST           = "friend_list"
    }
}
