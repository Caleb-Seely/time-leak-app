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
    
} 