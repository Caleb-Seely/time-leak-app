package com.cs.timeleak.data

import android.content.Context

object UserPrefs {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_PHONE = "phone_number"
    private const val KEY_UID = "uid"
    private const val KEY_GOAL_TIME = "goal_time_millis"

    fun saveUser(context: Context, phone: String?, uid: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PHONE, phone)
            .putString(KEY_UID, uid)
            .apply()
    }

    fun getPhone(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PHONE, null)
    }

    fun getUid(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UID, null)
    }

    fun saveGoalTime(context: Context, goalTimeMillis: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_GOAL_TIME, goalTimeMillis)
            .apply()
    }

    fun getGoalTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedGoal = prefs.getLong(KEY_GOAL_TIME, 0L)
        // Return default goal of 4 hours 30 minutes (16,200,000 milliseconds) if no goal is set
        return if (savedGoal == 0L) 16_200_000L else savedGoal
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
} 