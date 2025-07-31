package com.cs.timeleak.utils

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult

object FirebaseDebugHelper {
    private const val TAG = "FirebaseDebugHelper"
    
    fun checkFirebaseConfiguration(context: Context): String {
        val report = StringBuilder()
        report.append("=== Firebase Configuration Check ===\n\n")
        
        try {
            // Check if Firebase is initialized
            val firebaseApp = FirebaseApp.getInstance()
            report.append("✅ Firebase App initialized: ${firebaseApp.name}\n")
            report.append("   Project ID: ${firebaseApp.options.projectId}\n")
            report.append("   Application ID: ${firebaseApp.options.applicationId}\n\n")
            
        } catch (e: Exception) {
            report.append("❌ Firebase App not initialized: ${e.message}\n\n")
        }
        
        // Check Google Play Services
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        
        report.append("=== Google Play Services ===\n")
        when (resultCode) {
            ConnectionResult.SUCCESS -> {
                report.append("✅ Google Play Services is available\n")
            }
            ConnectionResult.SERVICE_MISSING -> {
                report.append("❌ Google Play Services is missing\n")
            }
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                report.append("⚠️ Google Play Services needs update\n")
            }
            ConnectionResult.SERVICE_DISABLED -> {
                report.append("❌ Google Play Services is disabled\n")
            }
            ConnectionResult.SERVICE_INVALID -> {
                report.append("❌ Google Play Services is invalid\n")
            }
            else -> {
                report.append("❌ Google Play Services error: $resultCode\n")
            }
        }
        report.append("\n")
        
        // Check Firebase Auth
        try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            report.append("=== Firebase Auth ===\n")
            report.append("✅ Firebase Auth initialized\n")
            if (currentUser != null) {
                report.append("✅ User authenticated: ${currentUser.phoneNumber}\n")
                report.append("   UID: ${currentUser.uid}\n")
            } else {
                report.append("ℹ️ No user currently authenticated\n")
            }
        } catch (e: Exception) {
            report.append("❌ Firebase Auth error: ${e.message}\n")
        }
        report.append("\n")
        
        // Check app package and signature
        report.append("=== App Configuration ===\n")
        report.append("Package: ${context.packageName}\n")
        report.append("Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}\n")
        
        return report.toString()
    }
    
    fun logFirebaseConfiguration(context: Context) {
        Log.d(TAG, checkFirebaseConfiguration(context))
    }
    
    fun getDebugInfo(context: Context): String {
        return checkFirebaseConfiguration(context)
    }
} 