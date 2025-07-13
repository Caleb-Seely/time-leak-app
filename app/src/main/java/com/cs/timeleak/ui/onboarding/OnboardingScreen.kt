package com.cs.timeleak.ui.onboarding

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs.timeleak.utils.UsageStatsPermissionChecker
import com.cs.timeleak.utils.BatteryOptimizationHelper
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

@Composable
fun OnboardingScreen(
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(OnboardingStep.USAGE_PERMISSION) }
    var hasUsagePermission by remember { mutableStateOf(false) }
    var hasBatteryOptimization by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check permissions when the screen is first composed
    LaunchedEffect(Unit) {
        hasUsagePermission = UsageStatsPermissionChecker.hasUsageAccessPermission(context)
        hasBatteryOptimization = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        
        if (hasUsagePermission && hasBatteryOptimization) {
            onPermissionGranted()
        } else if (hasUsagePermission && currentStep == OnboardingStep.USAGE_PERMISSION) {
            currentStep = OnboardingStep.BATTERY_OPTIMIZATION
        }
    }

    // Re-check permissions when returning to the foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val newUsagePermission = UsageStatsPermissionChecker.hasUsageAccessPermission(context)
                val newBatteryOptimization = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                
                hasUsagePermission = newUsagePermission
                hasBatteryOptimization = newBatteryOptimization
                
                if (newUsagePermission && newBatteryOptimization) {
                    onPermissionGranted()
                } else if (newUsagePermission && currentStep == OnboardingStep.USAGE_PERMISSION) {
                    currentStep = OnboardingStep.BATTERY_OPTIMIZATION
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = {
                when (currentStep) {
                    OnboardingStep.USAGE_PERMISSION -> 0.5f
                    OnboardingStep.BATTERY_OPTIMIZATION -> 1.0f
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        )

        when (currentStep) {
            OnboardingStep.USAGE_PERMISSION -> {
                UsagePermissionStep(
                    onPermissionGranted = {
                        hasUsagePermission = true
                        currentStep = OnboardingStep.BATTERY_OPTIMIZATION
                    }
                )
            }
            OnboardingStep.BATTERY_OPTIMIZATION -> {
                BatteryOptimizationStep(
                    onOptimizationGranted = {
                        hasBatteryOptimization = true
                        onPermissionGranted()
                    }
                )
            }
        }
    }
}

@Composable
private fun UsagePermissionStep(
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Usage Access Required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Enable Usage Access to track your app activity and accurate screen time stats.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = {
                context.startActivity(UsageStatsPermissionChecker.getUsageAccessSettingsIntent())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Open Settings")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "After granting permission, please return to this app",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BatteryOptimizationStep(
    onOptimizationGranted: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Battery Optimization",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "To ensure accurate tracking and reliable data sync, please disable battery optimization for this app. This is how your time is uploaded behind the scenes.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = {
                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Disable Battery Optimization")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Open Battery Settings")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "After disabling battery optimization, please return to this app",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private enum class OnboardingStep {
    USAGE_PERMISSION,
    BATTERY_OPTIMIZATION
} 