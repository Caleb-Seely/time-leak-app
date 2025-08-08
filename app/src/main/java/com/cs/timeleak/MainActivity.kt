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
import com.cs.timeleak.ui.onboarding.screens.IntroPage
import com.cs.timeleak.ui.onboarding.screens.HowItWorksPage
import com.cs.timeleak.ui.onboarding.screens.AuthScreen
import com.cs.timeleak.ui.onboarding.screens.PermissionsScreen
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
                    
                    // Check authentication and permission states
                    val authenticationState = remember {
                        derivedStateOf {
                            val isAuthenticated = auth.currentUser != null
                            val hasPermissions = UsageStatsPermissionChecker.hasUsageAccessPermission(this@MainActivity)
                            
                            when {
                                isAuthenticated && hasPermissions -> "dashboard" // Skip to dashboard
                                isAuthenticated && !hasPermissions -> "permissions" // Skip to permissions only
                                else -> "onboarding" // Full onboarding flow
                            }
                        }
                    }.value
                    
                    // Debug navigation state - determined by authentication state
                    var debugStep by remember { 
                        mutableStateOf(
                            when (authenticationState) {
                                "dashboard" -> 4 // Skip to dashboard
                                "permissions" -> 3 // Skip to permissions screen
                                else -> 0 // Start at intro
                            }
                        ) 
                    }
                    
                    // Update debug step when authentication or permission state changes
                    LaunchedEffect(authenticationState) {
                        when (authenticationState) {
                            "dashboard" -> {
                                Log.d(TAG, "User is authenticated and has permissions, skipping to dashboard")
                                debugStep = 4
                            }
                            "permissions" -> {
                                Log.d(TAG, "User is authenticated but missing permissions, skipping to permissions screen")
                                debugStep = 3
                            }
                            "onboarding" -> {
                                Log.d(TAG, "User needs full onboarding, starting from intro")
                                debugStep = 0
                            }
                        }
                    }
                    
                    // Screen display logic based on current state
                    
                    // Override states with debug navigation
                    val actualShowIntroPage = debugStep == 0 && authenticationState == "onboarding"
                    val actualShowHowItWorks = debugStep == 1 && authenticationState == "onboarding"
                    val actualShowAuth = debugStep == 2 && authenticationState == "onboarding"
                    val actualShowOnboarding = debugStep == 3 // Show permissions for both "permissions" and "onboarding" states

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
                            IntroPage(
                                onAction = { action ->
                                    when (action) {
                                        OnboardingAction.NextStep -> debugStep = 1
                                        else -> { /* Handle other actions if needed */ }
                                    }
                                }
                            )
                        }
                        actualShowHowItWorks -> {
                            HowItWorksPage(
                                onAction = { action ->
                                    when (action) {
                                        OnboardingAction.NextStep -> debugStep = 2
                                        else -> { /* Handle other actions if needed */ }
                                    }
                                }
                            )
                        }
                        actualShowAuth -> {
                            AuthScreen(
                                onAction = { action ->
                                    when (action) {
                                        OnboardingAction.NextStep -> debugStep = 3
                                        else -> { /* Handle other actions if needed */ }
                                    }
                                },
                                authViewModel = otpViewModel!!
                            )
                        }
                        actualShowOnboarding -> {
                            PermissionsScreen(
                                onAction = { action ->
                                    when (action) {
                                        OnboardingAction.NextStep -> {
                                            Log.d(TAG, "Moving to dashboard")
                                            debugStep = 4
                                        }
                                        else -> { /* Handle other actions if needed */ }
                                    }
                                },
                                permissionViewModel = permissionViewModel
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
                                val currentAuthState = if (auth.currentUser != null) "authenticated" else "not authenticated"
                                Log.d(TAG, "ON_RESUME - hasUsageAccess: $hasUsageAccess, user: $currentAuthState")
                                
                                // Update permission state in ViewModel - this triggers the UI update
                                permissionViewModel.checkPermissionStatus(this@MainActivity)
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