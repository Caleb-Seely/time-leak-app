package com.cs.timeleak.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import com.cs.timeleak.data.UserPrefs
import com.google.android.gms.common.GoogleApiAvailability
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.isActive

data class AuthState(
    val countryCode: String = "+1",
    val phoneNumber: String = "",
    val otp: String = "",
    val verificationId: String? = null,
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val error: String? = null
)

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "AuthViewModel"
    private val stateMutex = Mutex()
    private var appContext: Context? = null
    
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        // Check if user is already authenticated
        auth.currentUser?.let {
            _authState.value = _authState.value.copy(isAuthenticated = true)
        }
    }

    private suspend fun updateState(update: AuthState.() -> AuthState) {
        stateMutex.withLock {
            _authState.value = _authState.value.update()
        }
    }
    
    fun updateCountryCode(countryCode: String) {
        _authState.value = _authState.value.copy(
            countryCode = countryCode,
            error = null
        )
    }
    
    fun updatePhoneNumber(phoneNumber: String) {
        _authState.value = _authState.value.copy(
            phoneNumber = phoneNumber,
            error = null
        )
    }
    
    fun updateOtp(otp: String) {
        _authState.value = _authState.value.copy(
            otp = otp,
            error = null
        )
    }
    
    private fun checkNetworkConnectivity(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        
        Log.d(TAG, "Network connectivity - Internet: $hasInternet, Validated: $hasValidated")
        return hasInternet && hasValidated
    }
    
    private fun checkGooglePlayServices(context: Context): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        
        return when (resultCode) {
            com.google.android.gms.common.ConnectionResult.SUCCESS -> {
                Log.d(TAG, "✅ Google Play Services is available")
                true
            }
            else -> {
                Log.w(TAG, "❌ Google Play Services not available: $resultCode")
                val availability = when (resultCode) {
                    com.google.android.gms.common.ConnectionResult.SERVICE_MISSING -> "Missing"
                    com.google.android.gms.common.ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "Update Required"
                    com.google.android.gms.common.ConnectionResult.SERVICE_DISABLED -> "Disabled"
                    com.google.android.gms.common.ConnectionResult.SERVICE_INVALID -> "Invalid"
                    else -> "Unknown ($resultCode)"
                }
                Log.w(TAG, "Google Play Services status: $availability")
                false
            }
        }
    }
    
    private fun checkAppCheckToken(activity: android.app.Activity) {
        try {
            Log.d(TAG, "🔍 Checking App Check token...")
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.getAppCheckToken(false)
                .addOnSuccessListener { result ->
                    Log.d(TAG, "✅ App Check token retrieved successfully")
                    Log.d(TAG, "Token length: ${result.token.length}")
                    Log.d(TAG, "Token expires: ${java.util.Date(result.expireTimeMillis)}")
                    Log.d(TAG, "Token preview: ${result.token.take(20)}...")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to get App Check token: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception while checking App Check token: ${e.message}", e)
        }
    }
    
    fun sendVerificationCode(activity: android.app.Activity) {
        val activityRef = WeakReference(activity)

        val countryCode = _authState.value.countryCode
        val phoneNumber = _authState.value.phoneNumber
        val fullPhoneNumber = countryCode + phoneNumber

        Log.d(TAG, "=== PHONE VERIFICATION START ===")
        Log.d(TAG, "Activity: ${activity.javaClass.simpleName}")
        Log.d(TAG, "Package name: ${activity.packageName}")
        Log.d(TAG, "Country code: '$countryCode'")
        Log.d(TAG, "Phone number: '$phoneNumber'")
        Log.d(TAG, "Full phone number: '$fullPhoneNumber'")
        Log.d(TAG, "Firebase Auth instance: ${auth.app.name}")
        Log.d(TAG, "Firebase project ID: ${auth.app.options.projectId}")
        Log.d(TAG, "Firebase app ID: ${auth.app.options.applicationId}")

        if (phoneNumber.isBlank()) {
            Log.w(TAG, "Phone number validation failed: blank")
            viewModelScope.launch {
                updateState { copy(error = "Please enter a phone number") }
            }
            return
        }
        
        // Validate phone number format
        if (phoneNumber.length != 10) {
            Log.w(TAG, "Phone number validation failed: length=${phoneNumber.length}, expected=10")
            _authState.value = _authState.value.copy(
                error = "Phone number must be exactly 10 digits"
            )
            return
        }
        
        // Validate country code format
        if (!countryCode.startsWith("+") || countryCode.length < 2 || countryCode.length > 4) {
            Log.w(TAG, "Country code validation failed: '$countryCode', length=${countryCode.length}")
            _authState.value = _authState.value.copy(
                error = "Invalid country code format"
            )
            return
        }
        
        // Validate that country code contains only digits after +
        if (!countryCode.substring(1).all { it.isDigit() }) {
            Log.w(TAG, "Country code validation failed: contains non-digits: '${countryCode.substring(1)}'")
            _authState.value = _authState.value.copy(
                error = "Country code must contain only digits after +"
            )
            return
        }
        
        Log.d(TAG, "✅ Phone number validation passed")
        Log.d(TAG, "Attempting to verify phone number: $fullPhoneNumber")
        
        // Check network connectivity
        val networkAvailable = checkNetworkConnectivity(activity)
        Log.d(TAG, "Network connectivity available: $networkAvailable")
        if (!networkAvailable) {
            _authState.value = _authState.value.copy(
                error = "No internet connection. Please check your network and try again."
            )
            return
        }
        
        // Check Google Play Services availability
        val gpsAvailable = checkGooglePlayServices(activity)
        Log.d(TAG, "Google Play Services available: $gpsAvailable")
        if (!gpsAvailable) {
            _authState.value = _authState.value.copy(
                error = "Google Play Services is required for phone verification. Please install or update Google Play Services."
            )
            return
        }
        
        // Check App Check token
        checkAppCheckToken(activity)
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting verification process in coroutine")
                _authState.value = _authState.value.copy(
                    isLoading = true,
                    loadingMessage = "Sending verification code...",
                    error = null
                )
                
                Log.d(TAG, "Creating PhoneAuthProvider callbacks")
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        Log.d(TAG, "✅ Auto-verification completed successfully")
                        Log.d(TAG, "Credential SMS code: ${credential.smsCode}")
                        // Check if ViewModel is still active
                        if (viewModelScope.isActive) {
                            viewModelScope.launch {
                                signInWithCredential(credential, activityRef.get())
                            }
                        }
                    }

                    override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                        Log.e(TAG, "❌ VERIFICATION FAILED", e)

                        val errorMessage = mapFirebaseError(e)

                        // Only update state if ViewModel is still active
                        if (viewModelScope.isActive) {
                            viewModelScope.launch {
                                updateState {
                                    copy(
                                        isLoading = false,
                                        loadingMessage = null,
                                        error = errorMessage
                                    )
                                }
                            }
                        }
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken
                    ) {
                        Log.d(TAG, "✅ CODE SENT SUCCESSFULLY")
                        Log.d(TAG, "Verification ID: ${verificationId.take(10)}...")
                        if (viewModelScope.isActive) {
                            viewModelScope.launch {
                                updateState {
                                    copy(
                                        verificationId = verificationId,
                                        isLoading = false,
                                        loadingMessage = null
                                    )
                                }
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Building PhoneAuthOptions...")
                val options = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(fullPhoneNumber)
                    .setTimeout(120L, TimeUnit.SECONDS) // Increased timeout
                    .setActivity(activity)
                    .setCallbacks(callbacks)
                    .build()
                
                Log.d(TAG, "PhoneAuthOptions created successfully")
                Log.d(TAG, "Calling PhoneAuthProvider.verifyPhoneNumber()...")
                PhoneAuthProvider.verifyPhoneNumber(options)
                Log.d(TAG, "PhoneAuthProvider.verifyPhoneNumber() called - waiting for callbacks...")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ EXCEPTION DURING PHONE VERIFICATION")
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Exception cause: ${e.cause?.message}")
                Log.e(TAG, "Full stack trace:", e)
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    loadingMessage = null,
                    error = "Failed to send code: ${e.message}"
                )
            }
        }
    }
    
    fun verifyCode(activity: android.app.Activity) {
        val verificationId = _authState.value.verificationId
        val otp = _authState.value.otp
        
        if (verificationId == null || otp.length != 6) {
            _authState.value = _authState.value.copy(
                error = "Please enter a valid 6-digit code"
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(
                    isLoading = true,
                    loadingMessage = "Verifying code...",
                    error = null
                )
                
                val credential = PhoneAuthProvider.getCredential(verificationId, otp)
                signInWithCredential(credential, activity)
                
            } catch (e: Exception) {
                Log.e(TAG, "Code verification failed: ${e.message}", e)
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    loadingMessage = null,
                    error = "Verification failed: ${e.message}"
                )
            }
        }
    }

    private suspend fun signInWithCredential(
        credential: PhoneAuthCredential,
        activity: android.app.Activity?
    ) {
        try {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user

            if (user != null) {
                // Use application context if activity is null/destroyed
                val context = activity ?: appContext
                context?.let {
                    UserPrefs.saveUser(it, user.phoneNumber, user.uid)
                    com.cs.timeleak.utils.SyncScheduler.scheduleImmediateSync(it)
                }

                Log.d(TAG, "User signed in successfully: ${user.phoneNumber}")
                updateState {
                    copy(
                        isLoading = false,
                        loadingMessage = null,
                        isAuthenticated = true
                    )
                }
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthException) {
            Log.e(TAG, "Firebase auth error: ${e.errorCode}", e)
            val errorMessage = when (e.errorCode) {
                "ERROR_INVALID_VERIFICATION_CODE" -> "Invalid verification code. Please try again."
                "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "This phone number is already registered."
                "ERROR_USER_DISABLED" -> "This account has been disabled."
                else -> "Authentication failed: ${e.localizedMessage}"
            }
            updateState {
                copy(
                    isLoading = false,
                    loadingMessage = null,
                    error = errorMessage
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sign in", e)
            updateState {
                copy(
                    isLoading = false,
                    loadingMessage = null,
                    error = "Sign in failed: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun mapFirebaseError(e: com.google.firebase.FirebaseException): String {
        return when {
            e.message?.contains("17499") == true ->
                "SMS verification failed. Please check if this is a test number on a production app."
            e.message?.contains("quota") == true ->
                "SMS quota exceeded. Please try again later."
            e.message?.contains("17010") == true ->
                "Invalid phone number format. Please check the country code and number."
            else -> "Verification failed: ${e.localizedMessage ?: "Unknown error"}"
        }
    }
    
    fun signOut(context: Context) {
        auth.signOut()
        UserPrefs.clear(context)
        _authState.value = AuthState()
    }
    
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    fun getCurrentUserPhone(): String? {
        return auth.currentUser?.phoneNumber
    }
} 