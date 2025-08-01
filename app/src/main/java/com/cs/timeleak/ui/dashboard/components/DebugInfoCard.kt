package com.cs.timeleak.ui.dashboard.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cs.timeleak.data.FirestoreRepository
import com.cs.timeleak.data.UsageStatsRepository
import com.cs.timeleak.data.UserPrefs
import com.cs.timeleak.ui.dashboard.utils.TimeUtils.calculateTimeUntilNextDailySync
import com.cs.timeleak.ui.dashboard.utils.TimeUtils.formatDuration
import com.cs.timeleak.utils.DailyMidnightScheduler
import com.cs.timeleak.utils.UsageStatsPermissionChecker
import kotlinx.coroutines.launch

/**
 * Debug information card component for development and troubleshooting.
 * 
 * This component provides:
 * - System status information (phone number, UID, permissions)
 * - Background work manager status and scheduling info
 * - Manual sync controls for testing
 * - Baseline screen time information
 * - 30-day average screen time
 * - Real-time countdown to next scheduled sync
 * 
 * Intended for debugging and development purposes, typically hidden
 * from end users in production builds.
 * 
 * @param monthlyAverage Current 30-day average screen time in milliseconds (nullable)
 * @param onSyncMessage Callback to send sync status messages to parent component
 */
@Composable
fun DebugInfoCard(
    monthlyAverage: Long? = null,
    onSyncMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { UsageStatsRepository(context) }
    val firestoreRepository = remember { FirestoreRepository() }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    var workInfo by remember { mutableStateOf<WorkInfo?>(null) }
    var workName by remember { mutableStateOf<String?>(null) }
    var timeUntilNextSync by remember { mutableStateOf<String?>(null) }
    var scheduledTime by remember { mutableStateOf<Long?>(null) }
    var lastRunTime by remember { mutableStateOf<Long>(0L) }
    val savedPhone = remember { UserPrefs.getPhone(context) }
    val savedUid = remember { UserPrefs.getUid(context) }
    val baselineScreenTime = remember { UserPrefs.getBaselineScreenTime(context) }
    
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
            DebugRow("Baseline Screen Time", baselineScreenTime?.let { formatDuration(it) + " (Pre-app)" } ?: "Not captured")
            DebugRow("30-Day Average", monthlyAverage?.let { formatDuration(it) } ?: "Not calculated")
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
                        scope.launch {
                            isSyncing = true
                            try {
                                val stats = repository.getLast24HoursUsageStats()
                                if (stats != null) {
                                    firestoreRepository.uploadUsageData(context, stats)
                                    onSyncMessage("Successfully synced to database!")
                                } else {
                                    onSyncMessage("No usage data available")
                                }
                            } catch (e: Exception) {
                                onSyncMessage("Sync failed: ${e.message}")
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Sync Now", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                Button(
                    onClick = { 
                        DailyMidnightScheduler.reset(context)
                    }
                ) {
                    Text("Reset Midnight Sync", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
