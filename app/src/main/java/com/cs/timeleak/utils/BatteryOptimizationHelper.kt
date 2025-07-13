package com.cs.timeleak.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptimizationHelper"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Requested battery optimization ignore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request battery optimization ignore: ${e.message}", e)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened battery optimization settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery optimization settings: ${e.message}", e)
        }
    }
} 