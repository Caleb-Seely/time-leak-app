package com.cs.timeleak.ui.onboarding.error

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cs.timeleak.ui.onboarding.model.OnboardingError
import com.cs.timeleak.ui.onboarding.theme.OnboardingTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Standardized error display card component
 */
@Composable
fun OnboardingErrorCard(
    error: OnboardingError,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = OnboardingTheme.standardCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(OnboardingTheme.standardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Error header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = getErrorIcon(error),
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Error message
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (error.isRetryable && onRetry != null) {
                    Arrangement.spacedBy(8.dp)
                } else {
                    Arrangement.End
                }
            ) {
                if (error.isRetryable && onRetry != null) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = error.actionLabel ?: "Retry",
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
                
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = if (error.isRetryable && onRetry != null) {
                        Modifier.weight(1f)
                    } else {
                        Modifier
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

/**
 * Loading state overlay component
 */
@Composable
fun LoadingStateOverlay(
    isLoading: Boolean,
    message: String?,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = OnboardingTheme.standardCardElevation()
            ) {
                Row(
                    modifier = Modifier.padding(OnboardingTheme.standardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    
                    if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inline error text component for form fields
 */
@Composable
fun InlineErrorText(
    error: String?,
    modifier: Modifier = Modifier
) {
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Start,
            modifier = modifier.fillMaxWidth()
        )
    }
}

/**
 * Success message card component
 */
@Composable
fun SuccessMessageCard(
    message: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = OnboardingTheme.standardCardElevation()
    ) {
        Row(
            modifier = Modifier.padding(OnboardingTheme.standardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Success",
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Warning, // Using Warning as close
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Get appropriate icon for error type
 */
private fun getErrorIcon(error: OnboardingError): ImageVector {
    return when (error) {
        is OnboardingError.NetworkError -> Icons.Filled.Warning
        is OnboardingError.ValidationError -> Icons.Filled.Info
        is OnboardingError.AuthenticationError -> Icons.Filled.Error
        is OnboardingError.PermissionError -> Icons.Filled.Warning
        is OnboardingError.UnknownError -> Icons.Filled.Error
    }
}

/**
 * Loading state manager for coordinating multiple loading operations
 */
class LoadingStateManager {
    private val activeOperations = mutableSetOf<String>()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _loadingMessage = MutableStateFlow<String?>(null)
    val loadingMessage: StateFlow<String?> = _loadingMessage.asStateFlow()
    
    /**
     * Start a loading operation
     */
    fun startLoading(operationId: String, message: String) {
        activeOperations.add(operationId)
        _isLoading.value = true
        _loadingMessage.value = message
    }
    
    /**
     * Stop a loading operation
     */
    fun stopLoading(operationId: String) {
        activeOperations.remove(operationId)
        if (activeOperations.isEmpty()) {
            _isLoading.value = false
            _loadingMessage.value = null
        }
    }
    
    /**
     * Stop all loading operations
     */
    fun stopAllLoading() {
        activeOperations.clear()
        _isLoading.value = false
        _loadingMessage.value = null
    }
    
    /**
     * Check if a specific operation is loading
     */
    fun isOperationLoading(operationId: String): Boolean {
        return activeOperations.contains(operationId)
    }
    
    /**
     * Get debug info about active operations
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "is_loading" to _isLoading.value,
            "loading_message" to (_loadingMessage.value ?: "null"),
            "active_operations" to activeOperations.toList(),
            "operation_count" to activeOperations.size
        )
    }
}

/**
 * Composable to remember loading state manager
 */
@Composable
fun rememberLoadingStateManager(): LoadingStateManager {
    return remember { LoadingStateManager() }
}

/**
 * Error state manager for handling errors in onboarding flow
 */
class ErrorStateManager {
    private val _currentError = MutableStateFlow<OnboardingError?>(null)
    val currentError: StateFlow<OnboardingError?> = _currentError.asStateFlow()
    
    internal val errorHistory = mutableListOf<OnboardingError>()
    
    /**
     * Set current error
     */
    fun setError(error: OnboardingError) {
        errorHistory.add(error)
        _currentError.value = error
    }
    
    /**
     * Clear current error
     */
    fun clearError() {
        _currentError.value = null
    }
    
    /**
     * Get last error of a specific type
     */
    fun <T : OnboardingError> getLastErrorOfType(clazz: Class<T>): T? {
        return errorHistory.filterIsInstance(clazz).lastOrNull()
    }
    
    /**
     * Check if there have been repeated errors of the same type
     */
    fun <T : OnboardingError> getErrorCount(clazz: Class<T>): Int {
        return errorHistory.filterIsInstance(clazz).size
    }
    
    /**
     * Clear error history
     */
    fun clearHistory() {
        errorHistory.clear()
    }
    
    /**
     * Get debug info
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "current_error" to (_currentError.value?.message ?: "null"),
            "error_history_count" to errorHistory.size,
            "error_types" to errorHistory.map { it::class.simpleName }.distinct()
        )
    }
}

/**
 * Composable to remember error state manager
 */
@Composable
fun rememberErrorStateManager(): ErrorStateManager {
    return remember { ErrorStateManager() }
}
