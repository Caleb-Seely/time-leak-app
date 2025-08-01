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

        // Get usage stats and events
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val (launchCounts, accurateUsageTimes) = getAccurateUsageData(startTime, endTime)

        val filteredUsageStats = usageStats?.filterNotNull()?.filter {
            it.totalTimeInForeground > 0
        } ?: emptyList()

        // Use the more accurate usage times if available, otherwise fall back to UsageStats
        val totalScreenTime = if (accurateUsageTimes.isNotEmpty()) {
            accurateUsageTimes.values.sum()
        } else {
            filteredUsageStats.sumOf { it.totalTimeInForeground }
        }

        // Use a map to track unique packages and their usage
        val uniqueApps = mutableMapOf<String, AppUsage>()
        
        filteredUsageStats.forEach { stats ->
            val packageName = stats.packageName
            val usageTime = accurateUsageTimes[packageName] ?: stats.totalTimeInForeground
            
            // Only include apps that were actually used in the last 24 hours
            if (usageTime > 0 && stats.lastTimeUsed >= startTime) {
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

        // Get usage stats and events
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val (launchCounts, accurateUsageTimes) = getAccurateUsageData(startTime, endTime)

        val filteredUsageStats = usageStats?.filterNotNull()?.filter {
            it.totalTimeInForeground > 0
        } ?: emptyList()

        // Use the more accurate usage times if available, otherwise fall back to UsageStats
        val totalScreenTime = if (accurateUsageTimes.isNotEmpty()) {
            accurateUsageTimes.values.sum()
        } else {
            filteredUsageStats.sumOf { it.totalTimeInForeground }
        }

        // Use a map to track unique packages and their usage
        val uniqueApps = mutableMapOf<String, AppUsage>()
        
        filteredUsageStats.forEach { stats ->
            val packageName = stats.packageName
            val usageTime = accurateUsageTimes[packageName] ?: stats.totalTimeInForeground
            
            // Only include apps that were actually used today
            if (usageTime > 0 && stats.lastTimeUsed >= startTime) {
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
     * Gets more accurate usage data by analyzing UsageEvents in detail
     */
    private fun getAccurateUsageData(startTime: Long, endTime: Long): Pair<Map<String, Int>, Map<String, Long>> {
        val launchCounts = mutableMapOf<String, Int>()
        val usageTimes = mutableMapOf<String, Long>()
        val appSessions = mutableMapOf<String, Long>() // Track when apps started

        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)

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
                        appSessions[packageName]?.let { startTime ->
                            val sessionDuration = event.timeStamp - startTime
                            if (sessionDuration > 0) {
                                usageTimes[packageName] = usageTimes.getOrDefault(packageName, 0) + sessionDuration
                            }
                        }
                        appSessions.remove(packageName)
                    }
                }
            }

            // Handle apps that are still running (haven't been paused)
            val currentTime = System.currentTimeMillis()
            appSessions.forEach { (packageName, startTime) ->
                val sessionDuration = currentTime - startTime
                if (sessionDuration > 0) {
                    usageTimes[packageName] = usageTimes.getOrDefault(packageName, 0) + sessionDuration
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
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
                if (stats.totalTimeInForeground > 0) {
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
                    dailyTotals[dateKey] = dailyTotals.getOrDefault(dateKey, 0L) + stats.totalTimeInForeground
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
            e.printStackTrace()
            return null
        }
    }
}
