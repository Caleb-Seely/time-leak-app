package com.cs.timeleak.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.cs.timeleak.sync.UsageSyncWorker
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Reliable daily midnight scheduler using OneTimeWorkRequest chaining.
 * This approach works better than PeriodicWorkRequest for precise daily timing.
 */
object DailyMidnightScheduler {
    private const val TAG = "DailyMidnightScheduler"
    private const val WORK_NAME = "daily_midnight_sync"
    private const val TARGET_HOUR = 23
    private const val TARGET_MINUTE = 59
    
    /**
     * Schedules next 11:59 PM sync using OneTimeWorkRequest.
     * This method schedules itself to run again after completion.
     */
    fun scheduleNext1159PMSync(context: Context) {
        try {
            Log.d(TAG, "Scheduling next 11:59 PM sync...")
            
            val workManager = WorkManager.getInstance(context)
            
            // Calculate delay to next 11:59 PM
            val delay = calculateDelayToNext1159PM()
            val targetTime = System.currentTimeMillis() + delay
            val targetDate = Date(targetTime)
            
            Log.d(TAG, "Next sync scheduled for: $targetDate (in ${delay / (1000 * 60)} minutes)")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)  // Allow when battery is low
                .setRequiresCharging(false)       // Don't require charging
                .setRequiresDeviceIdle(false)     // Don't require idle
                .build()
            
            val syncRequest = OneTimeWorkRequestBuilder<DailyMidnightSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .addTag("midnight_sync")
                .setInputData(Data.Builder()
                    .putLong("scheduled_time", targetTime)
                    .build())
                .build()
            
            // Use REPLACE to ensure only one work is scheduled at a time
            workManager.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
            
            Log.d(TAG, "Successfully scheduled next 11:59 PM sync")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule next 11:59 PM sync: ${e.message}", e)
        }
    }
    
    /**
     * Calculates delay in milliseconds to next 11:59 PM
     */
    private fun calculateDelayToNext1159PM(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
            set(Calendar.MINUTE, TARGET_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If target time has passed today, schedule for tomorrow
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        return target.timeInMillis - now.timeInMillis
    }
    
    /**
     * Initialize the daily scheduling system
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Initializing daily midnight scheduler...")
        scheduleNext1159PMSync(context)
    }
    
    /**
     * Cancel all scheduled midnight syncs
     */
    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled all midnight sync work")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel midnight sync work: ${e.message}", e)
        }
    }
    
    /**
     * Check status of current midnight sync work
     */
    fun checkStatus(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
            
            Log.d(TAG, "=== Midnight Sync Status ===")
            
            if (workInfos.isEmpty()) {
                Log.w(TAG, "❌ No midnight sync work scheduled!")
            } else {
                workInfos.forEach { workInfo ->
                    val state = when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> "📋 ENQUEUED"
                        WorkInfo.State.RUNNING -> "🔄 RUNNING"
                        WorkInfo.State.SUCCEEDED -> "✅ SUCCEEDED"
                        WorkInfo.State.FAILED -> "❌ FAILED"
                        WorkInfo.State.BLOCKED -> "🚫 BLOCKED"
                        WorkInfo.State.CANCELLED -> "❌ CANCELLED"
                    }
                    
                    Log.d(TAG, "Midnight Sync: $state")
                    
                    // Log scheduled time if available
                    val scheduledTime = workInfo.outputData.getLong("scheduled_time", 0)
                    if (scheduledTime > 0) {
                        val scheduledDate = Date(scheduledTime)
                        Log.d(TAG, "Scheduled for: $scheduledDate")
                    }
                    
                    if (workInfo.state == WorkInfo.State.FAILED) {
                        Log.d(TAG, "Failure count: ${workInfo.runAttemptCount}")
                    }
                }
            }
            
            // Check last run time
            val prefs = context.getSharedPreferences("usage_sync_prefs", Context.MODE_PRIVATE)
            val lastRunTime = prefs.getLong("last_run_time", 0)
            if (lastRunTime > 0) {
                val lastRunDate = Date(lastRunTime)
                val hoursSinceLastRun = (System.currentTimeMillis() - lastRunTime) / (1000 * 60 * 60)
                Log.d(TAG, "Last successful sync: $lastRunDate ($hoursSinceLastRun hours ago)")
                
                if (hoursSinceLastRun > 26) {
                    Log.w(TAG, "⚠️ Last sync was more than 26 hours ago")
                }
            } else {
                Log.w(TAG, "⚠️ No record of last successful sync")
            }
            
            Log.d(TAG, "=== End Status ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking midnight sync status: ${e.message}", e)
        }
    }
    
    /**
     * Reset the scheduler (cancel and reschedule)
     */
    fun reset(context: Context) {
        Log.d(TAG, "🔄 Resetting midnight scheduler...")
        cancel(context)
        
        // Wait a moment for cancellation to process
        Thread.sleep(1000)
        
        initialize(context)
        Log.d(TAG, "✅ Midnight scheduler reset complete")
    }
}

