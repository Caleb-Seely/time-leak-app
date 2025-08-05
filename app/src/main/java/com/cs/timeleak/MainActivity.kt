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
                    var showIntroPage by remember { mutableStateOf(true) }
                    var showOnboarding by remember {
                        mutableStateOf(
                            !UsageStatsPermissionChecker.hasUsageAccessPermission(this)
                        )
                    }

                    // Listen for authentication state changes
                    LaunchedEffect(Unit) {
                        auth.addAuthStateListener { firebaseAuth ->
                            val user = firebaseAuth.currentUser
                            isAuthenticated = user != null
                            Log.d(TAG, "Auth state changed: user=${user?.phoneNumber}, authenticated=$isAuthenticated")
                        }
                    }

                    when {
                        showIntroPage -> {
                            IntroPage(
                                onContinue = {
                                    showIntroPage = false
                                }
                            )
                        }
                        !isAuthenticated -> {
                            // Always sign out before showing AuthScreen to prevent auto-login as previous/test user
                            LaunchedEffect(Unit) {
                                FirebaseAuth.getInstance().signOut()
                                UserPrefs.clear(applicationContext)
                                Log.d(TAG, "Signed out any previous user before showing AuthScreen")
                            }
                            AuthScreen(
                                onAuthSuccess = {
                                    isAuthenticated = true
                                    // Schedule immediate sync after successful authentication (only for new users)
                                    if (!hasScheduledSync) {
                                        SyncScheduler.scheduleImmediateSync(applicationContext)
                                        hasScheduledSync = true
                                    }
                                },
                                viewModel = otpViewModel!!
                            )
                        }
                        showOnboarding -> {
                            OnboardingScreen(
                                onPermissionGranted = {
                                    Log.d(TAG, "Onboarding permission granted callback - setting showOnboarding to false")
                                    showOnboarding = false
                                }
                            )
                        }
                        else -> {
                            DashboardScreen(
                                viewModel = otpViewModel!!,
                                onSignOut = {
                                    otpViewModel!!.signOut(applicationContext)
                                    isAuthenticated = false
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