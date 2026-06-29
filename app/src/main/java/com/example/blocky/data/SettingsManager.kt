package com.example.blocky.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "blocky_settings",
        Context.MODE_PRIVATE,
    )

    var isBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCKING_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_BLOCKING_ENABLED, value) }
        }

    companion object {
        private const val KEY_BLOCKING_ENABLED = "blocking_enabled"
    }
}
