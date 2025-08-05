package com.cs.timeleak.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.analytics.FirebaseAnalytics
import android.os.Bundle

// Data classes to hold your app usage information
data class DailyUsage(
    val date: String,
    val totalScreenTimeMillis: Long,
    val topApps: List<AppUsage>,
    val socialMediaTimeMillis: Long,
    val entertainmentTimeMillis: Long
)

data class AppUsage(
    val packageName: String,
    val usageTimeMillis: Long,
    val lastTimeUsed: Long,
    val launchCount: Int,
    val appName: String = "",
    val category: String = ""
)

class UsageStatsRepository(private val context: Context) {

    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager: PackageManager = context.packageManager

    // Maximum reasonable daily screen time (24 hours in milliseconds)
    private val MAX_DAILY_SCREEN_TIME = 24 * 60 * 60 * 1000L

    // Maximum reasonable single app session (4 hours in milliseconds)
    private val MAX_SINGLE_APP_SESSION = 4 * 60 * 60 * 1000L

    /**
     * Retrieves usage statistics for the last 24 hours from now.
     */
    fun getLast24HoursUsageStats(): DailyUsage? {
        if (!hasUsageAccessPermission()) {
            return null
        }

        // Set to 24 hours ago
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 24 * 60 * 60 * 1000 // 24 hours in milliseconds

        return calculateUsageStats(startTime, endTime)
    }

    /**
     * Retrieves usage statistics for the current calendar day (midnight to now).
     */
    fun getTodayUsageStats(): DailyUsage? {
        if (!hasUsageAccessPermission()) {
            return null
        }

        // Set to start of today (00:00:00)
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        return calculateUsageStats(startTime, endTime)
    }

