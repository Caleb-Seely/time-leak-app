package com.cs.timeleak.utils

import android.content.Context
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException

object WorkManagerDiagnostics {
    private const val TAG = "WorkManagerDiagnostics"
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Checks and logs the current status of all scheduled work
     */
    fun checkWorkStatus(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            
            Log.d(TAG, "=== WorkManager Diagnostics ===")
            
            // Check midnight sync work (daily 24-hour sync)
            try {
                val midnightWorkInfos = workManager.getWorkInfosForUniqueWork("daily_midnight_sync").get()
                Log.d(TAG, "Daily midnight sync work status:")
                if (midnightWorkInfos.isEmpty()) {
                    Log.w(TAG, "  ❌ No daily midnight sync work found!")
                } else {
                    midnightWorkInfos.forEach { workInfo ->
                        logWorkInfo("  Midnight Sync", workInfo)
                    }
                }
            } catch (e: ExecutionException) {
                Log.e(TAG, "Failed to get midnight sync work info: ${e.message}")
            }
            
            // Check immediate sync work
            try {
                val immediateSyncInfos = workManager.getWorkInfosForUniqueWork("immediate_usage_sync").get()
                Log.d(TAG, "Immediate sync work status:")
                if (immediateSyncInfos.isEmpty()) {
                    Log.d(TAG, "  ✅ No immediate sync work (expected if not recently triggered)")
                } else {
                    immediateSyncInfos.forEach { workInfo ->
                        logWorkInfo("  Immediate Sync", workInfo)
                    }
                }
            } catch (e: ExecutionException) {
                Log.e(TAG, "Failed to get immediate sync work info: ${e.message}")
            }
            
            
            // Check last run time from SharedPreferences
            val prefs = context.getSharedPreferences("usage_sync_prefs", Context.MODE_PRIVATE)
            val lastRunTime = prefs.getLong("last_run_time", 0)
            if (lastRunTime > 0) {
                val lastRunDate = Date(lastRunTime)
                val timeSinceLastRun = System.currentTimeMillis() - lastRunTime
                val hoursSinceLastRun = timeSinceLastRun / (1000 * 60 * 60)
                
                Log.d(TAG, "Last successful sync: ${dateFormatter.format(lastRunDate)} ($hoursSinceLastRun hours ago)")
                
                if (hoursSinceLastRun > 25) {
                    Log.w(TAG, "⚠️ Last sync was more than 25 hours ago - sync may not be working properly")
                } else {
                    Log.d(TAG, "✅ Last sync was within expected timeframe")
                }
            } else {
                Log.w(TAG, "⚠️ No record of last sync time found")
            }
            
            Log.d(TAG, "=== End Diagnostics ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error running WorkManager diagnostics: ${e.message}", e)
        }
    }
    
    private fun logWorkInfo(prefix: String, workInfo: WorkInfo) {
        val state = when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> "📋 ENQUEUED"
            WorkInfo.State.RUNNING -> "🔄 RUNNING"
            WorkInfo.State.SUCCEEDED -> "✅ SUCCEEDED"
            WorkInfo.State.FAILED -> "❌ FAILED"
            WorkInfo.State.BLOCKED -> "🚫 BLOCKED"
            WorkInfo.State.CANCELLED -> "❌ CANCELLED"
        }
        
        Log.d(TAG, "$prefix: $state (${workInfo.id})")
        
        // Log run attempt count if failed
        if (workInfo.state == WorkInfo.State.FAILED) {
            Log.d(TAG, "$prefix: Run attempt count: ${workInfo.runAttemptCount}")
        }
        
        // Log output data if available
        val outputData = workInfo.outputData
        if (outputData.size() > 0) {
            val lastRunTime = outputData.getLong("last_run_time", 0)
            if (lastRunTime > 0) {
                val lastRunDate = Date(lastRunTime)
                Log.d(TAG, "$prefix: Last run output: ${dateFormatter.format(lastRunDate)}")
            }
        }
        
        // Log tags
        if (workInfo.tags.isNotEmpty()) {
            Log.d(TAG, "$prefix: Tags: ${workInfo.tags.joinToString(", ")}")
        }
    }
    
}
