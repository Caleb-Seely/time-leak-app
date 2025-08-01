package com.cs.timeleak.data

import android.content.Context

object UserPrefs {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_PHONE = "phone_number"
    private const val KEY_UID = "uid"
    private const val KEY_GOAL_TIME = "goal_time_millis"
    private const val KEY_BASELINE_SCREEN_TIME = "baseline_screen_time_millis"
    private const val KEY_BASELINE_CAPTURED = "baseline_captured"

    fun saveUser(context: Context, phone: String?, uid: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        android.util.Log.d("UserPrefs", "Saving user: phone=$phone, uid=$uid")
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
        
        // If no goal is set, calculate default as 90% of baseline screen time
        return if (savedGoal == 0L) {
            val baseline = getBaselineScreenTime(context)
            if (baseline != null) {
                (baseline * 0.9).toLong()
            } else {
                // Fallback to 4 hours 30 minutes if baseline not available
                16_200_000L
            }
        } else {
            savedGoal
        }
    }

    fun saveBaselineScreenTime(context: Context, baselineMillis: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        android.util.Log.d("UserPrefs", "Saving baseline screen time: ${baselineMillis}ms")
        prefs.edit()
            .putLong(KEY_BASELINE_SCREEN_TIME, baselineMillis)
            .putBoolean(KEY_BASELINE_CAPTURED, true)
            .apply()
    }

    fun getBaselineScreenTime(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.getBoolean(KEY_BASELINE_CAPTURED, false)) {
            prefs.getLong(KEY_BASELINE_SCREEN_TIME, 0L)
        } else {
            null
        }
    }

    fun isBaselineCaptured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BASELINE_CAPTURED, false)
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