    /**
     * Common method to calculate usage stats with validation
     */
    private fun calculateUsageStats(startTime: Long, endTime: Long): DailyUsage? {
        // Get usage stats and events
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val (launchCounts, accurateUsageTimes) = getAccurateUsageData(startTime, endTime)

        val filteredUsageStats = usageStats?.filterNotNull()?.filter {
            getUsageTime(it) > 0 &&
                    it.lastTimeUsed >= startTime &&
                    it.lastTimeUsed <= endTime // Ensure timestamps are within range
        } ?: emptyList()

        // Use a map to track unique packages and their usage
        val uniqueApps = mutableMapOf<String, AppUsage>()

        filteredUsageStats.forEach { stats ->
            val packageName = stats.packageName
            var usageTime = accurateUsageTimes[packageName] ?: getUsageTime(stats)

            // CRITICAL: Validate and cap usage time to prevent impossible values
            usageTime = validateUsageTime(usageTime, packageName)

            // Only include apps that were actually used in the time period
            if (usageTime > 0 && stats.lastTimeUsed >= startTime && stats.lastTimeUsed <= endTime) {
                val appName = getAppName(packageName)
                val category = getAppCategory(packageName)

                uniqueApps[packageName] = AppUsage(
                    packageName = packageName,
                    usageTimeMillis = usageTime,
                    lastTimeUsed = stats.lastTimeUsed,
                    launchCount = launchCounts[packageName] ?: 0,
                    appName = appName,
                    category = category
                )
            }
        }

        val topApps = uniqueApps.values
            .sortedByDescending { it.usageTimeMillis }
            .take(100)

        // Calculate total with validation
        var totalScreenTime = uniqueApps.values.sumOf { it.usageTimeMillis }
        totalScreenTime = validateTotalScreenTime(totalScreenTime)

        val socialMediaTime = uniqueApps.values
            .filter { it.packageName in AppCategoryPackages.SOCIAL_MEDIA }
            .sumOf { it.usageTimeMillis }
        val entertainmentTime = uniqueApps.values
            .filter { it.packageName in AppCategoryPackages.ENTERTAINMENT }
            .sumOf { it.usageTimeMillis }

        return DailyUsage(
            date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_DATE),
            totalScreenTimeMillis = totalScreenTime,
            topApps = topApps,
            socialMediaTimeMillis = socialMediaTime,
            entertainmentTimeMillis = entertainmentTime
        )
    }

    /**
     * Validates individual app usage time to prevent impossible values
     */
    private fun validateUsageTime(usageTime: Long, packageName: String): Long {
        return when {
            usageTime < 0 -> {
                android.util.Log.w("UsageStats", "Negative usage time for $packageName: $usageTime")
                0L
            }
            usageTime > MAX_DAILY_SCREEN_TIME -> {
                android.util.Log.w("UsageStats", "Capping excessive usage time for $packageName: ${usageTime / (1000 * 60 * 60)} hours")
                MAX_DAILY_SCREEN_TIME
            }
            else -> usageTime
        }
    }

    /**
     * Validates total screen time to prevent impossible values
     */
    private fun validateTotalScreenTime(totalTime: Long): Long {
        return when {
            totalTime < 0 -> {
                android.util.Log.w("UsageStats", "Negative total screen time: $totalTime")
                0L
            }
            totalTime > MAX_DAILY_SCREEN_TIME -> {
                android.util.Log.w("UsageStats", "Capping excessive total screen time: ${totalTime / (1000 * 60 * 60)} hours")
                MAX_DAILY_SCREEN_TIME
            }
            else -> totalTime
        }
    }

    /**
     * Gets more accurate usage data by analyzing UsageEvents in detail
     */
    private fun getAccurateUsageData(startTime: Long, endTime: Long): Pair<Map<String, Int>, Map<String, Long>> {
        val launchCounts = mutableMapOf<String, Int>()
        val usageTimes = mutableMapOf<String, Long>()
        val appSessions = mutableMapOf<String, Long>() // Track when apps started

        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()

            val analytics = FirebaseAnalytics.getInstance(context)

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)

                // Validate event timestamp is within our query range
                if (event.timeStamp < startTime || event.timeStamp > endTime) {
                    continue
                }

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        // App came to foreground
                        val packageName = event.packageName

                        // Count as a launch (but filter out very quick switches)
                        val lastSession = appSessions[packageName] ?: 0
                        if (event.timeStamp - lastSession > 2000) { // 2 second threshold
                            launchCounts[packageName] = launchCounts.getOrDefault(packageName, 0) + 1
                        }

                        appSessions[packageName] = event.timeStamp
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        // App went to background
                        val packageName = event.packageName
                        appSessions[packageName]?.let { sessionStartTime ->
                            var sessionDuration = event.timeStamp - sessionStartTime

                            // CRITICAL: Validate session duration
                            if (sessionDuration > 0 && sessionDuration <= MAX_SINGLE_APP_SESSION) {
                                usageTimes[packageName] = usageTimes.getOrDefault(packageName, 0) + sessionDuration
                            } else if (sessionDuration > MAX_SINGLE_APP_SESSION) {
                                android.util.Log.w("UsageStats",
                                    "Capping excessive session for $packageName: ${sessionDuration / (1000 * 60)} minutes")
                                // Cap to reasonable maximum or skip entirely based on your preference
                                usageTimes[packageName] = usageTimes.getOrDefault(packageName, 0) + MAX_SINGLE_APP_SESSION
                            }
                        }
                        appSessions.remove(packageName)
                    }
                }
            }

            // Handle apps that are still running (haven't been paused)
            val currentTime = minOf(System.currentTimeMillis(), endTime) // Don't go beyond our query end time
            appSessions.forEach { (packageName, sessionStartTime) ->
                var sessionDuration = currentTime - sessionStartTime

                // CRITICAL: Validate ongoing session duration
                if (sessionDuration > 0 && sessionDuration <= MAX_SINGLE_APP_SESSION) {
                    usageTimes[packageName] = usageTimes.getOrDefault(packageName, 0) + sessionDuration
                } else if (sessionDuration > MAX_SINGLE_APP_SESSION) {
                    android.util.Log.w("UsageStats",
                        "Capping excessive ongoing session for $packageName: ${sessionDuration / (1000 * 60)} minutes")
                    // Cap to reasonable maximum
                    usageTimes[packageName] = usageTimes.getOrDefault(packageName, 0) + MAX_SINGLE_APP_SESSION
                }
            }

        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            val analytics = FirebaseAnalytics.getInstance(context)
            val errorDetails = Bundle()
            errorDetails.putString("error_message", e.message)
            errorDetails.putString("error_type", e.javaClass.simpleName)
            analytics.logEvent("usage_data_error", errorDetails)
            android.util.Log.e("UsageStats", "Error getting accurate usage data", e)
        }

        // Log analysis results to Analytics
        try {
            val analytics = FirebaseAnalytics.getInstance(context)
            val bundle = Bundle()
            bundle.putInt("launch_count", launchCounts.size)
            bundle.putInt("usage_times_count", usageTimes.size)
            analytics.logEvent("usage_data_collection", bundle)
        } catch (e: Exception) {
            android.util.Log.w("UsageStats", "Failed to log analytics", e)
        }

        return Pair(launchCounts, usageTimes)
    }

    /**
     * Gets the app name from package manager
     */
    private fun getAppName(packageName: String): String {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    /**
     * Gets the app category based on package name
     */
    private fun getAppCategory(packageName: String): String {
        return when {
            packageName in AppCategoryPackages.SOCIAL_MEDIA -> "social_media"
            packageName in AppCategoryPackages.ENTERTAINMENT -> "entertainment"
            else -> "other"
        }
    }

    /**
     * Gets the most accurate usage time available based on Android API level.
     * Prefers totalTimeVisible (API 29+) over totalTimeInForeground for better accuracy.
     * 
     * totalTimeVisible accounts for:
     * - Screen on/off state
     * - Multi-window scenarios (split screen, PiP)
     * - Actual visibility to user
     * 
     * This should provide better alignment with Digital Wellbeing measurements.
     */
    private fun getUsageTime(stats: UsageStats): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29): Use totalTimeVisible for more accurate screen time
            // This accounts for screen state and multi-window scenarios
            android.util.Log.v("UsageStats", "Using totalTimeVisible for ${stats.packageName}: ${stats.totalTimeVisible}ms vs totalTimeInForeground: ${stats.totalTimeInForeground}ms")
            stats.totalTimeVisible
        } else {
            // Android 9 and below: Fall back to totalTimeInForeground
            android.util.Log.v("UsageStats", "Using totalTimeInForeground for ${stats.packageName}: ${stats.totalTimeInForeground}ms (API < 29)")
            stats.totalTimeInForeground
        }
    }

    fun hasUsageAccessPermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageAccessPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Calculates the average daily screen time over the last 30 days
     * @return Average daily screen time in milliseconds, or null if no data available
     */
    fun getLast30DayAverageScreenTime(): Long? {
        if (!hasUsageAccessPermission()) {
            return null
        }

        try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (30 * 24 * 60 * 60 * 1000L) // 30 days in milliseconds

            // Get usage stats for the last 30 days
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (usageStats.isNullOrEmpty()) {
                return null
            }

            // Group usage stats by day and calculate daily totals
            val dailyTotals = mutableMapOf<String, Long>()

            usageStats.forEach { stats ->
                if (getUsageTime(stats) > 0 &&
                    stats.lastTimeUsed >= startTime &&
                    stats.lastTimeUsed <= endTime) {

                    // Validate individual app usage time
                    val validatedUsageTime = validateUsageTime(getUsageTime(stats), stats.packageName)

                    // Convert timestamp to date string (YYYY-MM-DD format)
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = stats.lastTimeUsed
                    val dateKey = String.format(
                        "%04d-%02d-%02d",
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH) + 1,
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )

                    // Add to daily total for this day
                    var dayTotal = dailyTotals.getOrDefault(dateKey, 0L) + validatedUsageTime
                    // Validate daily total doesn't exceed maximum
                    dayTotal = validateTotalScreenTime(dayTotal)
                    dailyTotals[dateKey] = dayTotal
                }
            }

            // Calculate average from daily totals
            return if (dailyTotals.isNotEmpty()) {
                val totalScreenTime = dailyTotals.values.sum()
                val daysWithData = dailyTotals.size
                totalScreenTime / daysWithData
            } else {
                null
            }

        } catch (e: Exception) {
            android.util.Log.e("UsageStats", "Error calculating 30-day average", e)
            return null
        }
    }
}