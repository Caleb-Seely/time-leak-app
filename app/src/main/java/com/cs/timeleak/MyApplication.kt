package com.cs.timeleak

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import androidx.work.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await


class MyApplication : Application(), Configuration.Provider {
    private val applicationScope = CoroutineScope(Dispatchers.Default)
    private val TAG = "MyApplication"

    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized successfully")
            
            // Initialize anonymous auth
            applicationScope.launch {
                try {
                    FirebaseAuth.getInstance().signInAnonymously().await()
                    Log.d(TAG, "Anonymous authentication successful")
                } catch (e: Exception) {
                    Log.e(TAG, "Authentication failed: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed: ${e.message}", e)
        }
    }





    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setDefaultProcessName("com.cs.timeleak")
            .build()
} 