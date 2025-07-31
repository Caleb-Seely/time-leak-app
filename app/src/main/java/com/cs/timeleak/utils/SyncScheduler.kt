package com.cs.timeleak.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import com.cs.timeleak.sync.UsageSyncWorker
import java.util.concurrent.TimeUnit
import java.util.Calendar

object SyncScheduler {
    private const val TAG = "SyncScheduler"
    
    /**
     * Schedules an immediate sync for a newly authenticated user
     */
    fun scheduleImmediateSync(context: Context) {
        try {
            Log.d(TAG, "Scheduling immediate sync for newly authenticated user")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build()

            val immediateSyncRequest = OneTimeWorkRequestBuilder<UsageSyncWorker>()
                .setConstraints(constraints)
                .setInputData(Data.Builder().putBoolean("is_immediate_sync", true).build())
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "immediate_usage_sync",
                androidx.work.ExistingWorkPolicy.REPLACE,
                immediateSyncRequest
            )
            
            Log.d(TAG, "Immediate sync scheduled successfully for new user")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule immediate sync: ${e.message}", e)
        }
    }
    
    /**
     * Schedules a daily periodic sync using WorkManager, aiming for 11:59 PM with a flex interval.
     * Uses a more reliable approach with minimum interval and better constraints.
     */
    fun scheduleDailyPeriodicSync(context: Context) {
        try {
            Log.d(TAG, "Scheduling daily periodic sync with WorkManager")
            
            // Cancel any existing work first
            WorkManager.getInstance(context).cancelUniqueWork("daily_usage_sync")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)  // Allow when battery is low
                .setRequiresCharging(false)       // Don't require charging
                .setRequiresDeviceIdle(false)     // Don't require idle
                .setRequiresStorageNotLow(false)  // Don't require storage
                .build()

            // Calculate initial delay to next 11:59 PM
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now) || equals(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val initialDelay = target.timeInMillis - now.timeInMillis
            
            Log.d(TAG, "Initial delay: ${initialDelay / (1000 * 60 * 60)} hours, ${(initialDelay / (1000 * 60)) % 60} minutes")
            
            // Use 15 hour minimum interval with 1 hour flex window (allows execution between 22:59-23:59)
            val periodicSyncRequest = androidx.work.PeriodicWorkRequestBuilder<UsageSyncWorker>(
                15, TimeUnit.HOURS,  // Minimum interval - WorkManager requires 15+ hours
                1, TimeUnit.HOURS     // Flex interval - 1 hour window
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.MINUTES
                )
                .addTag("daily_sync")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_usage_sync",
                androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                periodicSyncRequest
            )
            
            Log.d(TAG, "Daily periodic sync scheduled successfully with initial delay of ${initialDelay / (1000 * 60)} minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule daily periodic sync: ${e.message}", e)
        }
    }
    
    /**
     * Test method to schedule a sync in 1 minute for testing purposes
     */
    fun scheduleTestSync(context: Context) {
        try {
            Log.d(TAG, "Scheduling test sync in 1 minute")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build()

            val testSyncRequest = OneTimeWorkRequestBuilder<UsageSyncWorker>()
                .setConstraints(constraints)
                .setInputData(Data.Builder().putBoolean("is_immediate_sync", false).build())
                .setInitialDelay(1, TimeUnit.MINUTES)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "test_usage_sync",
                androidx.work.ExistingWorkPolicy.REPLACE,
                testSyncRequest
            )
            
            Log.d(TAG, "Test sync scheduled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule test sync: ${e.message}", e)
        }
    }
} 