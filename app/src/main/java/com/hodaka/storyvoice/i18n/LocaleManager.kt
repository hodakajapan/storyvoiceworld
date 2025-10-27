package com.hodaka.storyvoice.i18n


import android.content.Context
import android.content.res.Configuration
import java.util.Locale


object LocaleManager {
    fun updateLocale(context: Context, lang: String): Context {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}