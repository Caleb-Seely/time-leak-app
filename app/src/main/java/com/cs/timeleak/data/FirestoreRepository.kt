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
            Log.d(TAG, "=== FIRESTORE UPLOAD START ===")
            Log.d(TAG, "Firestore instance: ${firestore.javaClass.simpleName}")
            Log.d(TAG, "Firebase Auth instance: ${auth.javaClass.simpleName}")
            Log.d(TAG, "Usage collection path: ${usageCollection.path}")
            Log.d(TAG, "Daily usage total time: ${dailyUsage.totalScreenTimeMillis}ms")
            Log.d(TAG, "Daily usage apps count: ${dailyUsage.topApps.size}")
            
            val savedPhone = UserPrefs.getPhone(context)
            Log.d(TAG, "Saved phone from prefs: ${savedPhone?.take(5)}...")
            if (savedPhone.isNullOrBlank()) {
                Log.w(TAG, "❌ No phone number saved in prefs, skipping upload")
                return@withContext
            }
            
            // Ensure user is authenticated and has a UID
            val currentUser = auth.currentUser
            Log.d(TAG, "Current Firebase user: ${currentUser?.uid?.take(10)}...")
            Log.d(TAG, "User is anonymous: ${currentUser?.isAnonymous}")
            Log.d(TAG, "User phone number: ${currentUser?.phoneNumber?.take(5)}...")
            Log.d(TAG, "User email: ${currentUser?.email}")
            
            if (currentUser == null || currentUser.uid.isNullOrBlank()) {
                Log.w(TAG, "❌ No authenticated user with UID, skipping upload")
                return@withContext
            }
            val userId = currentUser.uid
            val phoneNumber = currentUser.phoneNumber
            if (phoneNumber.isNullOrBlank()) {
                Log.w(TAG, "❌ No phone number in Firebase user, skipping upload")
                return@withContext
            }
            Log.d(TAG, "✅ User validation passed - ID: ${userId.take(10)}..., Phone: ${phoneNumber.take(5)}...")
            
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

            Log.d(TAG, "📤 Uploading main usage data to Firestore...")
            Log.d(TAG, "Document path: ${usageCollection.path}/${userId}")
            Log.d(TAG, "Usage data object: ${usageData}")
            
            val userDoc = usageCollection.document(userId)
            Log.d(TAG, "User document reference: ${userDoc.path}")
            
            Log.d(TAG, "Calling Firestore set()...")
            userDoc.set(usageData).await()
            Log.d(TAG, "✅ Main usage data uploaded successfully")
            
            // Store detailed app usage data in subcollection
            val appUsageCollection = userDoc.collection("app_usage")
            Log.d(TAG, "App usage collection path: ${appUsageCollection.path}")
            
            // Clear existing app usage data for this user
            Log.d(TAG, "Fetching existing app usage documents...")
            val existingDocs = appUsageCollection.get().await()
            Log.d(TAG, "Found ${existingDocs.documents.size} existing app usage documents")
            
            val batch = firestore.batch()
            existingDocs.documents.forEach { doc ->
                Log.d(TAG, "Marking for deletion: ${doc.id}")
                batch.delete(doc.reference)
            }
            Log.d(TAG, "Committing batch delete...")
            batch.commit().await()
            Log.d(TAG, "✅ Existing app usage data cleared")
            
            // Upload new app usage data
            //Commenting out for now, I do not want the privacy overreach atm
//            dailyUsage.topApps.forEach { app ->
//                val appUsageData = AppUsageData(
//                    packageName = app.packageName,
//                    usageTimeMillis = app.usageTimeMillis,
//                    appName = app.appName,
//                    category = app.category
//                )
//
//                appUsageCollection.document(app.packageName)
//                    .set(appUsageData)
//                    .await()
//            }
            
            Log.d(TAG, "✅ FIRESTORE UPLOAD COMPLETED SUCCESSFULLY")
        } catch (e: Exception) {
            Log.e(TAG, "❌ FIRESTORE UPLOAD FAILED")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception cause: ${e.cause?.message}")
            Log.e(TAG, "Full stack trace:", e)
            throw e
        }
    }
    
    suspend fun getUsageData(userId: String): UsageData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📥 Fetching usage data for user: ${userId.take(10)}...")
            val document = usageCollection.document(userId).get().await()
            Log.d(TAG, "Document exists: ${document.exists()}")
            Log.d(TAG, "Document metadata: ${document.metadata}")
            
            if (document.exists()) {
                val usageData = document.toObject(UsageData::class.java)
                Log.d(TAG, "✅ Usage data retrieved: ${usageData}")
                usageData
            } else {
                Log.d(TAG, "❌ No usage data found for user")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get usage data")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Full stack trace:", e)
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