/**
 * Worker class that performs the actual sync and schedules the next one
 */
class DailyMidnightSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val TAG = "DailyMidnightSyncWorker"
    private val usageStatsRepository = com.cs.timeleak.data.UsageStatsRepository(context)
    private val firestoreRepository = com.cs.timeleak.data.FirestoreRepository()
    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    
    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "🌙 Daily midnight sync started at ${Date(startTime)}")
        
        return try {
            // Perform the sync logic directly (copied from UsageSyncWorker)
            Log.d(TAG, "[doWork] Checking usage access permission...")
            if (!usageStatsRepository.hasUsageAccessPermission()) {
                Log.w(TAG, "[doWork] No usage access permission, failing sync")
                // Still schedule next sync to maintain daily cadence
                DailyMidnightScheduler.scheduleNext1159PMSync(context)
                return Result.failure(Data.Builder().putString("error", "No usage access permission").build())
            }
            
            Log.d(TAG, "[doWork] Checking authentication...")
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "[doWork] No authenticated user. Failing sync - authentication required.")
                // Still schedule next sync to maintain daily cadence
                DailyMidnightScheduler.scheduleNext1159PMSync(context)
                return Result.failure(Data.Builder().putString("error", "No authenticated user").build())
            }
            
            val userId = currentUser.uid
            val phoneNumber = currentUser.phoneNumber
            Log.d(TAG, "[doWork] Authenticated user: $phoneNumber (UID: $userId)")
            
            Log.d(TAG, "[doWork] Fetching last 24 hours usage stats...")
            val dailyUsage = usageStatsRepository.getLast24HoursUsageStats()
            if (dailyUsage != null) {
                Log.d(TAG, "[doWork] Found usage data: ${dailyUsage.topApps.size} apps, ${dailyUsage.totalScreenTimeMillis / (1000 * 60)} minutes total screen time")
                
                // Only upload if we have meaningful usage data (more than 1 minute)
                if (dailyUsage.totalScreenTimeMillis > 60000) {
                    Log.d(TAG, "[doWork] Uploading usage data to Firestore...")
                    firestoreRepository.uploadUsageData(context, dailyUsage)
                    Log.d(TAG, "[doWork] Usage sync completed successfully")
                    
                    val now = System.currentTimeMillis()
                    saveLastRunTime(context, now)
                    
                    // Schedule the next midnight sync
                    DailyMidnightScheduler.scheduleNext1159PMSync(context)
                    
                    // Return success with timing info
                    val duration = now - startTime
                    val outputData = Data.Builder()
                        .putLong("last_run_time", now)
                        .putLong("total_screen_time", dailyUsage.totalScreenTimeMillis)
                        .putInt("app_count", dailyUsage.topApps.size)
                        .putString("sync_status", "success")
                        .putLong("sync_duration", duration)
                        .build()
                    
                    Log.d(TAG, "[doWork] Sync completed in ${duration}ms")
                    Result.success(outputData)
                } else {
                    Log.d(TAG, "[doWork] Usage data too minimal (${dailyUsage.totalScreenTimeMillis}ms), marking as successful but not uploading")
                    val now = System.currentTimeMillis()
                    saveLastRunTime(context, now)  // Still save run time to prevent constant retries
                    
                    // Schedule the next midnight sync
                    DailyMidnightScheduler.scheduleNext1159PMSync(context)
                    
                    val outputData = Data.Builder()
                        .putLong("last_run_time", now)
                        .putString("sync_status", "skipped_minimal_usage")
                        .build()
                    
                    Result.success(outputData)
                }
            } else {
                Log.w(TAG, "[doWork] No usage data available from repository")
                // Still schedule next sync to maintain daily cadence
                DailyMidnightScheduler.scheduleNext1159PMSync(context)
                
                val outputData = Data.Builder()
                    .putString("sync_status", "no_data_available")
                    .build()
                Result.success(outputData)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception in midnight sync worker: ${e.message}", e)
            
            // Still schedule next sync to maintain daily cadence
            DailyMidnightScheduler.scheduleNext1159PMSync(context)
            
            val outputData = Data.Builder()
                .putString("error", e.message ?: "Unknown error")
                .putString("sync_status", "failed")
                .build()
            
            // Return failure so WorkManager can retry with backoff
            Result.failure(outputData)
        }
    }
    
    private fun saveLastRunTime(context: Context, time: Long) {
        try {
            val prefs = context.getSharedPreferences("usage_sync_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_run_time", time).apply()
            Log.d(TAG, "[doWork] Last run time saved: ${Date(time)}")
        } catch (e: Exception) {
            Log.e(TAG, "[doWork] Failed to save last run time: ${e.message}", e)
        }
    }
}
