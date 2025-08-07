package com.cs.timeleak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.cs.timeleak.ui.dashboard.DashboardScreen
import com.cs.timeleak.ui.onboarding.screens.IntroPageWithDebug
import com.cs.timeleak.ui.onboarding.screens.HowItWorksPageWithDebug
import com.cs.timeleak.ui.onboarding.screens.AuthScreenWithDebug
import com.cs.timeleak.ui.onboarding.screens.PermissionsScreenWithDebug
import com.cs.timeleak.utils.UsageStatsPermissionChecker
import com.cs.timeleak.utils.BatteryOptimizationHelper
import com.cs.timeleak.utils.FirebaseDebugHelper
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import com.cs.timeleak.ui.auth.AuthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs.timeleak.data.UserPrefs
import com.cs.timeleak.utils.SyncScheduler
import com.cs.timeleak.utils.DailyMidnightScheduler
import com.cs.timeleak.integrity.IntegrityChecker
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.cs.timeleak.ui.onboarding.permission.PermissionViewModel
import com.cs.timeleak.ui.onboarding.model.OnboardingAction

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    private var otpViewModel: AuthViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log Firebase configuration for debugging
        FirebaseDebugHelper.logFirebaseConfiguration(this)
        
        // Schedule reliable daily midnight sync (11:59 PM)
        DailyMidnightScheduler.initialize(applicationContext)
        
        // Initialize Google Play Integrity API
        IntegrityChecker.initialize(applicationContext)
        
        // Log initial authentication state
        val auth = FirebaseAuth.getInstance()
        Log.d(TAG, "App startup - Current user: ${auth.currentUser?.phoneNumber ?: "None"}")
        
        otpViewModel = AuthViewModel()
        val permissionViewModel = PermissionViewModel()
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val auth = FirebaseAuth.getInstance()
                    var isAuthenticated by remember {
                        mutableStateOf(auth.currentUser != null)
                    }
                    var hasScheduledSync by remember { mutableStateOf(false) }
                    
                    // Debug navigation state
                    var debugStep by remember { mutableStateOf(0) } // 0=intro, 1=how-it-works, 2=auth, 3=permissions
                    
                    // Original states (temporarily overridden by debug)
                    var showIntroPage by remember { mutableStateOf(true) }
                    var showOnboarding by remember {
                        mutableStateOf(
                            !UsageStatsPermissionChecker.hasUsageAccessPermission(this)
                        )
                    }
                    
                    // Override states with debug navigation
                    val actualShowIntroPage = debugStep == 0
                    val actualShowHowItWorks = debugStep == 1
                    val actualShowAuth = debugStep == 2
                    val actualShowOnboarding = debugStep == 3

                    // Listen for authentication state changes
                    LaunchedEffect(Unit) {
                        auth.addAuthStateListener { firebaseAuth ->
                            val user = firebaseAuth.currentUser
                            isAuthenticated = user != null
                            Log.d(TAG, "Auth state changed: user=${user?.phoneNumber}, authenticated=$isAuthenticated")
                        }
                    }

                    when {
                        actualShowIntroPage -> {
                            IntroPageWithDebug(
                                onAction = { action ->
                                    when (action) {
                                        OnboardingAction.NextStep -> debugStep = 1
                                        else -> { /* Handle other actions if needed */ }
                                    }
                                },
                                onBack = { /* No back from intro */ },
                                onForward = { debugStep = 1 },
                                canGoBack = false,
                                canGoForward = true,
                                currentStep = "Intro (1/4)"
                            )
                        }
                        actualShowHowItWorks -> {
                            HowItWorksPageWithDebug(
                                onAction = { action ->
                                    when (action) {
                                        OnboardingAction.NextStep -> debugStep = 2
                                        else -> { /* Handle other actions if needed */ }
                                    }
                                },
                                onBack = { debugStep = 0 },
                                onForward = { debugStep = 2 },
                                canGoBack = true,
                                canGoForward = true,
                                currentStep = "How It Works (2/4)"
                            )
                        }
                        actualShowAuth -> {
                            AuthScreenWithDebug(
                                onAction = { action ->
                                    when (action) {
                                        OnboardingAction.NextStep -> debugStep = 3
                                        else -> { /* Handle other actions if needed */ }
                                    }
                                },
                                authViewModel = otpViewModel!!,
                                onBack = { debugStep = 1 },
                                onForward = { debugStep = 3 },
                                canGoBack = true,
                                canGoForward = true,
                                currentStep = "Auth (3/4)"
                            )
                        }
                        actualShowOnboarding -> {
                            PermissionsScreenWithDebug(
                                onAction = { action ->
                                    when (action) {
                                        OnboardingAction.NextStep -> {
                                            Log.d(TAG, "Debug: Moving to dashboard")
                                            debugStep = 4 // Move to dashboard or reset
                                        }
                                        else -> { /* Handle other actions if needed */ }
                                    }
                                },
                                permissionViewModel = permissionViewModel,
                                onBack = { debugStep = 2 },
                                onForward = { 
                                    Log.d(TAG, "Debug: Skipping to dashboard") 
                                    debugStep = 4
                                },
                                canGoBack = true,
                                canGoForward = true,
                                currentStep = "Permissions (4/4)"
                            )
                        }
                        else -> {
                            DashboardScreen(
                                viewModel = otpViewModel!!,
                                onSignOut = {
                                    otpViewModel!!.signOut(applicationContext)
                                    isAuthenticated = false
                                    debugStep = 0 // Reset to intro for debugging
                                }
                            )
                        }
                    }

                    // Simple lifecycle handling for permission detection
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                Log.d(TAG, "ON_RESUME - App returned to foreground")
                                val hasUsageAccess = UsageStatsPermissionChecker.hasUsageAccessPermission(this@MainActivity)
                                Log.d(TAG, "ON_RESUME - hasUsageAccess: $hasUsageAccess, current showOnboarding: $showOnboarding")
                                
                                // Update permission state in ViewModel - this triggers the UI update
                                permissionViewModel.checkPermissionStatus(this@MainActivity)
                                
                                showOnboarding = !hasUsageAccess
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                }
            }
        }
    }
    

} 