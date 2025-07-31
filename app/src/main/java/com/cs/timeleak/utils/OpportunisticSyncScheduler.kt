package com.cs.timeleak.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.cs.timeleak.sync.UsageSyncWorker
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Alternative sync strategy that doesn't rely on battery optimization exemption.
 * Uses multiple triggers and opportunistic syncing for better reliability.
 */
object OpportunisticSyncScheduler {
    private const val TAG = "OpportunisticSyncScheduler"
    
    /**
     * Schedules multiple sync opportunities throughout the day to increase chances of success
     * without requiring battery optimization exemption
     */
    fun scheduleOpportunisticSyncs(context: Context) {
        try {
            Log.d(TAG, "Setting up opportunistic sync strategy...")
            
            val workManager = WorkManager.getInstance(context)
            
            // Cancel existing work first
            workManager.cancelUniqueWork("daily_usage_sync")
            workManager.cancelUniqueWork("opportunistic_sync_1")
            workManager.cancelUniqueWork("opportunistic_sync_2")
            workManager.cancelUniqueWork("opportunistic_sync_3")
            
            // Strategy 1: Multiple daily sync attempts (morning, afternoon, evening)
            scheduleMultipleDailySyncs(context)
            
            // Strategy 2: App launch trigger sync
            scheduleAppLaunchSync(context)
            
            // Strategy 3: Network available trigger sync
            scheduleNetworkTriggerSync(context)
            
            Log.d(TAG, "Opportunistic sync strategy configured successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup opportunistic sync: ${e.message}", e)
        }
    }
    
    /**
     * Schedules sync attempts at 3 different times of day to increase success rate
     */
    private fun scheduleMultipleDailySyncs(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val now = Calendar.getInstance()
        
        // Sync times: 8 AM, 2 PM, and 11 PM
        val syncTimes = listOf(
            Pair(8, 0),   // 8:00 AM
            Pair(14, 0),  // 2:00 PM  
            Pair(23, 0)   // 11:00 PM
        )
        
        syncTimes.forEachIndexed { index, (hour, minute) ->
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                
                // If time has passed today, schedule for tomorrow
                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            val initialDelay = target.timeInMillis - now.timeInMillis
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build()
            
            // Use shorter periodic intervals to increase chances
            val syncRequest = PeriodicWorkRequestBuilder<UsageSyncWorker>(
                8, TimeUnit.HOURS,     // 8-hour intervals (3 times per day)
                1, TimeUnit.HOURS      // 1-hour flex window
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    30, TimeUnit.MINUTES
                )
                .addTag("opportunistic_sync")
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                "opportunistic_sync_${index + 1}",
                ExistingPeriodicWorkPolicy.REPLACE,
                syncRequest
            )
            
            Log.d(TAG, "Scheduled sync ${index + 1} for ${hour}:${minute} (delay: ${initialDelay / (1000 * 60)} minutes)")
        }
    }
    
    /**
     * Schedules sync to trigger when app is launched (user engagement triggers sync)
     */
    private fun scheduleAppLaunchSync(context: Context) {
        // Check if we should sync on app launch
        val prefs = context.getSharedPreferences("usage_sync_prefs", Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong("last_run_time", 0)
        val now = System.currentTimeMillis()
        val hoursSinceLastSync = (now - lastSyncTime) / (1000 * 60 * 60)
        
        // If last sync was more than 18 hours ago, trigger immediate sync
        if (lastSyncTime == 0L || hoursSinceLastSync >= 18) {
            Log.d(TAG, "App launch sync triggered - last sync was $hoursSinceLastSync hours ago")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val appLaunchSyncRequest = OneTimeWorkRequestBuilder<UsageSyncWorker>()
                .setConstraints(constraints)
                .setInputData(Data.Builder().putBoolean("is_app_launch_sync", true).build())
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                "app_launch_sync",
                ExistingWorkPolicy.REPLACE,
                appLaunchSyncRequest
            )
        }
    }
    
    /**
     * Schedules sync when network becomes available (opportunistic networking)
     */
    private fun scheduleNetworkTriggerSync(context: Context) {
        val prefs = context.getSharedPreferences("usage_sync_prefs", Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong("last_run_time", 0)
        val now = System.currentTimeMillis()
        val hoursSinceLastSync = (now - lastSyncTime) / (1000 * 60 * 60)
        
        // Only schedule network trigger if we haven't synced in the last 12 hours
        if (lastSyncTime == 0L || hoursSinceLastSync >= 12) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .build()
            
            val networkSyncRequest = OneTimeWorkRequestBuilder<UsageSyncWorker>()
                .setConstraints(constraints)
                .setInputData(Data.Builder().putBoolean("is_network_trigger_sync", true).build())
                .setInitialDelay(5, TimeUnit.MINUTES) // Small delay to avoid immediate execution
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                "network_trigger_sync",
                ExistingWorkPolicy.REPLACE,
                networkSyncRequest
            )
            
            Log.d(TAG, "Network trigger sync scheduled - last sync was $hoursSinceLastSync hours ago")
        }
    }
    
    /**
     * Call this when app comes to foreground to trigger opportunistic sync
     */
    fun onAppForeground(context: Context) {
        Log.d(TAG, "App came to foreground, checking for opportunistic sync...")
        scheduleAppLaunchSync(context)
        scheduleNetworkTriggerSync(context)
    }
    
    /**
     * Checks current sync status and logs diagnostics for opportunistic strategy
     */
    fun checkOpportunisticSyncStatus(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            
            Log.d(TAG, "=== Opportunistic Sync Status ===")
            
            // Check each opportunistic sync work
            for (i in 1..3) {
                try {
                    val workInfos = workManager.getWorkInfosForUniqueWork("opportunistic_sync_$i").get()
                    if (workInfos.isNotEmpty()) {
                        val workInfo = workInfos.first()
                        Log.d(TAG, "Opportunistic Sync $i: ${workInfo.state}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get status for opportunistic sync $i: ${e.message}")
                }
            }
            
            // Check last successful sync
            val prefs = context.getSharedPreferences("usage_sync_prefs", Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong("last_run_time", 0)
            if (lastSyncTime > 0) {
                val hoursSinceLastSync = (System.currentTimeMillis() - lastSyncTime) / (1000 * 60 * 60)
                Log.d(TAG, "Last successful sync: $hoursSinceLastSync hours ago")
                
                if (hoursSinceLastSync > 30) {
                    Log.w(TAG, "⚠️ No sync in over 30 hours - opportunistic strategy may need adjustment")
                }
            }
            
            Log.d(TAG, "=== End Opportunistic Status ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking opportunistic sync status: ${e.message}", e)
        }
    }
}
