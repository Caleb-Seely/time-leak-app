package com.cs.timeleak.ui.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cs.timeleak.ui.dashboard.utils.TimeUtils.formatDuration
import kotlin.math.abs

/**
 * Component that displays the 30-day average screen time with trend indicators.
 * 
 * Shows the rolling 30-day average and, if available, displays an improvement
 * or regression indicator compared to the user's baseline screen time (captured
 * before app installation). Uses visual arrows and colors to indicate trends:
 * - Green ▼: Improvement (decrease in screen time)
 * - Red ▲: Regression (increase in screen time)
 * 
 * @param average 30-day average screen time in milliseconds
 * @param baseline Baseline screen time for comparison (nullable, from pre-app usage)
 */
@Composable
fun AverageSection(
    average: Long,
    baseline: Long?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "30-day avg: ${formatDuration(average)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Show percentage change if baseline is available and not zero
        baseline?.let { baselineTime ->
            if (baselineTime > 0) {
                val percentageChange = ((average - baselineTime).toFloat() / baselineTime.toFloat() * 100).toInt()
                val absPercentage = abs(percentageChange)
                
                // Only show percentage if it's not zero
                if (absPercentage > 0) {
                    val isImprovement = percentageChange < 0
                    val arrow = if (isImprovement) "▼" else "▲" // Using thick triangle arrows
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$arrow$absPercentage%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isImprovement) Color(0xFF4CAF50) else Color(0xFFF44336) // Green for down, Red for up
                    )
                }
            }
        }
    }
}
