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
    
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    init {
        // Check if user is already authenticated
        auth.currentUser?.let {
            _authState.value = _authState.value.copy(isAuthenticated = true)
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
    
    fun sendVerificationCode(context: Context) {
        val countryCode = _authState.value.countryCode
        val phoneNumber = _authState.value.phoneNumber
        val fullPhoneNumber = countryCode + phoneNumber

        if (phoneNumber.isBlank()) {
            _authState.value = _authState.value.copy(
                error = "Please enter a phone number"
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _authState.value = _authState.value.copy(
                    isLoading = true,
                    loadingMessage = "Sending verification code...",
                    error = null
                )
                
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        // Auto-verification completed
                        viewModelScope.launch {
                            signInWithCredential(credential, context)
                        }
                    }
                    
                    override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                        _authState.value = _authState.value.copy(
                            isLoading = false,
                            loadingMessage = null,
                            error = "Verification failed: ${e.message}"
                        )
                    }
                    
                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken
                    ) {
                        _authState.value = _authState.value.copy(
                            verificationId = verificationId,
                            isLoading = false,
                            loadingMessage = null
                        )
                    }
                }
                
                val options = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(fullPhoneNumber)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(context as android.app.Activity)
                    .setCallbacks(callbacks)
                    .build()
                
                PhoneAuthProvider.verifyPhoneNumber(options)
                
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    loadingMessage = null,
                    error = "Failed to send code: ${e.message}"
                )
            }
        }
    }
    
    fun verifyCode(context: Context) {
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
                signInWithCredential(credential, context)
                
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    loadingMessage = null,
                    error = "Verification failed: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun signInWithCredential(credential: PhoneAuthCredential, context: Context? = null) {
        try {
            auth.signInWithCredential(credential).await()
            val user = auth.currentUser
            if (context != null && user != null) {
                UserPrefs.saveUser(context, user.phoneNumber, user.uid)
            }
            _authState.value = _authState.value.copy(
                isLoading = false,
                loadingMessage = null,
                isAuthenticated = true
            )
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(
                isLoading = false,
                loadingMessage = null,
                error = "Sign in failed: ${e.message}"
            )
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