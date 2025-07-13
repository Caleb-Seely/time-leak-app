package com.cs.timeleak.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.cs.timeleak.model.UsageData
import com.cs.timeleak.model.AppUsageData
import com.cs.timeleak.data.DailyUsage
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cs.timeleak.data.UserPrefs
import android.content.Context

class FirestoreRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val usageCollection = firestore.collection("usage_data")
    private val TAG = "FirestoreRepository"

    suspend fun uploadUsageData(context: Context, dailyUsage: DailyUsage) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting upload of usage data")
            val savedPhone = UserPrefs.getPhone(context)
            if (savedPhone.isNullOrBlank()) {
                Log.d(TAG, "No phone number saved, skipping upload.")
                return@withContext
            }
            
            // Ensure user is authenticated and has a UID
            val currentUser = auth.currentUser
            if (currentUser == null || currentUser.uid.isNullOrBlank()) {
                Log.d(TAG, "No authenticated user with UID, skipping upload.")
                return@withContext
            }
            val userId = currentUser.uid
            val phoneNumber = currentUser.phoneNumber ?: savedPhone // fallback to saved phone if not in auth
            Log.d(TAG, "User ID: $userId, Phone: $phoneNumber")
            
            // Create main usage data document (without app details)
            val usageData = UsageData(
                userId = userId,
                phoneNumber = phoneNumber,
                date = Date(),
                totalScreenTime = dailyUsage.totalScreenTimeMillis,
                socialMediaTime = dailyUsage.socialMediaTimeMillis,
                entertainmentTime = dailyUsage.entertainmentTimeMillis,
                goalTime = UserPrefs.getGoalTime(context)
            )

            Log.d(TAG, "Uploading main usage data to Firestore...")
            val userDoc = usageCollection.document(userId)
            userDoc.set(usageData).await()
            
            // Store detailed app usage data in subcollection
            val appUsageCollection = userDoc.collection("app_usage")
            
            // Clear existing app usage data for this user
            val existingDocs = appUsageCollection.get().await()
            val batch = firestore.batch()
            existingDocs.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            
            // Upload new app usage data
            dailyUsage.topApps.forEach { app ->
                val appUsageData = AppUsageData(
                    packageName = app.packageName,
                    usageTimeMillis = app.usageTimeMillis,
                    appName = app.appName,
                    category = app.category
                )
                
                appUsageCollection.document(app.packageName)
                    .set(appUsageData)
                    .await()
            }
            
            Log.d(TAG, "Successfully uploaded usage data and app details to Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload usage data: ${e.message}", e)
            throw e
        }
    }
    
    suspend fun getUsageData(userId: String): UsageData? = withContext(Dispatchers.IO) {
        try {
            val document = usageCollection.document(userId).get().await()
            if (document.exists()) {
                document.toObject(UsageData::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get usage data: ${e.message}", e)
            null
        }
    }
    
    suspend fun getAppUsageData(userId: String): List<AppUsageData> = withContext(Dispatchers.IO) {
        try {
            val appUsageCollection = usageCollection.document(userId).collection("app_usage")
            val snapshot = appUsageCollection.get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(AppUsageData::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app usage data: ${e.message}", e)
            emptyList()
        }
    }
} 