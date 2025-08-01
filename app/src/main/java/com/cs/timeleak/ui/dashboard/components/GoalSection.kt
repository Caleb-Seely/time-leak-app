package com.cs.timeleak.ui.dashboard.components

// Compose UI imports
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Dashboard utilities
import com.cs.timeleak.ui.dashboard.utils.ColorUtils.getProgressColor
import com.cs.timeleak.ui.dashboard.utils.TimeUtils.formatDuration

/**
 * Goal tracking section component displaying daily goal progress.
 * 
 * This component shows:
 * - Current daily goal time
 * - Progress towards goal (percentage and remaining/over time)
 * - Visual progress bar with color-coded status
 * - Edit goal functionality
 * 
 * The component uses a traffic light color system:
 * - Green: Under 80% of goal (good progress)
 * - Orange: 80-100% of goal (approaching limit)
 * - Red: Over 100% of goal (exceeded limit)
 * 
 * @param currentUsage Current screen time usage in milliseconds
 * @param goalTime Daily goal time in milliseconds
 * @param onEditGoal Callback triggered when user wants to edit their goal
 */
@Composable
fun GoalSection(
    currentUsage: Long,
    goalTime: Long,
    onEditGoal: () -> Unit
) {
    // === PROGRESS CALCULATIONS ===
    // Calculate progress as a ratio, capped at 100% for visual display
    val progress = (currentUsage.toFloat() / goalTime.toFloat()).coerceAtMost(1.0f)
    val isOverGoal = currentUsage > goalTime
    // Calculate time remaining (or time over goal if exceeded)
    val remainingTime = if (isOverGoal) currentUsage - goalTime else goalTime - currentUsage
    val percentage = (progress * 100).toInt()
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // === GOAL HEADER ROW ===
        // Shows goal time and edit button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Goal time display
            Text(
                text = "Daily Goal: ${formatDuration(goalTime)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Edit goal button with icon and text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onEditGoal() }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Goal",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Edit Goal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // === PROGRESS DETAILS ROW ===
        // Shows remaining/over time and percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Remaining or over time display
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isOverGoal) "Over: " else "Remaining: ",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(remainingTime),
                    style = MaterialTheme.typography.titleSmall,
                    color = getProgressColor(progress) // Color-coded based on progress
                )
            }
            
            // Percentage display
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.titleSmall,
                color = getProgressColor(progress) // Matches the remaining time color
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // === VISUAL PROGRESS BAR ===
        // Background container for progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant) // Light background
        ) {
            // Filled progress indicator with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress) // Width based on progress percentage
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                getProgressColor(progress), // Solid color on left
                                getProgressColor(progress).copy(alpha = 0.7f) // Faded on right
                            )
                        )
                    )
            )
        }
    }
}
