package com.cs.timeleak.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import com.cs.timeleak.data.FirestoreRepository
import com.cs.timeleak.data.UsageStatsRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import java.util.Calendar
import androidx.work.ExistingWorkPolicy

class UsageSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val usageStatsRepository = UsageStatsRepository(context)
    private val firestoreRepository = FirestoreRepository()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "UsageSyncWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "[doWork] UsageSyncWorker started")
        
        fun saveLastRunTime(context: Context, time: Long) {
            val prefs = context.getSharedPreferences("usage_sync_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_run_time", time).apply()
        }
        
        fun scheduleNextDailySync() {
            try {
                // Check if this is an immediate sync (first time user)
                val isImmediateSync = inputData.getBoolean("is_immediate_sync", false)
                
                if (isImmediateSync) {
                    // For immediate sync: schedule next sync for 11:59 PM today
                    scheduleDailySyncFor1159PM()
                    Log.d(TAG, "[scheduleNextDailySync] Immediate sync completed, scheduled daily sync for 11:59 PM")
                } else {
                    // For daily sync: schedule next sync for 11:59 PM tomorrow
                    scheduleDailySyncFor1159PMTomorrow()
                    Log.d(TAG, "[scheduleNextDailySync] Daily sync completed, scheduled next daily sync for 11:59 PM tomorrow")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[scheduleNextDailySync] Failed to schedule next daily sync: ${e.message}", e)
            }
        }
        
        return try {
            Log.d(TAG, "[doWork] Checking usage access permission...")
            if (!usageStatsRepository.hasUsageAccessPermission()) {
                Log.w(TAG, "[doWork] No usage access permission, skipping sync")
                return Result.failure()
            }
            Log.d(TAG, "[doWork] Usage access permission granted")
            Log.d(TAG, "[doWork] Checking authentication...")
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "[doWork] No authenticated user. Skipping sync. Phone number required.")
                return Result.success() // Don't retry, just skip
            } else {
                Log.d(TAG, "[doWork] Authenticated user: UID=${currentUser.uid}, Phone=${currentUser.phoneNumber}")
            }
            Log.d(TAG, "[doWork] Fetching today's usage stats...")
            val dailyUsage = usageStatsRepository.getTodayUsageStats()
            if (dailyUsage != null) {
                Log.d(TAG, "[doWork] Uploading usage data: ${dailyUsage.topApps.size} apps, ${dailyUsage.totalScreenTimeMillis}ms total")
                firestoreRepository.uploadUsageData(context, dailyUsage)
                Log.d(TAG, "[doWork] Usage sync completed successfully")
                val now = System.currentTimeMillis()
                saveLastRunTime(context, now)
                val outputData = Data.Builder()
                    .putLong("last_run_time", now)
                    .build()
                scheduleNextDailySync()
                Log.d(TAG, "[doWork] Scheduled next daily sync after successful upload")
                Result.success(outputData)
            } else {
                Log.w(TAG, "[doWork] No usage data available for today")
                val now = System.currentTimeMillis()
                saveLastRunTime(context, now)
                val outputData = Data.Builder()
                    .putLong("last_run_time", now)
                    .build()
                scheduleNextDailySync()
                Log.d(TAG, "[doWork] Scheduled next daily sync after no data")
                Result.success(outputData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[doWork] Usage sync failed: ${e.message}", e)
            Result.retry()
        }
    }
    
    private fun scheduleDailySyncFor1159PM() {
        val calendar = Calendar.getInstance()
        val targetHour = 23 // 11 PM
        val targetMinute = 59 // 59 minutes
        
        // Set target time to 11:59 PM today
        calendar.set(Calendar.HOUR_OF_DAY, targetHour)
        calendar.set(Calendar.MINUTE, targetMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // If it's already past 11:59 PM today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        scheduleSyncForTime(calendar.timeInMillis, "daily_usage_sync")
    }
    
    private fun scheduleDailySyncFor1159PMTomorrow() {
        val calendar = Calendar.getInstance()
        val targetHour = 23 // 11 PM
        val targetMinute = 59 // 59 minutes
        
        // Set target time to 11:59 PM tomorrow
        calendar.add(Calendar.DAY_OF_YEAR, 1) // Add 1 day
        calendar.set(Calendar.HOUR_OF_DAY, targetHour)
        calendar.set(Calendar.MINUTE, targetMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        scheduleSyncForTime(calendar.timeInMillis, "daily_usage_sync")
    }
    
    private fun scheduleSyncForTime(targetTime: Long, workName: String) {
        val delayMillis = targetTime - System.currentTimeMillis()
        val delayHours = TimeUnit.MILLISECONDS.toHours(delayMillis)
        val delayMinutes = TimeUnit.MILLISECONDS.toMinutes(delayMillis) % 60
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()
        val syncWorkRequest = OneTimeWorkRequestBuilder<UsageSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                15, TimeUnit.MINUTES
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            androidx.work.ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
        Log.d(TAG, "[scheduleSyncForTime] Next sync scheduled for ${java.util.Date(targetTime)} (in ${delayHours}h ${delayMinutes}m)")
    }
} 