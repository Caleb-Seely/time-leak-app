package com.cs.timeleak.ui.dashboard.utils

import androidx.compose.ui.graphics.Color

/**
 * Utility object for color calculations based on screen time and progress.
 * 
 * This class provides consistent color theming across the dashboard based on:
 * - Goal progress (green = good, orange = warning, red = over limit)
 * - Usage trends (green = improvement, red = regression)
 * - Status indicators for various UI elements
 * 
 * Color logic follows a traffic light system for intuitive user understanding.
 */
object ColorUtils {
    
    // === COLOR CONSTANTS ===
    // Material Design color palette for consistent theming
    private val GREEN_COLOR = Color(0xFF4CAF50)    // Success/Good progress
    private val ORANGE_COLOR = Color(0xFFFF9800)   // Warning/Approaching limit
    private val RED_COLOR = Color(0xFFF44336)      // Error/Over limit
    
    /**
     * Determines color based on current screen time usage relative to goal
     * @param currentUsage Current screen time in milliseconds
     * @param goalTime Daily goal time in milliseconds
     * @return Color representing the usage status (green, orange, or red)
     */
    fun getScreenTimeColor(currentUsage: Long, goalTime: Long): Color {
        val progress = currentUsage.toFloat() / goalTime.toFloat()
        return getProgressColor(progress)
    }
    
    /**
     * Determines color based on progress percentage
     * @param progress Progress as a float (0.0 to 1.0+)
     * @return Color representing the progress status
     *         - Green: < 80% of goal
     *         - Orange: 80-100% of goal
     *         - Red: > 100% of goal
     */
    fun getProgressColor(progress: Float): Color {
        return when {
            progress < 0.8f -> GREEN_COLOR  // Good - under 80%
            progress < 1.0f -> ORANGE_COLOR // Warning - 80-100%
            else -> RED_COLOR               // Over goal - 100%+
        }
    }
    
    /**
     * Gets improvement color for percentage changes
     * @param isImprovement True if the change represents an improvement (decrease)
     * @return Green for improvements, Red for regressions
     */
    fun getImprovementColor(isImprovement: Boolean): Color {
        return if (isImprovement) GREEN_COLOR else RED_COLOR
    }
}
