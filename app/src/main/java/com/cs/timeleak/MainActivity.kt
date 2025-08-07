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
import com.cs.timeleak.ui.onboarding.OnboardingScreen
import com.cs.timeleak.ui.auth.AuthScreen
import com.cs.timeleak.ui.onboarding.IntroPage
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward

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
                            com.cs.timeleak.ui.onboarding.IntroPageWithDebugNav(
                                onContinue = {
                                    debugStep = 1
                                },
                                onBack = { /* No back from intro */ },
                                onForward = { debugStep = 1 },
                                canGoBack = false,
                                canGoForward = true,
                                currentStep = "Intro (1/4)"
                            )
                        }
                        actualShowHowItWorks -> {
                            com.cs.timeleak.ui.onboarding.HowItWorksPageWithDebugNav(
                                onContinue = {
                                    debugStep = 2
                                },
                                onBack = { debugStep = 0 },
                                onForward = { debugStep = 2 },
                                canGoBack = true,
                                canGoForward = true,
                                currentStep = "How It Works (2/4)"
                            )
                        }
                        actualShowAuth -> {
                            com.cs.timeleak.ui.auth.AuthScreenWithDebugNav(
                                onAuthSuccess = {
                                    debugStep = 3
                                },
                                onBack = { debugStep = 1 },
                                onForward = { debugStep = 3 },
                                canGoBack = true,
                                canGoForward = true,
                                currentStep = "Auth (3/4)",
                                viewModel = otpViewModel!!
                            )
                        }
                        actualShowOnboarding -> {
                            com.cs.timeleak.ui.onboarding.OnboardingScreenWithDebugNav(
                                onPermissionGranted = {
                                    Log.d(TAG, "Debug: Moving to dashboard")
                                    debugStep = 4 // Move to dashboard or reset
                                },
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

                    // Re-check permission when returning to the foreground
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val hasUsageAccess = UsageStatsPermissionChecker.hasUsageAccessPermission(this@MainActivity)
                                val shouldShowOnboarding = !hasUsageAccess
                                Log.d(TAG, "ON_RESUME - hasUsageAccess: $hasUsageAccess, shouldShowOnboarding: $shouldShowOnboarding, current showOnboarding: $showOnboarding")
                                showOnboarding = shouldShowOnboarding
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