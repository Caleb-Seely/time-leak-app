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
import com.cs.timeleak.data.UsageStatsRepository

data class AuthState(
    val countryCode: String = "+1",
    val phoneNumber: String = "",
    val otp: String = "",
    val verificationId: String? = null,
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val error: String? = null,
    val hasFailedForCurrentNumber: Boolean = false // Track if current number failed
)

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "AuthViewModel"
    private val stateMutex = Mutex()
    private var appContext: Context? = null
    
    // Store the ForceResendingToken for resend operations
    private var forceResendingToken: PhoneAuthProvider.ForceResendingToken? = null
    
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
            error = null,
            isLoading = false,
            loadingMessage = null
        )
    }
    
    fun updatePhoneNumber(phoneNumber: String) {
        val currentState = _authState.value
        val isNumberChanged = phoneNumber != currentState.phoneNumber
        _authState.value = currentState.copy(
            phoneNumber = phoneNumber,
            // Clear hasFailedForCurrentNumber if user changes the phone number
            hasFailedForCurrentNumber = if (isNumberChanged) false else currentState.hasFailedForCurrentNumber
        )
    }
    
    fun updateOtp(otp: String) {
        _authState.value = _authState.value.copy(
            otp = otp,
            error = null
        )
    }
    
    fun clearError() {
        _authState.value = _authState.value.copy(
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
        val currentState = _authState.value
        Log.d(TAG, "🔄 sendVerificationCode called")
        Log.d(TAG, "🔄 Current state - isLoading: ${currentState.isLoading}, verificationId: ${currentState.verificationId?.take(10)}..., hasFailedForCurrentNumber: ${currentState.hasFailedForCurrentNumber}")
        Log.d(TAG, "🔄 Phone: '${currentState.phoneNumber}', Country: '${currentState.countryCode}'")
        
        // Check if this is a resend (we already have a verification ID)
        val isResend = currentState.verificationId != null
        Log.d(TAG, "🔄 Is this a resend operation? $isResend")
        
        // Prevent multiple rapid calls
        if (currentState.isLoading) {
            Log.w(TAG, "🔄 Already sending verification code, ignoring duplicate request")
            Log.w(TAG, "🔄 Current loading message: '${currentState.loadingMessage}'")
            return
        }
        
        val phoneNumberToSubmit = currentState.phoneNumber
        
        Log.d(TAG, "📞 sendVerificationCode called with phone: '$phoneNumberToSubmit'")
        Log.d(TAG, "📞 Current AuthViewModel state: phoneNumber='${currentState.phoneNumber}', isLoading=${currentState.isLoading}")
        
        // Store the phone number but DON'T clear it yet - wait for error/success
        // This prevents premature clearing that breaks state sync
        Log.d(TAG, "📞 Keeping phone number in AuthViewModel during processing")
        
        val activityRef = WeakReference(activity)

        val countryCode = currentState.countryCode
        val phoneNumber = phoneNumberToSubmit // Use the stored number
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
                updateState { copy(
                    error = "Please enter a phone number",
                    isLoading = false,
                    loadingMessage = null
                ) }
            }
            return
        }
        
        // Validate phone number format
        if (phoneNumber.length != 10) {
            Log.w(TAG, "Phone number validation failed: length=${phoneNumber.length}, expected=10")
            _authState.value = _authState.value.copy(
                error = "Phone number must be exactly 10 digits",
                isLoading = false,
                loadingMessage = null
            )
            return
        }
        
        // Validate country code format
        if (!countryCode.startsWith("+") || countryCode.length < 2 || countryCode.length > 4) {
            Log.w(TAG, "Country code validation failed: '$countryCode', length=${countryCode.length}")
            _authState.value = _authState.value.copy(
                error = "Invalid country code format",
                isLoading = false,
                loadingMessage = null
            )
            return
        }
        
        // Validate that country code contains only digits after +
        if (!countryCode.substring(1).all { it.isDigit() }) {
            Log.w(TAG, "Country code validation failed: contains non-digits: '${countryCode.substring(1)}'")
            _authState.value = _authState.value.copy(
                error = "Country code must contain only digits after +",
                isLoading = false,
                loadingMessage = null
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
                error = "No internet connection. Please check your network and try again.",
                isLoading = false,
                loadingMessage = null
            )
            return
        }
        
        // Check Google Play Services availability
        val gpsAvailable = checkGooglePlayServices(activity)
        Log.d(TAG, "Google Play Services available: $gpsAvailable")
        if (!gpsAvailable) {
            _authState.value = _authState.value.copy(
                error = "Google Play Services is required for phone verification. Please install or update Google Play Services.",
                isLoading = false,
                loadingMessage = null
            )
            return
        }
        
        // Check App Check token
        checkAppCheckToken(activity)
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "🚀 Starting verification process in coroutine")
                Log.d(TAG, "🚀 Thread: ${Thread.currentThread().name}")
                Log.d(TAG, "🚀 ViewModel scope active: ${viewModelScope.isActive}")
                
                val loadingMessage = if (isResend) "Resending verification code..." else "Sending verification code..."
                Log.d(TAG, "🚀 Setting loading state: '$loadingMessage'")
                
                _authState.value = _authState.value.copy(
                    isLoading = true,
                    loadingMessage = loadingMessage,
                    error = null
                )
                
                Log.d(TAG, "🚀 State updated - isLoading: ${_authState.value.isLoading}, message: '${_authState.value.loadingMessage}'")
                
                Log.d(TAG, "📞 Creating PhoneAuthProvider callbacks")
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        Log.d(TAG, "✅ onVerificationCompleted called - Auto-verification successful!")
                        Log.d(TAG, "✅ Credential SMS code: ${credential.smsCode}")
                        Log.d(TAG, "✅ ViewModel scope active: ${viewModelScope.isActive}")
                        Log.d(TAG, "✅ Activity reference valid: ${activityRef.get() != null}")
                        
                        // Check if ViewModel is still active
                        if (viewModelScope.isActive) {
                            Log.d(TAG, "✅ Starting sign-in with credential...")
                            viewModelScope.launch {
                                signInWithCredential(credential, activityRef.get())
                            }
                        } else {
                            Log.w(TAG, "⚠️ ViewModel scope not active, skipping auto sign-in")
                        }
                    }

                    override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                        Log.e(TAG, "❌ onVerificationFailed called")
                        Log.e(TAG, "❌ Exception type: ${e.javaClass.simpleName}")
                        Log.e(TAG, "❌ Exception message: ${e.message}")
                        Log.e(TAG, "❌ Exception cause: ${e.cause?.message}")
                        Log.e(TAG, "❌ ViewModel scope active: ${viewModelScope.isActive}")
                        Log.i(TAG, "❌ Clearing phone number to prevent infinite loop retry")

                        val errorMessage = mapFirebaseError(e)

                        // Only update state if ViewModel is still active
                        if (viewModelScope.isActive) {
                            viewModelScope.launch {
                                updateState {
                                    copy(
                                        phoneNumber = "",      // CRITICAL: Clear phone number to prevent infinite loop
                                        verificationId = null, // Clear verification ID to return to phone input
                                        otp = "",             // Clear any partial OTP
                                        isLoading = false,
                                        loadingMessage = null,
                                        error = errorMessage,
                                        hasFailedForCurrentNumber = true // Mark this number as failed
                                    )
                                }
                                // Clear the force resending token on failure
                                forceResendingToken = null
                                Log.d(TAG, "❌ Cleared ForceResendingToken due to verification failure")
                            }
                        }
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken
                    ) {
                        Log.d(TAG, "🎉 onCodeSent called - CODE SENT SUCCESSFULLY!")
                        Log.d(TAG, "🎉 Verification ID: ${verificationId.take(10)}...")
                        Log.d(TAG, "🎉 Resend token: ${token.javaClass.simpleName}")
                        Log.d(TAG, "🎉 ViewModel scope active: ${viewModelScope.isActive}")
                        Log.d(TAG, "🎉 Current thread: ${Thread.currentThread().name}")
                        
                        // CRITICAL: Store the ForceResendingToken for future resend operations
                        forceResendingToken = token
                        Log.d(TAG, "🎉 Stored ForceResendingToken for future resends")
                        
                        if (viewModelScope.isActive) {
                            Log.d(TAG, "🎉 Updating state with new verification ID...")
                            viewModelScope.launch {
                                updateState {
                                    copy(
                                        verificationId = verificationId,
                                        isLoading = false,
                                        loadingMessage = null
                                    )
                                }
                                Log.d(TAG, "🎉 State updated - verification screen should now show")
                                Log.d(TAG, "🎉 New state - isLoading: ${_authState.value.isLoading}, verificationId exists: ${_authState.value.verificationId != null}")
                            }
                        } else {
                            Log.w(TAG, "⚠️ ViewModel scope not active, cannot update state")
                        }
                    }
                }
                
                Log.d(TAG, "🏗️ Building PhoneAuthOptions...")
                Log.d(TAG, "🏗️ Activity: ${activity.javaClass.simpleName}@${activity.hashCode()}")
                Log.d(TAG, "🏗️ Activity is finishing: ${activity.isFinishing}")
                Log.d(TAG, "🏗️ Activity is destroyed: ${activity.isDestroyed}")
                
                val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(fullPhoneNumber)
                    .setTimeout(120L, TimeUnit.SECONDS) // Increased timeout
                    .setActivity(activity)
                    .setCallbacks(callbacks)
                    
                // Add force resend token if this is a resend operation
                if (isResend && currentState.verificationId != null) {
                    if (forceResendingToken != null) {
                        Log.d(TAG, "🏗️ Adding stored ForceResendingToken for resend operation")
                        optionsBuilder.setForceResendingToken(forceResendingToken!!)
                    } else {
                        Log.w(TAG, "🏗️ Resend requested but no ForceResendingToken available - this may cause the resend to be ignored")
                        Log.w(TAG, "🏗️ Proceeding without token - Firebase may silently ignore this request")
                        // Clear verification state to retry from phone input if no token available
                        _authState.value = _authState.value.copy(
                            verificationId = null,
                            otp = "",
                            isLoading = false,
                            loadingMessage = null,
                            error = "Please try sending the code again"
                        )
                        return@launch
                    }
                }
                
                val options = optionsBuilder.build()
                
                Log.d(TAG, "🏗️ PhoneAuthOptions created successfully")
                Log.d(TAG, "🚀 Calling PhoneAuthProvider.verifyPhoneNumber()...")
                Log.d(TAG, "🚀 Expecting callbacks: onCodeSent, onVerificationCompleted, or onVerificationFailed")
                
                PhoneAuthProvider.verifyPhoneNumber(options)
                
                Log.d(TAG, "🚀 PhoneAuthProvider.verifyPhoneNumber() called - now waiting for callbacks...")
                Log.d(TAG, "🚀 If no callbacks fire within 2 minutes, there may be a network or configuration issue")
                
                // Add timeout mechanism to handle cases where callbacks never fire
                viewModelScope.launch {
                    kotlinx.coroutines.delay(130_000L) // 130 seconds (slightly longer than Firebase timeout)
                    
                    // Check if we're still in loading state after timeout
                    if (_authState.value.isLoading && _authState.value.loadingMessage?.contains("verification code") == true) {
                        Log.w(TAG, "⏰ Firebase callbacks timeout - no response after 130 seconds")
                        Log.w(TAG, "⏰ Current state: loading=${_authState.value.isLoading}, verificationId exists=${_authState.value.verificationId != null}")
                        
                        _authState.value = _authState.value.copy(
                            verificationId = null,
                            otp = "",
                            isLoading = false,
                            loadingMessage = null,
                            error = "Request timed out. Please try again."
                        )
                        
                        // Clear the force resending token on timeout
                        forceResendingToken = null
                        Log.d(TAG, "⏰ Cleared ForceResendingToken due to timeout")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "💥 EXCEPTION DURING PHONE VERIFICATION")
                Log.e(TAG, "💥 Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "💥 Exception message: ${e.message}")
                Log.e(TAG, "💥 Exception cause: ${e.cause?.message}")
                Log.e(TAG, "💥 Thread: ${Thread.currentThread().name}")
                Log.e(TAG, "💥 ViewModel scope active: ${viewModelScope.isActive}")
                Log.e(TAG, "💥 Full stack trace:", e)
                
                Log.d(TAG, "💥 Exception handler: About to clear state to prevent infinite loop")
                Log.d(TAG, "💥 Before exception state update: phoneNumber='${_authState.value.phoneNumber}', verificationId exists: ${_authState.value.verificationId != null}")
                
                val shouldClearPhone = !isResend // Only clear phone number if this wasn't a resend
                Log.d(TAG, "💥 Should clear phone number: $shouldClearPhone (isResend: $isResend)")
                
                _authState.value = _authState.value.copy(
                    phoneNumber = if (shouldClearPhone) "" else _authState.value.phoneNumber, // Keep phone on resend failure
                    verificationId = null, // Clear verification ID to return to phone input
                    otp = "",             // Clear any partial OTP
                    isLoading = false,
                    loadingMessage = null,
                    error = "Failed to send code: ${e.message}"
                )
                
                // Clear the force resending token on exception
                forceResendingToken = null
                Log.d(TAG, "💥 Cleared ForceResendingToken due to exception")
                
                Log.d(TAG, "💥 After exception state update: phoneNumber='${_authState.value.phoneNumber}', isLoading: ${_authState.value.isLoading}")
                Log.d(TAG, "💥 This should ${if (shouldClearPhone) "trigger state sync and return to phone input" else "show error but stay on OTP screen"}")
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
                    verificationId = null, // Clear verification ID to return to phone input
                    otp = "",             // Clear any partial OTP
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
                    
                    // Capture baseline screen time if not already captured
                    if (!UserPrefs.isBaselineCaptured(it)) {
                        captureBaseline(it)
                    }
                    
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
                    verificationId = null, // Clear verification ID to return to phone input
                    otp = "",             // Clear any partial OTP
                    isLoading = false,
                    loadingMessage = null,
                    error = errorMessage
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sign in", e)
            updateState {
                copy(
                    verificationId = null, // Clear verification ID to return to phone input
                    otp = "",             // Clear any partial OTP
                    isLoading = false,
                    loadingMessage = null,
                    error = "Sign in failed: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun captureBaseline(context: Context) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "📊 Capturing baseline screen time for new user...")
                val repository = UsageStatsRepository(context)
                
                // Check if we have usage access permission
                if (!repository.hasUsageAccessPermission()) {
                    Log.w(TAG, "⚠️ No usage access permission - cannot capture baseline")
                    return@launch
                }
                
                val baseline30DayAverage = repository.getLast30DayAverageScreenTime()
                if (baseline30DayAverage != null && baseline30DayAverage > 0) {
                    UserPrefs.saveBaselineScreenTime(context, baseline30DayAverage)
                    Log.d(TAG, "✅ Baseline captured: ${baseline30DayAverage}ms (${baseline30DayAverage / (1000 * 60)} minutes)")
                } else {
                    Log.w(TAG, "⚠️ No baseline data available - will try again later")
                    // Don't mark as captured if no data available
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to capture baseline: ${e.message}", e)
            }
        }
    }

    /**
     * Clear verification state and return to phone input screen
     * Preserves error message so user knows what went wrong
     */
    fun clearVerificationState() {
        _authState.value = _authState.value.copy(
            verificationId = null,
            otp = "",
            isLoading = false,
            loadingMessage = null
            // Preserve error message - don't clear it
        )
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