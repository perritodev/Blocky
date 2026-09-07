package com.omargarcia.blocky.utils

import android.content.Context
import android.content.res.Configuration
import com.omargarcia.blocky.data.SettingsManager
import java.util.Locale

object LocaleHelper {
    /**
     * Creates and returns a localized [Context] based on the current language saved in [SettingsManager].
     */
    fun getLocalizedContext(context: Context): Context {
        val settings = SettingsManager(context)
        return getLocalizedContext(context, settings.languageCode)
    }

    /**
     * Creates and returns a localized [Context] for the specified [languageCode] (e.g., "es", "en").
     */
    fun getLocalizedContext(context: Context, languageCode: String): Context {
        val locale = Locale.forLanguageTag(languageCode)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(config)
    }
}
