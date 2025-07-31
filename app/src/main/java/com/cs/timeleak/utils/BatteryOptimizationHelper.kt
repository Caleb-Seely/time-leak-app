package com.cs.timeleak.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.app.AlarmManager

object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptimizationHelper"

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // On older versions, exact alarms are always allowed
        }
    }
    
    fun requestExactAlarmPermission(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Requested exact alarm permission")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request exact alarm permission: ${e.message}", e)
            }
        }
    }
    
    fun openAlarmSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened alarm settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open alarm settings: ${e.message}", e)
        }
    }
    
    /**
     * Checks if the app is whitelisted from battery optimizations
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Not applicable for older Android versions
        }
    }
    
    /**
     * Opens the battery optimization settings for the app
     */
    fun requestDisableBatteryOptimization(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:${context.packageName}")
                }
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened battery optimization settings")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open battery optimization settings: ${e.message}")
                // Fallback to general battery optimization settings
                openBatteryOptimizationSettings(context)
            }
        }
    }
    
    /**
     * Opens the general battery optimization settings page
     */
    private fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened general battery optimization settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery optimization settings: ${e.message}")
        }
    }
    
    /**
     * Gets comprehensive battery optimization status and instructions
     */
    fun getBatteryOptimizationStatus(context: Context): BatteryOptimizationStatus {
        val isOptimizationDisabled = isBatteryOptimizationDisabled(context)
        val canScheduleAlarms = canScheduleExactAlarms(context)
        
        return BatteryOptimizationStatus(
            isBatteryOptimizationDisabled = isOptimizationDisabled,
            canScheduleExactAlarms = canScheduleAlarms,
            needsAction = !isOptimizationDisabled || !canScheduleAlarms
        )
    }
    
    data class BatteryOptimizationStatus(
        val isBatteryOptimizationDisabled: Boolean,
        val canScheduleExactAlarms: Boolean,
        val needsAction: Boolean
    )
} 