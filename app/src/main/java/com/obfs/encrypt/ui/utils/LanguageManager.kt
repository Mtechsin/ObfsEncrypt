package com.obfs.encrypt.ui.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LanguageManager {
    
    fun setLocale(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            "en" -> Locale.ENGLISH
            "ar" -> Locale("ar")
            else -> Locale.getDefault()
        }
        
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        
        return context.createConfigurationContext(config)
    }
    
    fun getLocaleFromCode(languageCode: String): Locale {
        return when (languageCode) {
            "en" -> Locale.ENGLISH
            "ar" -> Locale("ar")
            else -> Locale.getDefault()
        }
    }
    
    fun updateResources(context: Context, languageCode: String) {
        val locale = getLocaleFromCode(languageCode)
        Locale.setDefault(locale)
        
        val resources = context.resources
        val config = Configuration(resources.configuration)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
    
    fun applyLocale(activity: Activity, languageCode: String) {
        val locale = getLocaleFromCode(languageCode)
        Locale.setDefault(locale)
        
        val config = activity.resources.configuration
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
    }
    
    fun getCurrentLanguageCode(context: Context): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        
        return when (locale.language) {
            "en" -> "en"
            "ar" -> "ar"
            else -> "system"
        }
    }
}
