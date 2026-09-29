package com.kregosh.mtglifetracker.data

import android.content.Context
import java.util.UUID

class UserPreferences(context: Context) : UserPrefs {

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
    }
}
