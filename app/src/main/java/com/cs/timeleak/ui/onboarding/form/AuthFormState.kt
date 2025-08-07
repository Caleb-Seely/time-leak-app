package com.cs.timeleak.ui.onboarding.form

import androidx.compose.runtime.*
import com.cs.timeleak.ui.onboarding.model.ValidationResult
import android.util.Log

/**
 * Form state manager for authentication flow.
 * Handles phone number input, OTP verification, and validation.
 */
class AuthFormState {
    
    private val TAG = "AuthFormState"
    
    var countryCode by mutableStateOf("+1")
        private set
        
    var phoneNumber by mutableStateOf("")
        private set
        
    var otp by mutableStateOf("")
        private set
        
    var isWaitingForOtp by mutableStateOf(false)
        private set
        
    var otpResendCooldown by mutableStateOf(0)
        private set
    
    // Form validation states - this always performs actual validation
    val phoneNumberValidation: ValidationResult
        get() = validatePhoneNumber(phoneNumber, countryCode)
        
    /**
     * Determines if phone error should be shown in UI
     * This prevents showing errors prematurely before user interaction
     */
    fun shouldShowPhoneError(): Boolean {
        // Only show error if validation fails AND user has attempted submission
        // Do NOT show error just because they typed 10 digits - only on submission attempt
        return !phoneNumberValidation.isValid() && _hasAttemptedPhoneSubmission
    }
    
    /**
     * Determines if OTP error should be shown in UI
     * This prevents showing errors prematurely before user interaction
     */
    fun shouldShowOtpError(): Boolean {
        // Only show error if validation fails AND user has attempted submission
        return !otpValidation.isValid() && _hasAttemptedOtpSubmission && otp.isNotEmpty()
    }
    
    private var _hasAttemptedPhoneSubmission = false
    private var _hasAttemptedOtpSubmission = false
    private var _isSubmissionInProgress = false
    
    fun markPhoneSubmissionAttempted() {
        _hasAttemptedPhoneSubmission = true
        _isSubmissionInProgress = true
    }
    
    fun markOtpSubmissionAttempted() {
        _hasAttemptedOtpSubmission = true
    }
    
    fun clearSubmissionInProgress() {
        _isSubmissionInProgress = false
    }
    
    val isSubmissionInProgress: Boolean
        get() = _isSubmissionInProgress
        
    val otpValidation: ValidationResult
        get() = validateOtp(otp)
    
    // Computed properties
    val isPhoneNumberValid: Boolean
        get() = phoneNumberValidation.isValid()
        
    val isOtpValid: Boolean
        get() = otpValidation.isValid()
        
    /**
     * Determines if phone number can be submitted (valid format and ready)
     * This is separate from error display logic - uses actual validation
     */
    val canSubmitPhoneNumber: Boolean
        get() = isPhoneNumberValid && !isWaitingForOtp && !_isSubmissionInProgress
        
    val canSubmitOtp: Boolean
        get() = isOtpValid
        
    val canResendOtp: Boolean
        get() = isWaitingForOtp && otpResendCooldown == 0
    
    /**
     * Update country code with validation
     */
    fun updateCountryCode(newCode: String) {
        countryCode = newCode.let { code ->
            when {
                code.isEmpty() -> "+1"
                code.startsWith("+") && code.length <= 4 -> code
                !code.startsWith("+") && code.length <= 3 -> "+$code"
                else -> countryCode // Keep current if invalid
            }
        }
    }
    
    /**
     * Update phone number with digit filtering
     */
    fun updatePhoneNumber(newNumber: String) {
        val digitsOnly = newNumber.filter { it.isDigit() }
        if (digitsOnly.length <= 10) {
            phoneNumber = digitsOnly
        }
    }
    
    /**
     * Update OTP with validation
     */
    fun updateOtp(newOtp: String) {
        if (newOtp.length <= 6 && newOtp.all { it.isDigit() }) {
            otp = newOtp
        }
    }
    
