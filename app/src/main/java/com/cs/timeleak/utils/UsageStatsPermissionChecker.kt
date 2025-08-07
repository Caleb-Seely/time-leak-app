package com.cs.timeleak.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

object UsageStatsPermissionChecker {
    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getUsageAccessSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            // Try to open directly to the app's usage access page
            // This works on most Android versions but falls back gracefully
            data = android.net.Uri.parse("package:${context.packageName}")
            
            // Add flags to improve return behavior
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
    }
    
    /**
     * Enhanced method that attempts to return user to app after permission grant
     */
    fun requestUsageAccessWithReturn(context: Context): Intent {
        val settingsIntent = getUsageAccessSettingsIntent(context)
        
        // For activities, we can use startActivityForResult approach
        if (context is android.app.Activity) {
            // Add additional flags for better back navigation
            settingsIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                 Intent.FLAG_ACTIVITY_SINGLE_TOP
            return settingsIntent
        } else {
            // For non-activity contexts, use the standard approach
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return settingsIntent
        }
    }
} 