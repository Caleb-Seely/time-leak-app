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
import com.cs.timeleak.utils.UsageStatsPermissionChecker
import com.cs.timeleak.utils.BatteryOptimizationHelper
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import com.cs.timeleak.ui.auth.AuthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs.timeleak.data.UserPrefs
import com.cs.timeleak.utils.SyncScheduler

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    private var otpViewModel: AuthViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                        mutableStateOf(
                            auth.currentUser != null ||
                            UserPrefs.getPhone(applicationContext) != null
                        )
                    }
                    var hasScheduledSync by remember { mutableStateOf(false) }
                    var showOnboarding by remember {
                        mutableStateOf(
                            !UsageStatsPermissionChecker.hasUsageAccessPermission(this) ||
                            !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
                        )
                    }

                    when {
                        !isAuthenticated -> {
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
                            
                            // Check if permissions are revoked while app is running
                            LaunchedEffect(Unit) {
                                if (!UsageStatsPermissionChecker.hasUsageAccessPermission(this@MainActivity) ||
                                    !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this@MainActivity)) {
                                    showOnboarding = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    

} 