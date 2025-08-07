package com.cs.timeleak.ui.onboarding.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cs.timeleak.ui.onboarding.components.*
import com.cs.timeleak.ui.onboarding.error.*
import com.cs.timeleak.ui.onboarding.model.*
import com.cs.timeleak.ui.onboarding.permission.*
import com.cs.timeleak.ui.onboarding.provider.OnboardingContentProvider

/**
 * PermissionsScreen using permission management architecture.
 * Uses PermissionViewModel and permission abstraction.
 */
@Composable
fun PermissionsScreen(
    onAction: (OnboardingAction) -> Unit,
    permissionViewModel: PermissionViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionState by permissionViewModel.permissionState.collectAsState()
    val errorManager = rememberErrorStateManager()
    val loadingManager = rememberLoadingStateManager()
    
    // Check permission status on composition and when returning to screen
    LaunchedEffect(Unit) {
        permissionViewModel.checkPermissionStatus(context)
    }
    
    // Re-check permission when app returns from settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                android.util.Log.d("PermissionsScreen", "App resumed, checking permission status")
                permissionViewModel.checkPermissionStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Handle permission granted - navigate to next step
    LaunchedEffect(permissionState.hasUsagePermission) {
        if (permissionState.hasUsagePermission) {
            onAction(OnboardingAction.NextStep)
        }
    }
    
    val stepContent = OnboardingContentProvider.getPermissionsContent()
    
    OnboardingPageLayout(
        modifier = modifier,
        header = {
            OnboardingHeader(step = stepContent)
        },
        content = {
            PermissionsScreenContent(
                stepContent = stepContent,
                permissionState = permissionState,
                errorManager = errorManager,
                loadingManager = loadingManager,
                onRequestPermission = {
                    permissionViewModel.requestUsagePermission(context)
                },
                onAction = onAction
            )
        },
        bottomAction = {
            PermissionsScreenBottomAction(
                stepContent = stepContent,
                permissionState = permissionState,
                onRequestPermission = {
                    permissionViewModel.requestUsagePermission(context)
                },
                onAction = onAction
            )
        }
    )
}

/**
 * Content section for permissions screen
 */
@Composable
private fun ColumnScope.PermissionsScreenContent(
    stepContent: OnboardingStep,
    permissionState: PermissionState,
    errorManager: ErrorStateManager,
    loadingManager: LoadingStateManager,
    onRequestPermission: () -> Unit,
    onAction: (OnboardingAction) -> Unit
) {
    val currentError by errorManager.currentError.collectAsState()
    val isLoading by loadingManager.isLoading.collectAsState()
    val loadingMessage by loadingManager.loadingMessage.collectAsState()
    
    // Show error if present
    currentError?.let { error ->
        OnboardingErrorCard(
            error = error,
            onRetry = { 
                errorManager.clearError()
                onRequestPermission()
            },
            onDismiss = { errorManager.clearError() }
        )
    }
    
    // Loading state
    LoadingStateOverlay(
        isLoading = isLoading,
        message = loadingMessage
    )
    
    // Main content
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Explanation text
        val infoTexts = stepContent.content.filter { it.type == ContentType.INFO_TEXT }
        infoTexts.firstOrNull()?.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )
        }
        
        // Privacy explanation
        val privacyPoints = stepContent.content.filter { it.type == ContentType.PRIVACY_POINT }
        if (privacyPoints.isNotEmpty()) {
            val privacyTitle = privacyPoints.firstOrNull()?.title
            PrivacyExplanationCard(
                privacyPoints = privacyPoints,
                title = privacyTitle
            )
        }
        
        // Waiting state indicator
        if (permissionState.isWaitingForPermission) {
            WaitingForPermissionIndicator()
        }
    }
}

/**
 * Waiting for permission indicator
 */
@Composable
private fun WaitingForPermissionIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = "Waiting for permission...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Bottom action section for permissions screen
 */
@Composable
private fun PermissionsScreenBottomAction(
    stepContent: OnboardingStep,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onAction: (OnboardingAction) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status text
        if (permissionState.isWaitingForPermission) {
            // Show simple message while waiting
            Text(
                text = "Please grant permission in Settings",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Show last info text
            val lastInfoText = stepContent.content.filter { it.type == ContentType.INFO_TEXT }.lastOrNull()
            lastInfoText?.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Main action button
        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stepContent.buttonText,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * Debug wrapper version with navigation controls
 */
@Composable
fun PermissionsScreenWithDebug(
    onAction: (OnboardingAction) -> Unit,
    permissionViewModel: PermissionViewModel,
    onBack: () -> Unit,
    onForward: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    currentStep: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        PermissionsScreen(
            onAction = onAction,
            permissionViewModel = permissionViewModel
        )
        
        // Debug navigation overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DEBUG: $currentStep",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Debug Back",
                        tint = if (canGoBack) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
                
                IconButton(
                    onClick = onForward,
                    enabled = canGoForward
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Debug Forward",
                        tint = if (canGoForward) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
            }
        }
    }
}
