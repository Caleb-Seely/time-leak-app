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
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "[doWork] UsageSyncWorker started at ${java.util.Date(startTime)}")
        
        fun saveLastRunTime(context: Context, time: Long) {
            try {
                val prefs = context.getSharedPreferences("usage_sync_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("last_run_time", time).apply()
                Log.d(TAG, "[doWork] Last run time saved: ${java.util.Date(time)}")
            } catch (e: Exception) {
                Log.e(TAG, "[doWork] Failed to save last run time: ${e.message}", e)
            }
        }
        
        fun getLastRunTime(context: Context): Long {
            return try {
                val prefs = context.getSharedPreferences("usage_sync_prefs", Context.MODE_PRIVATE)
                prefs.getLong("last_run_time", 0L)
            } catch (e: Exception) {
                Log.e(TAG, "[doWork] Failed to get last run time: ${e.message}", e)
                0L
            }
        }
        
        return try {
            val lastRun = getLastRunTime(context)
            val hoursSinceLastRun = if (lastRun > 0) (startTime - lastRun) / (1000 * 60 * 60) else -1
            
            Log.d(TAG, "[doWork] Last successful sync: ${if (lastRun > 0) java.util.Date(lastRun).toString() else "Never"} ($hoursSinceLastRun hours ago)")
            
            Log.d(TAG, "[doWork] Checking usage access permission...")
            if (!usageStatsRepository.hasUsageAccessPermission()) {
                Log.w(TAG, "[doWork] No usage access permission, failing sync")
                return Result.failure(Data.Builder().putString("error", "No usage access permission").build())
            }
            Log.d(TAG, "[doWork] Usage access permission granted")
            
            Log.d(TAG, "[doWork] Checking authentication...")
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "[doWork] No authenticated user. Failing sync - authentication required.")
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
                    
                    val outputData = Data.Builder()
                        .putLong("last_run_time", now)
                        .putLong("total_screen_time", dailyUsage.totalScreenTimeMillis)
                        .putInt("app_count", dailyUsage.topApps.size)
                        .putString("sync_status", "success")
                        .build()
                    
                    val syncDuration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "[doWork] Sync completed in ${syncDuration}ms")
                    
                    Result.success(outputData)
                } else {
                    Log.d(TAG, "[doWork] Usage data too minimal (${dailyUsage.totalScreenTimeMillis}ms), marking as successful but not uploading")
                    val now = System.currentTimeMillis()
                    saveLastRunTime(context, now)  // Still save run time to prevent constant retries
                    
                    val outputData = Data.Builder()
                        .putLong("last_run_time", now)
                        .putString("sync_status", "skipped_minimal_usage")
                        .build()
                    
                    Result.success(outputData)
                }
            } else {
                Log.w(TAG, "[doWork] No usage data available from repository")
                // Don't mark as failed - this might be normal (e.g., device wasn't used)
                val outputData = Data.Builder()
                    .putString("sync_status", "no_data_available")
                    .build()
                Result.success(outputData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[doWork] Failed to sync usage data: ${e.message}", e)
            
            val outputData = Data.Builder()
                .putString("error", e.message ?: "Unknown error")
                .putString("sync_status", "failed")
                .build()
            
            // Return failure so WorkManager can retry with backoff
            Result.failure(outputData)
        }
    }
    
}