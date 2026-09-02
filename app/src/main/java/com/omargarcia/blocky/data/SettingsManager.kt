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
        get() {
            val saved = prefs.getString(KEY_LANGUAGE_CODE, null)
            if (saved != null) return saved
            
            // First use: Detect system language. If Spanish -> "es", otherwise default to "en"
            val systemLang = java.util.Locale.getDefault().language.lowercase(java.util.Locale.ROOT)
            return if (systemLang.startsWith("es")) "es" else "en"
        }
        set(value) {
            prefs.edit { putString(KEY_LANGUAGE_CODE, value) }
        }

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, value) }
        }

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_SOUND_ENABLED, value) }
        }

    var repeatCallThreshold: Int
        get() = prefs.getInt(KEY_REPEAT_CALL_THRESHOLD, 1)
        set(value) {
            prefs.edit { putInt(KEY_REPEAT_CALL_THRESHOLD, value) }
        }

    var repeatCallIntervalMinutes: Int
        get() = prefs.getInt(KEY_REPEAT_CALL_INTERVAL_MINUTES, 15)
        set(value) {
            prefs.edit { putInt(KEY_REPEAT_CALL_INTERVAL_MINUTES, value) }
        }

    var isBlockSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_SOUND_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_BLOCK_SOUND_ENABLED, value) }
        }

    companion object {
        private const val KEY_BLOCKING_ENABLED = "blocking_enabled"
        private const val KEY_LANGUAGE_CODE = "language_code"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_BLOCK_SOUND_ENABLED = "block_sound_enabled"
        private const val KEY_REPEAT_CALL_THRESHOLD = "repeat_call_threshold"
        private const val KEY_REPEAT_CALL_INTERVAL_MINUTES = "repeat_call_interval_minutes"
    }
}
