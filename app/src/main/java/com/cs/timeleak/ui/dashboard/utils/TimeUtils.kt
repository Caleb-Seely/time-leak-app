package com.cs.timeleak.ui.dashboard.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility object containing time formatting and calculation functions for the dashboard.
 * 
 * This class provides a centralized location for all time-related operations including:
 * - Duration formatting (milliseconds to human-readable strings)
 * - Date/time formatting for display purposes
 * - Sync scheduling calculations
 * - Countdown timer formatting
 * 
 * All functions are designed to be pure and thread-safe.
 */
object TimeUtils {
    
    /**
     * Formats duration in milliseconds to a human-readable string
     * @param millis Duration in milliseconds
     * @return Formatted string like "2 hr 30 min" or "45 min"
     */
    fun formatDuration(millis: Long): String {
        val hours = millis / (1000 * 60 * 60)
        val minutes = (millis % (1000 * 60 * 60)) / (1000 * 60)
        return when {
            hours > 0 -> "$hours hr ${minutes} min"
            else -> "$minutes min"
        }
    }
    
    /**
     * Formats timestamp to a human-readable date and time
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted string like "Jan 15, 14:30"
     */
    fun formatDateTime(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * Calculates time remaining until next daily sync at 11:59 PM
     * @param lastRunTime Last sync execution time in milliseconds
     * @param scheduledTime Scheduled time (currently unused but kept for compatibility)
     * @return Formatted time remaining string
     */
    fun calculateTimeUntilNextDailySync(
        lastRunTime: Long,
        scheduledTime: Long?
    ): String {
        val now = System.currentTimeMillis()
        
        // Calculate next 11:59 PM
        val calendar = Calendar.getInstance()
        val targetHour = 23 // 11 PM
        val targetMinute = 59 // 59 minutes
        
        // Set target time to 11:59 PM today
        calendar.set(Calendar.HOUR_OF_DAY, targetHour)
        calendar.set(Calendar.MINUTE, targetMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // If it's already past 11:59 PM today, schedule for tomorrow
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val nextSyncTime = calendar.timeInMillis
        val timeRemaining = nextSyncTime - now
        
        return formatTimeRemaining(timeRemaining)
    }
    
    /**
     * Formats remaining time in milliseconds to a countdown string
     * @param millis Time remaining in milliseconds
     * @return Formatted string like "2h 30m 45s", "30m 45s", or "45s"
     */
    fun formatTimeRemaining(millis: Long): String {
        if (millis <= 0) return "Due now"
        
        val hours = millis / (1000 * 60 * 60)
        val minutes = (millis % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (millis % (1000 * 60)) / 1000
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
