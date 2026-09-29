package com.kregosh.mtglifetracker.data

import android.content.Context
import java.util.UUID

/**
 * Lightweight wrapper over SharedPreferences.
 *
 * Stores the stable [userId] UUID (generated once per install) and the user's
 * chosen [displayName] so they survive app restarts.
 */
class UserPreferences(context: Context) : UserPrefs {

    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    /** Stable UUID identifying this device/install. Generated once, never changed. */
    val userId: String
        get() = prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString().also { id ->
            prefs.edit().putString(KEY_USER_ID, id).apply()
        }

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        set(value) { prefs.edit().putString(KEY_DISPLAY_NAME, value).apply() }

    companion object {
        private const val KEY_USER_ID      = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
