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
            
            // Check periodic sync work
            try {
                val periodicWorkInfos = workManager.getWorkInfosForUniqueWork("daily_usage_sync").get()
                Log.d(TAG, "Daily periodic sync work status:")
                if (periodicWorkInfos.isEmpty()) {
                    Log.w(TAG, "  ❌ No daily periodic sync work found!")
                } else {
                    periodicWorkInfos.forEach { workInfo ->
                        logWorkInfo("  Periodic Sync", workInfo)
                    }
                }
            } catch (e: ExecutionException) {
                Log.e(TAG, "Failed to get daily sync work info: ${e.message}")
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
            
            // Check test sync work
            try {
                val testSyncInfos = workManager.getWorkInfosForUniqueWork("test_usage_sync").get()
                Log.d(TAG, "Test sync work status:")
                if (testSyncInfos.isEmpty()) {
                    Log.d(TAG, "  ✅ No test sync work (expected)")
                } else {
                    testSyncInfos.forEach { workInfo ->
                        logWorkInfo("  Test Sync", workInfo)
                    }
                }
            } catch (e: ExecutionException) {
                Log.e(TAG, "Failed to get test sync work info: ${e.message}")
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
    
    /**
     * Cancels all existing work and reschedules fresh daily sync
     */
    fun resetDailySync(context: Context) {
        try {
            Log.d(TAG, "🔄 Resetting daily sync...")
            val workManager = WorkManager.getInstance(context)
            
            // Cancel all existing work
            workManager.cancelUniqueWork("daily_usage_sync")
            workManager.cancelUniqueWork("immediate_usage_sync") 
            workManager.cancelUniqueWork("test_usage_sync")
            
            Log.d(TAG, "Cancelled all existing sync work")
            
            // Wait a moment for cancellation to process
            Thread.sleep(1000)
            
            // Reschedule daily sync
            SyncScheduler.scheduleDailyPeriodicSync(context)
            
            Log.d(TAG, "✅ Daily sync has been reset and rescheduled")
            
            // Check status after reset
            Thread.sleep(2000)
            checkWorkStatus(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting daily sync: ${e.message}", e)
        }
    }
    
    /**
     * Schedules a test sync in 2 minutes to verify the system is working
     */
    fun scheduleTestSyncInTwoMinutes(context: Context) {
        try {
            Log.d(TAG, "🧪 Scheduling test sync in 2 minutes...")
            SyncScheduler.scheduleTestSync(context)
            Log.d(TAG, "✅ Test sync scheduled - check logs in 2 minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling test sync: ${e.message}", e)
        }
    }
}