    /**
     * Set waiting for OTP state (called after phone verification sent)
     */
    fun updateWaitingForOtp(waiting: Boolean) {
        isWaitingForOtp = waiting
        if (waiting) {
            startResendCooldown()
            _isSubmissionInProgress = false // Clear submission progress when OTP state is set
        } else {
            otpResendCooldown = 0
        }
    }
    
    /**
     * Start countdown for OTP resend
     */
    private fun startResendCooldown() {
        otpResendCooldown = 60 // 60 second cooldown
    }
    
    /**
     * Decrement resend cooldown (called by timer)
     */
    fun decrementResendCooldown() {
        if (otpResendCooldown > 0) {
            otpResendCooldown--
        }
    }
    
    /**
     * Clear form data
     */
    fun clear() {
        countryCode = "+1"
        phoneNumber = ""
        otp = ""
        isWaitingForOtp = false
        otpResendCooldown = 0
        _hasAttemptedPhoneSubmission = false
        _hasAttemptedOtpSubmission = false
        _isSubmissionInProgress = false
    }
    
    /**
     * Reset OTP state for retry
     */
    fun resetOtp() {
        otp = ""
        isWaitingForOtp = false
        otpResendCooldown = 0
    }
    
    /**
     * Get formatted phone number for display
     */
    fun getFormattedPhoneNumber(): String {
        return formatPhoneNumberDisplay(phoneNumber)
    }
    
    /**
     * Get full phone number with country code
     */
    fun getFullPhoneNumber(): String {
        return "$countryCode$phoneNumber"
    }
    
    /**
     * Get form state for debugging
     */
    fun getDebugInfo(): Map<String, String> {
        return mapOf(
            "country_code" to countryCode,
            "phone_number" to phoneNumber,
            "phone_valid" to isPhoneNumberValid.toString(),
            "otp_length" to otp.length.toString(),
            "otp_valid" to isOtpValid.toString(),
            "waiting_otp" to isWaitingForOtp.toString(),
            "resend_cooldown" to otpResendCooldown.toString(),
            "can_submit_phone" to canSubmitPhoneNumber.toString(),
            "can_submit_otp" to canSubmitOtp.toString()
        )
    }
}

/**
 * Validation utilities for auth form
 */
object AuthFormValidator {
    
    fun validatePhoneNumber(number: String, countryCode: String): ValidationResult {
        return when {
            number.length != 10 -> ValidationResult.Error("Phone number must be exactly 10 digits")
            !countryCode.startsWith("+") -> ValidationResult.Error("Invalid country code format")
            countryCode.length !in 2..4 -> ValidationResult.Error("Invalid country code length")
            !countryCode.substring(1).all { it.isDigit() } -> ValidationResult.Error("Country code must contain only digits")
            else -> ValidationResult.Success
        }
    }
    
    fun validateOtp(otp: String): ValidationResult {
        return when {
            otp.length != 6 -> ValidationResult.Error("Please enter a valid 6-digit code")
            !otp.all { it.isDigit() } -> ValidationResult.Error("OTP must contain only digits")
            else -> ValidationResult.Success
        }
    }
}

/**
 * Helper function to validate phone number
 */
private fun validatePhoneNumber(number: String, countryCode: String): ValidationResult {
    return AuthFormValidator.validatePhoneNumber(number, countryCode)
}

/**
 * Helper function to validate OTP
 */
private fun validateOtp(otp: String): ValidationResult {
    return AuthFormValidator.validateOtp(otp)
}

/**
 * Format phone number for display (xxx - xxx - xxxx)
 */
private fun formatPhoneNumberDisplay(digits: String): String {
    return when (digits.length) {
        0 -> ""
        in 1..3 -> digits
        in 4..6 -> "${digits.substring(0, 3)} - ${digits.substring(3)}"
        in 7..10 -> "${digits.substring(0, 3)} - ${digits.substring(3, 6)} - ${digits.substring(6)}"
        else -> "${digits.substring(0, 3)} - ${digits.substring(3, 6)} - ${digits.substring(6, 10)}"
    }
}

/**
 * Composable to remember auth form state across recompositions
 */
@Composable
fun rememberAuthFormState(): AuthFormState {
    return remember { AuthFormState() }
}
