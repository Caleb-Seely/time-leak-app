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
import android.util.Log

@Composable
fun OnboardingScreen(
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val TAG = "OnboardingScreen"
    var hasUsagePermission by remember { mutableStateOf(UsageStatsPermissionChecker.hasUsageAccessPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    Log.d(TAG, "OnboardingScreen composed - initial hasUsagePermission: $hasUsagePermission")

    // Re-check permission when returning to the foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val newPermissionState = UsageStatsPermissionChecker.hasUsageAccessPermission(context)
                Log.d(TAG, "ON_RESUME in OnboardingScreen - old permission: $hasUsagePermission, new permission: $newPermissionState")
                hasUsagePermission = newPermissionState
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Only call onPermissionGranted when permission transitions to true
    LaunchedEffect(hasUsagePermission) {
        Log.d(TAG, "LaunchedEffect triggered - hasUsagePermission: $hasUsagePermission")
        if (hasUsagePermission) {
            Log.d(TAG, "Calling onPermissionGranted()")
            onPermissionGranted()
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
            progress = { 1.0f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        )

        UsagePermissionStep(
            onPermissionGranted = {
                hasUsagePermission = true
            }
        )
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