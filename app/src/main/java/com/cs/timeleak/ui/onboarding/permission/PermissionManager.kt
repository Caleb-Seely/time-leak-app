package com.cs.timeleak.ui.onboarding.permission

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.cs.timeleak.utils.UsageStatsPermissionChecker
import com.cs.timeleak.utils.BatteryOptimizationHelper
import com.cs.timeleak.utils.AppForegroundHelper
import com.cs.timeleak.data.UsageStatsRepository

/**
 * Permission management interface for abstracting permission operations
 */
interface PermissionManager {
    suspend fun requestUsagePermission(context: Context): PermissionResult
    suspend fun checkUsagePermission(context: Context): Boolean
    fun startPermissionMonitoring(callback: (Boolean) -> Unit)
    fun stopPermissionMonitoring()
}

/**
 * Implementation of PermissionManager for usage stats permissions
 */
class UsagePermissionManager : PermissionManager {
    private var monitoringJob: Job? = null
    private var monitoringCallback: ((Boolean) -> Unit)? = null
    
    override suspend fun requestUsagePermission(context: Context): PermissionResult {
        return try {
            val hasPermission = checkUsagePermission(context)
            if (hasPermission) {
                PermissionResult.AlreadyGranted
            } else {
                // Use enhanced intent method for better return behavior
                val intent = UsageStatsPermissionChecker.requestUsageAccessWithReturn(context)
                
                // Add logging for debugging
                android.util.Log.d("PermissionManager", "Launching usage access settings with flags: ${intent.flags}")
                android.util.Log.d("PermissionManager", "Intent data: ${intent.data}")
                
                context.startActivity(intent)
                PermissionResult.RequestSent
            }
        } catch (e: Exception) {
            android.util.Log.e("PermissionManager", "Failed to request permission", e)
            PermissionResult.Error("Failed to request permission: ${e.message}")
        }
    }
    
    override suspend fun checkUsagePermission(context: Context): Boolean {
        return try {
            // Use both methods to ensure accurate detection
            val checkerResult = UsageStatsPermissionChecker.hasUsageAccessPermission(context)
            val repositoryResult = UsageStatsRepository(context).hasUsageAccessPermission()
            
            // Log if there's a discrepancy for debugging
            if (checkerResult != repositoryResult) {
                android.util.Log.d("PermissionManager", "Permission check mismatch - Checker: $checkerResult, Repository: $repositoryResult")
            }
            
            // Return true if either method indicates permission is granted
            checkerResult || repositoryResult
        } catch (e: Exception) {
            android.util.Log.e("PermissionManager", "Error checking permission", e)
            false
        }
    }
    
    override fun startPermissionMonitoring(callback: (Boolean) -> Unit) {
        monitoringCallback = callback
        monitoringJob?.cancel()
        // Note: This would need a coroutine scope to be passed in for production use
        // For now, we'll keep the interface simple
    }
    
    override fun stopPermissionMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        monitoringCallback = null
    }
    
    /**
     * Start monitoring with a coroutine scope (internal method)
     */
    fun startMonitoring(
        context: Context,
        scope: kotlinx.coroutines.CoroutineScope,
        interval: Long = 500L,
        callback: (Boolean) -> Unit
    ) {
        monitoringJob?.cancel()
        monitoringCallback = callback
        
        monitoringJob = scope.launch {
            while (isActive) {
                val hasPermission = checkUsagePermission(context)
                callback(hasPermission)
                delay(interval)
            }
        }
    }
}

/**
 * Permission result sealed class
 */
sealed class PermissionResult {
    object AlreadyGranted : PermissionResult()
    object RequestSent : PermissionResult()
    object Denied : PermissionResult()
    data class Error(val message: String) : PermissionResult()
}

/**
 * Permission state data class
 */
data class PermissionState(
    val hasUsagePermission: Boolean = false,
    val isWaitingForPermission: Boolean = false,
    val permissionDeniedCount: Int = 0,
    val lastCheckTime: Long = 0L
)

/**
 * ViewModel for managing permission state in onboarding
 */
