package com.cs.timeleak.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cs.timeleak.MainActivity

/**
 * Helper utility to bring the app back to the foreground after permission grants
 */
object AppForegroundHelper {
    private const val TAG = "AppForegroundHelper"
    
    /**
     * Attempt to bring the app back to the foreground
     */
    fun bringAppToForeground(context: Context) {
        try {
            Log.d(TAG, "Attempting to bring app to foreground")
            
            // Method 1: Check if app is already in foreground
            if (isAppInForeground(context)) {
                Log.d(TAG, "App is already in foreground")
                return
            }
            
            // Method 2: Launch main activity with flags to bring to front
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Launched MainActivity to bring app to foreground")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bring app to foreground", e)
        }
    }
    
    /**
     * Check if the app is currently in the foreground
     */
    private fun isAppInForeground(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcesses = activityManager.runningAppProcesses
            
            if (runningAppProcesses != null) {
                for (processInfo in runningAppProcesses) {
                    if (processInfo.processName == context.packageName) {
                        val importance = processInfo.importance
                        Log.d(TAG, "App process importance: $importance")
                        return importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                               importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if app is in foreground", e)
            false
        }
    }
    
    /**
     * Schedule a check to bring app to foreground after a delay
     * This can be useful when we detect permission was granted
     */
    fun scheduleAppReturn(context: Context, delayMs: Long = 1000L) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            bringAppToForeground(context)
        }, delayMs)
    }
    
    /**
     * Create an intent that can be used to return to the app
     */
    fun createReturnToAppIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
    }
}
