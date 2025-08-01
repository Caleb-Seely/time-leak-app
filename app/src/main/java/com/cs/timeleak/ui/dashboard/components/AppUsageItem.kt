package com.cs.timeleak.ui.dashboard.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cs.timeleak.data.AppUsage
import com.cs.timeleak.ui.dashboard.utils.TimeUtils.formatDateTime
import com.cs.timeleak.ui.dashboard.utils.TimeUtils.formatDuration

/**
 * Expandable item displaying app usage information with detailed breakdown.
 * 
 * The item initially shows the app's name and total usage time. Upon expansion,
 * additional details such as launch count, last used timestamp, and detailed
 * usage time become visible. The item supports smooth expand/collapse animations.
 * 
 * @param app AppUsage data to display
 */
@Composable
fun AppUsageItem(app: AppUsage) {
    // Track expansion state
    var expanded by remember { mutableStateOf(false) }
    
    // Main container for the app usage item
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }  // Toggle expansion on click
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Header row showing app name and usage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App name
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Display usage time and toggle icon
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(app.usageTimeMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                // Expand/collapse icon
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        
        // Expandable details section
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            // Detailed information container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // App-specific details as key-value pairs
                DetailRow("Launch Count", "${app.launchCount} times")
                DetailRow("Last Used", formatDateTime(app.lastTimeUsed))
                DetailRow("Usage Time", formatDuration(app.usageTimeMillis))
            }
        }
    }
}