class PermissionViewModel(
    private val permissionManager: PermissionManager = UsagePermissionManager()
) : ViewModel() {
    
    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()
    
    private var monitoringJob: Job? = null
    
    /**
     * Check current permission status
     */
    fun checkPermissionStatus(context: Context) {
        viewModelScope.launch {
            val hasPermission = permissionManager.checkUsagePermission(context)
            _permissionState.value = _permissionState.value.copy(
                hasUsagePermission = hasPermission,
                lastCheckTime = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Request usage permission
     */
    fun requestUsagePermission(context: Context) {
        viewModelScope.launch {
            val result = permissionManager.requestUsagePermission(context)
            
            when (result) {
                is PermissionResult.AlreadyGranted -> {
                    _permissionState.value = _permissionState.value.copy(
                        hasUsagePermission = true,
                        isWaitingForPermission = false
                    )
                }
                is PermissionResult.RequestSent -> {
                    _permissionState.value = _permissionState.value.copy(
                        isWaitingForPermission = true
                    )
                    startPermissionMonitoring(context)
                }
                is PermissionResult.Denied -> {
                    _permissionState.value = _permissionState.value.copy(
                        hasUsagePermission = false,
                        isWaitingForPermission = false,
                        permissionDeniedCount = _permissionState.value.permissionDeniedCount + 1
                    )
                }
                is PermissionResult.Error -> {
                    _permissionState.value = _permissionState.value.copy(
                        isWaitingForPermission = false
                    )
                    // Error handling would be done by the UI layer
                }
            }
        }
    }
    
    /**
     * Start monitoring permission changes
     */
    private fun startPermissionMonitoring(context: Context) {
        if (permissionManager is UsagePermissionManager) {
            permissionManager.startMonitoring(
                context = context,
                scope = viewModelScope,
                interval = 250L // Faster polling for immediate detection
            ) { hasPermission ->
                val currentState = _permissionState.value
                if (hasPermission && !currentState.hasUsagePermission) {
                    // Permission was granted
                    android.util.Log.d("PermissionViewModel", "Usage permission granted! Updating state...")
                    
                    // Update state immediately
                    _permissionState.value = currentState.copy(
                        hasUsagePermission = true,
                        isWaitingForPermission = false
                    )
                    stopPermissionMonitoring()
                    
                    // Try to bring app to foreground (but don't rely on it)
                    AppForegroundHelper.bringAppToForeground(context)
                }
            }
        }
    }
    
    /**
     * Stop monitoring permission changes
     */
    fun stopPermissionMonitoring() {
        monitoringJob?.cancel()
        permissionManager.stopPermissionMonitoring()
        _permissionState.value = _permissionState.value.copy(
            isWaitingForPermission = false
        )
    }
    
    /**
     * Reset permission state
     */
    fun reset() {
        stopPermissionMonitoring()
        _permissionState.value = PermissionState()
    }
    
    override fun onCleared() {
        super.onCleared()
        stopPermissionMonitoring()
    }
    
    /**
     * Get debug information about permission state
     */
    fun getDebugInfo(): Map<String, String> {
        val state = _permissionState.value
        return mapOf(
            "has_permission" to state.hasUsagePermission.toString(),
            "waiting_for_permission" to state.isWaitingForPermission.toString(),
            "denied_count" to state.permissionDeniedCount.toString(),
            "last_check" to state.lastCheckTime.toString(),
            "monitoring_active" to (monitoringJob?.isActive == true).toString()
        )
    }
}

/**
 * Factory for creating permission intents
 */
object PermissionIntentFactory {
    
    /**
     * Create usage stats settings intent
     */
    fun createUsageStatsIntent(context: Context): Intent {
        return UsageStatsPermissionChecker.getUsageAccessSettingsIntent(context)
    }
    
    /**
     * Create battery optimization intent
     */
    fun createBatteryOptimizationIntent(context: Context): Intent {
        // Note: This method may not exist in BatteryOptimizationHelper
        // For now, we'll return a generic settings intent
        return Intent(android.provider.Settings.ACTION_SETTINGS)
    }
}

/**
 * Permission state tracker for monitoring permission changes
 */
class PermissionStateTracker(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    private val _permissionState = MutableStateFlow(false)
    val permissionState: StateFlow<Boolean> = _permissionState.asStateFlow()
    
    private var monitoringJob: Job? = null
    
    /**
     * Start monitoring permission state
     */
    fun startMonitoring(interval: Long = 500L) {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            while (isActive) {
                val hasPermission = UsageStatsPermissionChecker.hasUsageAccessPermission(context)
                _permissionState.value = hasPermission
                delay(interval)
            }
        }
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    /**
     * Check permission once
     */
    suspend fun checkOnce(): Boolean {
        val hasPermission = UsageStatsPermissionChecker.hasUsageAccessPermission(context)
        _permissionState.value = hasPermission
        return hasPermission
    }
}

/**
 * Composable to remember permission state tracker
 */
@Composable
fun rememberPermissionStateTracker(context: Context): PermissionStateTracker {
    val scope = rememberCoroutineScope()
    return remember(context) { 
        PermissionStateTracker(context, scope) 
    }
}
