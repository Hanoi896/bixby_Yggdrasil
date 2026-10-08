package com.huginmunin.app.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    
    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
        
        // Apply locale immediately
        applyLocale(context, languageCode)
    }
    
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "") ?: ""
    }
    
    fun isOnboardingDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    }
    
    fun markOnboardingDone(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }
    
    fun getOnboardingStep(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("onboarding_step", 0)
    }
    
    fun setOnboardingStep(context: Context, step: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("onboarding_step", step).apply()
    }
    
    /**
     * Apply the saved locale to the context
     */
    fun applyLocale(context: Context, languageCode: String? = null): Context {
        val code = languageCode ?: getLanguage(context)
        if (code.isEmpty()) return context
        
        val locale = Locale(code)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
    
    /**
     * Wrap context with correct locale for use in attachBaseContext
     */
    fun wrapContext(context: Context): Context {
        return try {
            val languageCode = getLanguage(context)
            if (languageCode.isEmpty()) return context
            
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            
            context.createConfigurationContext(config)
        } catch (e: Exception) {
            // If SharedPreferences access fails early, return original context
            context
        }
    }
    
    /**
     * Recreate the activity to apply new locale
     */
    fun recreateActivity(activity: Activity) {
        activity.recreate()
    }
}
