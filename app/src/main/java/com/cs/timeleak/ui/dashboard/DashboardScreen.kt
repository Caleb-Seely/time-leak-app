package com.cs.timeleak.ui.dashboard

// Compose UI imports for animations, layouts, and components
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset

// Data models and repositories
import com.cs.timeleak.data.DailyUsage
import com.cs.timeleak.data.FirestoreRepository
import com.cs.timeleak.data.UsageStatsRepository
import com.cs.timeleak.data.UserPrefs

// Authentication and navigation
import com.cs.timeleak.ui.auth.AuthViewModel

// Dashboard-specific components and utilities
import com.cs.timeleak.ui.dashboard.components.*
import com.cs.timeleak.ui.dashboard.utils.ColorUtils.getScreenTimeColor
import com.cs.timeleak.ui.dashboard.utils.TimeUtils.formatDuration

// Coroutines for async operations
import kotlinx.coroutines.launch

/**
 * Main dashboard screen displaying today's usage statistics, goals, and app breakdown.
 * 
 * This screen serves as the primary interface for users to:
 * - View their daily screen time usage
 * - Monitor progress against their daily goals
 * - See breakdown of social media and entertainment usage
 * - Track 30-day rolling averages
 * - Browse detailed app usage statistics
 * - Access debug information (when enabled)
 * 
 * @param viewModel Authentication view model for user management
 * @param onSignOut Callback function triggered when user signs out
 */
@Composable
fun DashboardScreen(
    viewModel: AuthViewModel,
    onSignOut: () -> Unit = {}
) {
    // Context and repository setup
    val context = LocalContext.current
    val repository = remember { UsageStatsRepository(context) }
    
    // State management for dashboard data
    var usageStats by remember { mutableStateOf<DailyUsage?>(null) }
    var monthlyAverage by remember { mutableStateOf<Long?>(null) }
    
    // UI state management
    var isSyncing by remember { mutableStateOf(false) }  // Tracks sync operation status
    var syncMessage by remember { mutableStateOf<String?>(null) }  // Displays sync feedback
    var showDebugInfo by remember { mutableStateOf(false) }  // Toggle for debug panel
    var showGoalModal by remember { mutableStateOf(false) }  // Controls goal editing modal
    
    // Goal and user preferences
    var goalTimeMillis by remember { mutableStateOf(UserPrefs.getGoalTime(context)) }
    
    // Legacy state variables (kept for potential future features)
    var integrityTestRunning by remember { mutableStateOf(false) }
    var integrityTestResult by remember { mutableStateOf<String?>(null) }
    
    // Coroutine scope for async operations
    val scope = rememberCoroutineScope()

    // Initialize data on screen load
    LaunchedEffect(Unit) {
        scope.launch {
            // Fetch today's usage statistics
            usageStats = repository.getTodayUsageStats()
            // Calculate 30-day rolling average for trend analysis
            monthlyAverage = repository.getLast30DayAverageScreenTime()
        }
    }

    // Main content container with gradient background
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
        // === SYNC STATUS MESSAGE ===
        // Displays success/error messages from sync operations with animation
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
                        // Dynamic color based on message type
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

        // === HEADER SECTION ===
        // Contains screen title, debug toggle, and sign-out button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Screen title
            Text(
                text = "Today's Usage",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Action buttons container
            Row {
                // Debug information toggle
                IconButton(
                    onClick = { showDebugInfo = !showDebugInfo }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Debug Info",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                
                // Sign out button
//                IconButton(
//                    onClick = {
//                        onSignOut()
//                    }
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.ExitToApp,
//                        contentDescription = "Sign Out",
//                        tint = MaterialTheme.colorScheme.error
//                    )
//                }
            }
        }

        // === DEBUG INFORMATION PANEL ===
        // Collapsible panel showing system info, sync status, and manual controls
        if (showDebugInfo) {
            DebugInfoCard(
                monthlyAverage = monthlyAverage,
                onSyncMessage = { message -> syncMessage = message }
            )
        }

        // === MAIN CONTENT ===
        // Display usage data if available, otherwise show loading indicator
        usageStats?.let { stats ->
            // === USAGE SUMMARY CARD ===
            // Main card containing screen time, category breakdowns, averages, and goal tracking
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
                    // === TOTAL SCREEN TIME DISPLAY ===
                    // Large, prominently displayed total with color-coding based on goal progress
                    Column {
                        Text(
                            text = formatDuration(stats.totalScreenTimeMillis),
                            style = MaterialTheme.typography.headlineLarge,
                            color = getScreenTimeColor(stats.totalScreenTimeMillis, goalTimeMillis)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            // === CATEGORY BREAKDOWN PILLS ===
                            // Visual breakdown of social media and entertainment usage
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Social Media Usage Pill
                                Row(
                                    modifier = Modifier
                                        .offset(x = (-6).dp) // Visual adjustment for alignment
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Socials:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        text = formatDuration(stats.socialMediaTimeMillis),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                
                                // Entertainment/Streaming Usage Pill
                                Row(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Streaming:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        text = formatDuration(stats.entertainmentTimeMillis),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // === 30-DAY TREND SECTION ===
                            // Shows rolling average with improvement/regression indicators
                            monthlyAverage?.let { average ->
                                val baselineScreenTime = UserPrefs.getBaselineScreenTime(context)

                                AverageSection(
                                    average = average,
                                    baseline = baselineScreenTime
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // === GOAL TRACKING SECTION ===
                    // Progress bar, remaining time, and goal editing functionality
                    GoalSection(
                        currentUsage = stats.totalScreenTimeMillis,
                        goalTime = goalTimeMillis,
                        onEditGoal = { showGoalModal = true }
                    )
                }
            }
            
            // === TOP APPS SECTION ===
            // Detailed breakdown of individual app usage with expandable details
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Top Apps",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Scrollable list of apps with usage details
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
        } ?: 
        // === LOADING STATE ===
        // Displayed while usage data is being fetched
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    
    // === GOAL EDITING MODAL ===
    // Modal dialog for setting/updating daily screen time goals
    if (showGoalModal) {
        GoalEditModal(
            currentGoal = goalTimeMillis,
            monthlyAverage = monthlyAverage,
            onGoalSaved = { newGoal ->
                goalTimeMillis = newGoal
                UserPrefs.saveGoalTime(context, newGoal)
                showGoalModal = false
            },
            onDismiss = { showGoalModal = false }
        )
    }
}