package com.cs.timeleak.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cs.timeleak.data.AppUsage
import com.cs.timeleak.data.DailyUsage
import com.cs.timeleak.data.UsageStatsRepository
import com.cs.timeleak.data.FirestoreRepository
import com.cs.timeleak.utils.UsageStatsPermissionChecker
import com.cs.timeleak.utils.BatteryOptimizationHelper
import com.cs.timeleak.utils.SyncScheduler
import com.cs.timeleak.utils.WorkManagerDiagnostics
import com.cs.timeleak.utils.DailyMidnightScheduler
import com.cs.timeleak.ui.auth.AuthViewModel
import androidx.work.WorkManager
import androidx.work.WorkInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import com.cs.timeleak.data.UserPrefs
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.work.OneTimeWorkRequestBuilder
import com.cs.timeleak.sync.UsageSyncWorker
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cs.timeleak.integrity.IntegrityChecker

@Composable
fun DashboardScreen(
    viewModel: AuthViewModel,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { UsageStatsRepository(context) }
    val firestoreRepository = remember { FirestoreRepository() }
    var usageStats by remember { mutableStateOf<DailyUsage?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var showDebugInfo by remember { mutableStateOf(false) }
    var showGoalModal by remember { mutableStateOf(false) }
    var goalTimeMillis by remember { mutableStateOf(UserPrefs.getGoalTime(context)) }
    var integrityTestRunning by remember { mutableStateOf(false) }
    var integrityTestResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            usageStats = repository.getTodayUsageStats()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        // Sync message at the very top
        syncMessage?.let { message ->
            AnimatedVisibility(
                visible = syncMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp, top = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.startsWith("Successfully")) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.startsWith("Successfully")) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Header with debug toggle and sign out
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Usage",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Row {
                IconButton(
                    onClick = { showDebugInfo = !showDebugInfo }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Debug Info",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                
                IconButton(
                    onClick = {
                        onSignOut()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Sign Out",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Debug Information
        if (showDebugInfo) {
            DebugInfoCard()
        }

        usageStats?.let { stats ->
            // Row for Total Screen Time and Sync Button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "Total Screen Time:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatDuration(stats.totalScreenTimeMillis),
                            style = MaterialTheme.typography.headlineMedium,
                            color = getScreenTimeColor(stats.totalScreenTimeMillis, goalTimeMillis)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Goal Section
                    GoalSection(
                        currentUsage = stats.totalScreenTimeMillis,
                        goalTime = goalTimeMillis,
                        onEditGoal = { showGoalModal = true }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Social Media:",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatDuration(stats.socialMediaTimeMillis),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Entertainment:",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatDuration(stats.entertainmentTimeMillis),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    syncMessage = null
                                    try {
                                        val stats = repository.getLast24HoursUsageStats()
                                        if (stats != null) {
                                            firestoreRepository.uploadUsageData(context, stats)
                                            syncMessage = "Successfully synced to database!"
                                        } else {
                                            syncMessage = "No usage data available"
                                        }
                                    } catch (e: Exception) {
                                        syncMessage = "Sync failed: ${e.message}"
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isSyncing) "Syncing..." else "Sync", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Top Apps",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                LazyColumn {
                    items(stats.topApps) { app ->
                        AppUsageItem(app)
                        Divider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    
    // Goal Edit Modal
    if (showGoalModal) {
        GoalEditModal(
            currentGoal = goalTimeMillis,
            onGoalSaved = { newGoal ->
                goalTimeMillis = newGoal
                UserPrefs.saveGoalTime(context, newGoal)
                showGoalModal = false
            },
            onDismiss = { showGoalModal = false }
        )
    }
}

@Composable
private fun GoalSection(
    currentUsage: Long,
    goalTime: Long,
    onEditGoal: () -> Unit
) {
    val progress = (currentUsage.toFloat() / goalTime.toFloat()).coerceAtMost(1.0f)
    val isOverGoal = currentUsage > goalTime
    val remainingTime = if (isOverGoal) currentUsage - goalTime else goalTime - currentUsage
    val percentage = (progress * 100).toInt()
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daily Goal",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onEditGoal,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Goal",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Edit Goal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                getProgressColor(progress),
                                getProgressColor(progress).copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isOverGoal) {
                    "${formatDuration(remainingTime)} over goal"
                } else {
                    "${formatDuration(remainingTime)} remaining"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = getProgressColor(progress)
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.titleSmall,
                color = getProgressColor(progress)
            )
        }
    }
}

@Composable
private fun GoalEditModal(
    currentGoal: Long,
    onGoalSaved: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var hours by remember { mutableStateOf((currentGoal / (1000 * 60 * 60)).toString()) }
    var minutes by remember { mutableStateOf(((currentGoal % (1000 * 60 * 60)) / (1000 * 60)).toString()) }
    var hoursError by remember { mutableStateOf<String?>(null) }
    var minutesError by remember { mutableStateOf<String?>(null) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Set Daily Screen Time Goal",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Hours",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { 
                                hours = it.filter { char -> char.isDigit() }
                                hoursError = null
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = hoursError != null,
                            supportingText = hoursError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Minutes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { 
                                minutes = it.filter { char -> char.isDigit() }
                                minutesError = null
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = minutesError != null,
                            supportingText = minutesError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            val hoursInt = hours.toIntOrNull() ?: 0
                            val minutesInt = minutes.toIntOrNull() ?: 0
                            
                            if (hoursInt < 0 || hoursInt > 24) {
                                hoursError = "Hours must be 0-24"
                                return@Button
                            }
                            
                            if (minutesInt < 0 || minutesInt > 59) {
                                minutesError = "Minutes must be 0-59"
                                return@Button
                            }
                            
                            if (hoursInt == 0 && minutesInt == 0) {
                                minutesError = "Goal must be greater than 0"
                                return@Button
                            }
                            
                            val totalMillis = (hoursInt * 60 * 60 * 1000L) + (minutesInt * 60 * 1000L)
                            onGoalSaved(totalMillis)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private fun getScreenTimeColor(currentUsage: Long, goalTime: Long): Color {
    val progress = currentUsage.toFloat() / goalTime.toFloat()
    return when {
        progress < 0.8f -> Color(0xFF4CAF50) // Green
        progress < 1.0f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
}

private fun getProgressColor(progress: Float): Color {
    return when {
        progress < 0.8f -> Color(0xFF4CAF50) // Green
        progress < 1.0f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
}

@Composable
private fun DebugInfoCard() {
    val context = LocalContext.current
    var workInfo by remember { mutableStateOf<WorkInfo?>(null) }
    var workName by remember { mutableStateOf<String?>(null) }
    var timeUntilNextSync by remember { mutableStateOf<String?>(null) }
    var scheduledTime by remember { mutableStateOf<Long?>(null) }
    var lastRunTime by remember { mutableStateOf<Long>(0L) }
    val savedPhone = remember { UserPrefs.getPhone(context) }
    val savedUid = remember { UserPrefs.getUid(context) }
    
    fun getLastRunTime(context: Context): Long {
        val prefs = context.getSharedPreferences("usage_sync_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("last_run_time", 0L)
    }
    
    LaunchedEffect(Unit) {
        val workManager = WorkManager.getInstance(context)
        try {
            // Check for daily sync first
            var workInfos = workManager.getWorkInfosForUniqueWork("daily_usage_sync").get()
            workInfo = workInfos.firstOrNull()
            workName = if (workInfo != null) "daily_usage_sync" else null
            
            // If no daily sync, check for immediate sync
            if (workInfo == null) {
                workInfos = workManager.getWorkInfosForUniqueWork("immediate_usage_sync").get()
                workInfo = workInfos.firstOrNull()
                workName = if (workInfo != null) "immediate_usage_sync" else null
            }
            
            if (scheduledTime == null) {
                scheduledTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {}
    }
    LaunchedEffect(Unit) {
        while (true) {
            val workManager = WorkManager.getInstance(context)
            try {
                // Check for daily sync first
                var workInfos = workManager.getWorkInfosForUniqueWork("daily_usage_sync").get()
                workInfo = workInfos.firstOrNull()
                workName = if (workInfo != null) "daily_usage_sync" else null
                
                // If no daily sync, check for immediate sync
                if (workInfo == null) {
                    workInfos = workManager.getWorkInfosForUniqueWork("immediate_usage_sync").get()
                    workInfo = workInfos.firstOrNull()
                    workName = if (workInfo != null) "immediate_usage_sync" else null
                }
            } catch (e: Exception) {}
            lastRunTime = getLastRunTime(context)
            timeUntilNextSync = calculateTimeUntilNextDailySync(lastRunTime, scheduledTime)
            kotlinx.coroutines.delay(1000)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Debug Information",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            DebugRow("Phone Number", savedPhone ?: "Not saved")
            DebugRow("User UID", savedUid ?: "Not saved")
            DebugRow("Usage Permission", if (UsageStatsPermissionChecker.hasUsageAccessPermission(context)) "✓ Granted" else "✗ Denied")
            workInfo?.let { info ->
                val workType = when (workName) {
                    "immediate_usage_sync" -> "Immediate"
                    "daily_usage_sync" -> "Daily"
                    else -> "Unknown"
                }
                DebugRow("Work Status", "${info.state.name} (${workType})")
                DebugRow("Work ID", info.id.toString())
                timeUntilNextSync?.let { time ->
                    DebugRow("Next Daily Sync", time)
                }
            } ?: run {
                DebugRow("Work Status", "Not scheduled")
                DebugRow("Next Daily Sync", "Will be scheduled after first sync")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { 
                        DailyMidnightScheduler.reset(context)
                    }
                ) {
                    Text("Reset Midnight", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { 
                        DailyMidnightScheduler.checkStatus(context)
                    }
                ) {
                    Text("Check Midnight", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}



private fun calculateTimeUntilNextDailySync(
    lastRunTime: Long,
    scheduledTime: Long?
): String {
    val now = System.currentTimeMillis()
    
    // Calculate next 11:59 PM
    val calendar = java.util.Calendar.getInstance()
    val targetHour = 23 // 11 PM
    val targetMinute = 59 // 59 minutes
    
    // Set target time to 11:59 PM today
    calendar.set(java.util.Calendar.HOUR_OF_DAY, targetHour)
    calendar.set(java.util.Calendar.MINUTE, targetMinute)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    
    // If it's already past 11:59 PM today, schedule for tomorrow
    if (calendar.timeInMillis <= now) {
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    
    val nextSyncTime = calendar.timeInMillis
    val timeRemaining = nextSyncTime - now
    
    return formatTimeRemaining(timeRemaining)
}

private fun formatTimeRemaining(millis: Long): String {
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

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun AppUsageItem(app: AppUsage) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(app.usageTimeMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                DetailRow("Launch Count", "${app.launchCount} times")
                DetailRow("Last Used", formatDateTime(app.lastTimeUsed))
                DetailRow("Usage Time", formatDuration(app.usageTimeMillis))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatDuration(millis: Long): String {
    val hours = millis / (1000 * 60 * 60)
    val minutes = (millis % (1000 * 60 * 60)) / (1000 * 60)
    return when {
        hours > 0 -> "$hours hr ${minutes} min"
        else -> "$minutes min"
    }
}

private fun formatDateTime(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
} 