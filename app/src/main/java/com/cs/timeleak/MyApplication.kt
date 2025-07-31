package com.cs.timeleak

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import androidx.work.Configuration

class MyApplication : Application(), Configuration.Provider {
    private val TAG = "MyApplication"

    override fun onCreate() {
        super.onCreate()
        
        try {
            Log.d(TAG, "=== FIREBASE INITIALIZATION START ===")
            // Initialize Firebase
            val firebaseApp = FirebaseApp.initializeApp(this)
            Log.d(TAG, "✅ Firebase initialized successfully")
            Log.d(TAG, "Firebase app name: ${firebaseApp?.name}")
            Log.d(TAG, "Firebase project ID: ${firebaseApp?.options?.projectId}")
            Log.d(TAG, "Firebase application ID: ${firebaseApp?.options?.applicationId}")
            Log.d(TAG, "Firebase API key: ${firebaseApp?.options?.apiKey?.take(10)}...")
            
            // Initialize Firebase App Check
            initializeAppCheck()

        } catch (e: Exception) {
            Log.e(TAG, "❌ FIREBASE INITIALIZATION FAILED")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception cause: ${e.cause?.message}")
            Log.e(TAG, "Full stack trace:", e)
        }
    }
    
    private fun initializeAppCheck() {
        try {
            Log.d(TAG, "=== FIREBASE APP CHECK INITIALIZATION ===")
            Log.d(TAG, "Build type: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
            Log.d(TAG, "Package name: ${packageName}")
            Log.d(TAG, "Version name: ${BuildConfig.VERSION_NAME}")
            Log.d(TAG, "Version code: ${BuildConfig.VERSION_CODE}")
            
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            Log.d(TAG, "FirebaseAppCheck instance obtained: ${firebaseAppCheck.javaClass.simpleName}")
            
            // Use different providers for debug vs release builds
            if (BuildConfig.DEBUG) {
                // For development: Use debug provider
                Log.d(TAG, "🔧 Initializing App Check with Debug provider for development")
                val debugFactory = DebugAppCheckProviderFactory.getInstance()
                Log.d(TAG, "Debug provider factory: ${debugFactory.javaClass.simpleName}")
                firebaseAppCheck.installAppCheckProviderFactory(debugFactory)
                
                // Get and log debug token
                firebaseAppCheck.getAppCheckToken(false)
                    .addOnSuccessListener { result ->
                        Log.d(TAG, "🔑 App Check DEBUG TOKEN: ${result.token}")
                        Log.d(TAG, "Token expires at: ${java.util.Date(result.expireTimeMillis)}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed to get App Check debug token: ${e.message}", e)
                    }
            } else {
                // For production: Use Play Integrity provider
                Log.d(TAG, "🛡️ Initializing App Check with Play Integrity provider for production")
                val playIntegrityFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
                Log.d(TAG, "Play Integrity provider factory: ${playIntegrityFactory.javaClass.simpleName}")
                firebaseAppCheck.installAppCheckProviderFactory(playIntegrityFactory)
            }
            
            Log.d(TAG, "✅ Firebase App Check initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ FAILED TO INITIALIZE FIREBASE APP CHECK")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception cause: ${e.cause?.message}")
            Log.e(TAG, "Full stack trace:", e)
            // Don't crash the app if App Check fails to initialize
            // The app can still function, but may have limited Firebase access
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setDefaultProcessName("com.cs.timeleak")
            .build()
} 