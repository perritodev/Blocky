package com.omargarcia.blocky.data

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

    var languageCode: String
        get() = prefs.getString(KEY_LANGUAGE_CODE, "en") ?: "en"
        set(value) {
            prefs.edit { putString(KEY_LANGUAGE_CODE, value) }
        }

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, value) }
        }

    companion object {
        private const val KEY_BLOCKING_ENABLED = "blocking_enabled"
        private const val KEY_LANGUAGE_CODE = "language_code"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
