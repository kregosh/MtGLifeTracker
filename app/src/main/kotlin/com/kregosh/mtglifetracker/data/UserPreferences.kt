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
                if (value != null) putString(KEY_BACKGROUND_IMAGE, value)
                else remove(KEY_BACKGROUND_IMAGE)
            }.apply()
        }

    companion object {
        private const val KEY_USER_ID         = "user_id"
        private const val KEY_DISPLAY_NAME    = "display_name"
        private const val KEY_BACKGROUND_IMAGE = "background_image_uri"
    }
}
