package com.huginmunin.app.utils

import android.content.Context

object UserManager {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_PHONE = "user_phone"
    private const val KEY_USER_DOB = "user_dob"
    
    fun saveUserProfile(context: Context, name: String, phone: String, dob: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_PHONE, phone)
            putString(KEY_USER_DOB, dob)
            apply()
        }
    }
    
    fun getUserName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }
    
    fun getUserPhone(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_PHONE, "") ?: ""
    }
    
    fun getUserDob(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_DOB, "") ?: ""
    }
    
    fun isProfileSet(context: Context): Boolean {
        return getUserName(context).isNotEmpty()
    }
}
