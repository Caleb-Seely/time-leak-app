package com.cs.timeleak.integrity

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import com.google.android.play.core.integrity.model.IntegrityErrorCode
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Handles Google Play Integrity API verification for app security
 */
object IntegrityChecker {
    private const val TAG = "IntegrityChecker"
    
    // You need to get this from Google Play Console -> Release -> App Integrity
    // For testing, you can use a placeholder, but for production you MUST set the real value
    private const val CLOUD_PROJECT_NUMBER = "YOUR_CLOUD_PROJECT_NUMBER" // Replace with actual value
    
    private var integrityManager: IntegrityManager? = null
    
    /**
     * Initialize the Integrity Manager
     */
    fun initialize(context: Context) {
        try {
            integrityManager = IntegrityManagerFactory.create(context.applicationContext)
            Log.d(TAG, "Integrity Manager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Integrity Manager: ${e.message}", e)
        }
    }
    
    /**
     * Request an integrity token for verification
     * @param requestHash A unique hash for this request (can be user ID, action identifier, etc.)
     * @return IntegrityResult with success status and token/error information
     */
    suspend fun requestIntegrityToken(requestHash: String): IntegrityResult {
        Log.d(TAG, "Requesting integrity token for hash: $requestHash")
        
        val manager = integrityManager
        if (manager == null) {
            Log.e(TAG, "Integrity Manager not initialized")
            return IntegrityResult.Error("Integrity Manager not initialized")
        }
        
        return try {
            val request = IntegrityTokenRequest.builder()
                .setCloudProjectNumber(CLOUD_PROJECT_NUMBER.toLongOrNull() ?: 0L)
                .setNonce(requestHash)
                .build()
            
            Log.d(TAG, "Sending integrity token request...")
            val response: IntegrityTokenResponse = manager.requestIntegrityToken(request).await()
            
            val token = response.token()
            if (token.isNullOrEmpty()) {
                Log.w(TAG, "Received empty integrity token")
                IntegrityResult.Error("Empty integrity token received")
            } else {
                Log.d(TAG, "Integrity token received successfully (length: ${token.length})")
                IntegrityResult.Success(token)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request integrity token: ${e.message}", e)
            
            // Parse specific error codes if available
            val errorMessage = when {
                e.message?.contains("NETWORK_ERROR") == true -> "Network error - check internet connection"
                e.message?.contains("PLAY_STORE_NOT_FOUND") == true -> "Google Play Store not available"
                e.message?.contains("APP_NOT_INSTALLED") == true -> "App not installed from Play Store"
                e.message?.contains("PLAY_SERVICES_NOT_FOUND") == true -> "Google Play Services not available"
                e.message?.contains("TOO_MANY_REQUESTS") == true -> "Too many requests - rate limited"
                else -> "Integrity check failed: ${e.message}"
            }
            
            IntegrityResult.Error(errorMessage)
        }
    }
    
    /**
     * Quick integrity check for app startup or critical operations
     */
    suspend fun performQuickIntegrityCheck(context: Context): IntegrityResult {
        val currentTime = System.currentTimeMillis()
        val requestHash = "quick_check_$currentTime"
        
        Log.d(TAG, "Performing quick integrity check...")
        return requestIntegrityToken(requestHash)
    }
    
    /**
     * Integrity check for user authentication
     */
    suspend fun performAuthIntegrityCheck(userId: String): IntegrityResult {
        val currentTime = System.currentTimeMillis()
        val requestHash = "auth_${userId}_$currentTime"
        
        Log.d(TAG, "Performing auth integrity check for user: $userId")
        return requestIntegrityToken(requestHash)
    }
    
    /**
     * Integrity check for data sync operations
     */
    suspend fun performSyncIntegrityCheck(syncType: String): IntegrityResult {
        val currentTime = System.currentTimeMillis()
        val requestHash = "sync_${syncType}_$currentTime"
        
        Log.d(TAG, "Performing sync integrity check for: $syncType")
        return requestIntegrityToken(requestHash)
    }
    
    /**
     * Test the integrity API with detailed logging
     */
    suspend fun runIntegrityTest(context: Context): IntegrityTestResult {
        Log.d(TAG, "=== Starting Integrity API Test ===")
        
        val startTime = System.currentTimeMillis()
        
        // Test 1: Basic functionality
        Log.d(TAG, "Test 1: Basic integrity check")
        val basicResult = performQuickIntegrityCheck(context)
        
        // Test 2: Multiple requests to check rate limiting
        Log.d(TAG, "Test 2: Multiple request test")
        val multipleResults = mutableListOf<IntegrityResult>()
        repeat(3) { i ->
            val result = requestIntegrityToken("test_multiple_$i")
            multipleResults.add(result)
            // Small delay between requests
            kotlinx.coroutines.delay(100)
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        
        // Analyze results
        val successCount = multipleResults.count { it is IntegrityResult.Success }
        val errorCount = multipleResults.count { it is IntegrityResult.Error }
        
        val testResult = IntegrityTestResult(
            basicCheckSuccess = basicResult is IntegrityResult.Success,
            basicCheckResult = basicResult,
            multipleRequestsSuccessCount = successCount,
            multipleRequestsErrorCount = errorCount,
            totalTestTimeMs = totalTime,
            recommendations = generateRecommendations(basicResult, multipleResults)
        )
        
        Log.d(TAG, "=== Integrity API Test Complete ===")
        Log.d(TAG, "Basic check: ${if (testResult.basicCheckSuccess) "SUCCESS" else "FAILED"}")
        Log.d(TAG, "Multiple requests: $successCount success, $errorCount errors")
        Log.d(TAG, "Total test time: ${totalTime}ms")
        Log.d(TAG, "Recommendations: ${testResult.recommendations}")
        
        return testResult
    }
    
    private fun generateRecommendations(
        basicResult: IntegrityResult,
        multipleResults: List<IntegrityResult>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (basicResult is IntegrityResult.Error) {
            when {
                basicResult.error.contains("CLOUD_PROJECT_NUMBER") || 
                basicResult.error.contains("project") -> {
                    recommendations.add("⚠️ Set correct CLOUD_PROJECT_NUMBER in IntegrityChecker.kt")
                    recommendations.add("📋 Get project number from Google Play Console > Release > App Integrity")
                }
                basicResult.error.contains("PLAY_STORE_NOT_FOUND") -> {
                    recommendations.add("📱 App must be installed from Google Play Store for production")
                    recommendations.add("🧪 Use internal testing track for development")
                }
                basicResult.error.contains("Network") -> {
                    recommendations.add("🌐 Check internet connection")
                    recommendations.add("🔄 Retry the integrity check")
                }
                else -> {
                    recommendations.add("🔍 Check logs for specific error details")
                    recommendations.add("📖 Refer to Play Integrity API documentation")
                }
            }
        } else {
            recommendations.add("✅ Basic integrity check working correctly")
        }
        
        val errorRate = multipleResults.count { it is IntegrityResult.Error }.toFloat() / multipleResults.size
        if (errorRate > 0.5f) {
            recommendations.add("⚠️ High error rate in multiple requests - check rate limiting")
        } else if (errorRate == 0f) {
            recommendations.add("✅ All multiple requests successful")
        }
        
        if (CLOUD_PROJECT_NUMBER == "YOUR_CLOUD_PROJECT_NUMBER") {
            recommendations.add("🔑 CRITICAL: Replace CLOUD_PROJECT_NUMBER with actual value")
        }
        
        return recommendations
    }
}

/**
 * Result of an integrity check
 */
sealed class IntegrityResult {
    data class Success(val token: String) : IntegrityResult()
    data class Error(val error: String) : IntegrityResult()
}

/**
 * Result of comprehensive integrity testing
 */
data class IntegrityTestResult(
    val basicCheckSuccess: Boolean,
    val basicCheckResult: IntegrityResult,
    val multipleRequestsSuccessCount: Int,
    val multipleRequestsErrorCount: Int,
    val totalTestTimeMs: Long,
    val recommendations: List<String>
)
