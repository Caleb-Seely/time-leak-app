package com.cs.timeleak.ui.onboarding.form

import androidx.compose.runtime.*
import com.cs.timeleak.ui.onboarding.model.ValidationResult
import com.cs.timeleak.ui.onboarding.utils.formatPhoneNumberDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Advanced authentication form state with intelligent submission control.
 * 
 * Features:
 * - Intelligent retry logic with exponential backoff
 * - Submission history tracking to prevent infinite loops
 * - Error-aware auto-submission that learns from failures
 * - Graceful degradation when auto-submission fails repeatedly
 * - Circuit breaker pattern for failed submissions
 */
class AdvancedAuthFormState {
    
    // Core form state
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
    
    // Advanced submission control
    private val _submissionHistory = MutableStateFlow<Map<String, SubmissionAttempt>>(emptyMap())
    val submissionHistory: StateFlow<Map<String, SubmissionAttempt>> = _submissionHistory.asStateFlow()
    
    private val _submissionState = MutableStateFlow(SubmissionState.IDLE)
    val submissionState: StateFlow<SubmissionState> = _submissionState.asStateFlow()
    
    private val _autoSubmissionMode = MutableStateFlow(AutoSubmissionMode.ENABLED)
    val autoSubmissionMode: StateFlow<AutoSubmissionMode> = _autoSubmissionMode.asStateFlow()
    
    private var _hasUserInteracted = false
    private var _lastKnownGoodNumber = ""
    
    // Form validation - always performs actual validation
    val phoneNumberValidation: ValidationResult
        get() = validatePhoneNumber(phoneNumber, countryCode)
        
    val otpValidation: ValidationResult
        get() = validateOtp(otp)
    
    // Smart error display logic
    fun shouldShowPhoneError(): Boolean {
        val currentNumber = getFullPhoneNumber()
        val attempt = _submissionHistory.value[currentNumber]
        
        return when {
            // Never show error if user hasn't interacted
            !_hasUserInteracted -> false
            
            // Show error if validation fails AND user has attempted submission
            !phoneNumberValidation.isValid() && attempt != null -> true
            
            // Show error if this specific number has failed before
            attempt?.hasErrors == true -> true
            
            else -> false
        }
    }
    
    // Computed properties with intelligent logic
    val isPhoneNumberValid: Boolean
        get() = phoneNumberValidation.isValid()
        
    val isOtpValid: Boolean
        get() = otpValidation.isValid()
    
    /**
     * Smart submission logic that considers history and prevents infinite loops
     */
    val canSubmitPhoneNumber: Boolean
        get() {
            if (!isPhoneNumberValid || isWaitingForOtp) return false
            
            val currentNumber = getFullPhoneNumber()
            val attempt = _submissionHistory.value[currentNumber]
            val submissionState = _submissionState.value
            
            return when {
                // Can't submit if already in progress
                submissionState == SubmissionState.SUBMITTING -> false
                
                // Can't submit if in cooldown
                submissionState == SubmissionState.COOLDOWN -> false
                
                // Can submit if never tried this number
                attempt == null -> true
                
                // Can submit if enough time has passed since last failure
                attempt.hasErrors && attempt.canRetry() -> true
                
                // Can't submit if this number failed recently
                attempt.hasErrors && !attempt.canRetry() -> false
                
                // Can submit if no errors with this number
                !attempt.hasErrors -> true
                
                else -> false
            }
        }
        
    val canSubmitOtp: Boolean
        get() = isOtpValid
        
    val canResendOtp: Boolean
        get() = isWaitingForOtp && otpResendCooldown == 0
    
    /**
     * Should auto-submit based on intelligent logic
     */
    fun shouldAutoSubmit(): Boolean {
        val currentNumber = getFullPhoneNumber()
        val attempt = _submissionHistory.value[currentNumber]
        val mode = _autoSubmissionMode.value
        
        return when (mode) {
            AutoSubmissionMode.DISABLED -> false
            AutoSubmissionMode.ENABLED -> {
                canSubmitPhoneNumber && 
                _hasUserInteracted && 
                (attempt == null || (!attempt.hasErrors || attempt.canRetry()))
            }
            AutoSubmissionMode.MANUAL_ONLY -> false
        }
    }
    
    /**
     * Record a submission attempt
     */
    fun recordSubmissionAttempt(phoneNumber: String, isError: Boolean = false, errorMessage: String? = null) {
        val currentHistory = _submissionHistory.value.toMutableMap()
        val existingAttempt = currentHistory[phoneNumber]
        
        val newAttempt = if (existingAttempt == null) {
            SubmissionAttempt(
                phoneNumber = phoneNumber,
                attempts = 1,
                hasErrors = isError,
                lastError = errorMessage,
                firstAttemptTime = System.currentTimeMillis(),
                lastAttemptTime = System.currentTimeMillis()
            )
        } else {
            existingAttempt.copy(
                attempts = existingAttempt.attempts + 1,
                hasErrors = existingAttempt.hasErrors || isError,
                lastError = if (isError) errorMessage else existingAttempt.lastError,
                lastAttemptTime = System.currentTimeMillis()
            )
        }
        
        currentHistory[phoneNumber] = newAttempt
        _submissionHistory.value = currentHistory
        
        // Update auto-submission mode based on failure patterns
        updateAutoSubmissionMode(newAttempt)
    }
    
    /**
     * Update auto-submission mode based on failure patterns
     */
    private fun updateAutoSubmissionMode(attempt: SubmissionAttempt) {
        val currentMode = _autoSubmissionMode.value
        
        when {
            // Disable auto-submission after 3 failed attempts with the same number
            attempt.attempts >= 3 && attempt.hasErrors -> {
                _autoSubmissionMode.value = AutoSubmissionMode.MANUAL_ONLY
            }
            
            // Re-enable after successful submission
            !attempt.hasErrors && currentMode == AutoSubmissionMode.MANUAL_ONLY -> {
                _autoSubmissionMode.value = AutoSubmissionMode.ENABLED
            }
        }
    }
    
    /**
     * Set submission state
     */
    fun setSubmissionState(state: SubmissionState) {
        _submissionState.value = state
    }
    
    /**
     * Clear submission state
     */
    fun clearSubmissionState() {
        _submissionState.value = SubmissionState.IDLE
    }
    
    /**
     * Update country code with validation
     */
    fun updateCountryCode(newCode: String) {
        _hasUserInteracted = true
        countryCode = when {
            newCode.isEmpty() -> "+1"
            newCode.startsWith("+") && newCode.length <= 4 -> newCode
            !newCode.startsWith("+") && newCode.length <= 3 -> "+$newCode"
            else -> countryCode // Keep current if invalid
        }
    }
    
    /**
     * Update phone number with digit filtering
     */
    fun updatePhoneNumber(newNumber: String) {
        _hasUserInteracted = true
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
     * Set waiting for OTP state
     */
    fun updateWaitingForOtp(waiting: Boolean) {
        isWaitingForOtp = waiting
        if (waiting) {
            startResendCooldown()
            clearSubmissionState()
            // Record successful submission
            recordSubmissionAttempt(getFullPhoneNumber(), isError = false)
        } else {
            otpResendCooldown = 0
        }
    }
    
    /**
     * Handle submission error
     */
    fun handleSubmissionError(errorMessage: String) {
        recordSubmissionAttempt(getFullPhoneNumber(), isError = true, errorMessage = errorMessage)
        setSubmissionState(SubmissionState.COOLDOWN)
    }
    
    /**
     * Start countdown for OTP resend
     */
    private fun startResendCooldown() {
        otpResendCooldown = 60
    }
    
    /**
     * Decrement resend cooldown
     */
    fun decrementResendCooldown() {
        if (otpResendCooldown > 0) {
            otpResendCooldown--
        }
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
     * Check if a number is known to be problematic
     */
    fun isProblematicNumber(phoneNumber: String = getFullPhoneNumber()): Boolean {
        val attempt = _submissionHistory.value[phoneNumber]
        return attempt?.hasErrors == true && !attempt.canRetry()
    }
    
    /**
     * Get retry delay for current number
     */
    fun getRetryDelayForCurrentNumber(): Long? {
        val attempt = _submissionHistory.value[getFullPhoneNumber()]
        return attempt?.getRetryDelay()
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
        _hasUserInteracted = false
        _submissionHistory.value = emptyMap()
        _submissionState.value = SubmissionState.IDLE
        _autoSubmissionMode.value = AutoSubmissionMode.ENABLED
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
     * Force enable auto-submission (for manual override)
     */
    fun forceEnableAutoSubmission() {
        _autoSubmissionMode.value = AutoSubmissionMode.ENABLED
    }
    
    /**
     * Get debug information
     */
    fun getDebugInfo(): Map<String, Any> {
        val currentNumber = getFullPhoneNumber()
        val attempt = _submissionHistory.value[currentNumber]
        
        return mapOf(
            "phone_number" to currentNumber,
            "phone_valid" to isPhoneNumberValid,
            "can_submit" to canSubmitPhoneNumber,
            "should_auto_submit" to shouldAutoSubmit(),
            "submission_state" to _submissionState.value.name,
            "auto_submission_mode" to _autoSubmissionMode.value.name,
            "user_interacted" to _hasUserInteracted,
            "attempt_count" to (attempt?.attempts ?: 0),
            "has_errors" to (attempt?.hasErrors ?: false),
            "can_retry" to (attempt?.canRetry() ?: true),
            "retry_delay" to (attempt?.getRetryDelay() ?: 0)
        )
    }
}

/**
 * Submission attempt tracking
 */
data class SubmissionAttempt(
    val phoneNumber: String,
    val attempts: Int,
    val hasErrors: Boolean,
    val lastError: String?,
    val firstAttemptTime: Long,
    val lastAttemptTime: Long
) {
    /**
     * Check if we can retry this number (exponential backoff)
     */
    fun canRetry(): Boolean {
        if (!hasErrors) return true
        
        val timeSinceLastAttempt = System.currentTimeMillis() - lastAttemptTime
        val backoffDelay = getRetryDelay()
        
        return timeSinceLastAttempt >= backoffDelay
    }
    
    /**
     * Get retry delay with exponential backoff
     */
    fun getRetryDelay(): Long {
        if (!hasErrors) return 0
        
        // Exponential backoff: 5s, 15s, 45s, 120s (max)
        return when (attempts) {
            1 -> 5_000L
            2 -> 15_000L
            3 -> 45_000L
            else -> 120_000L
        }.coerceAtMost(120_000L)
    }
}

/**
 * Submission state enumeration
 */
enum class SubmissionState {
    IDLE,           // Ready to submit
    SUBMITTING,     // Currently submitting
    COOLDOWN        // Waiting before retry
}

/**
 * Auto-submission mode
 */
enum class AutoSubmissionMode {
    ENABLED,        // Auto-submit when phone is valid
    DISABLED,       // Never auto-submit
    MANUAL_ONLY     // Only submit on manual button press
}

/**
 * Composable to remember advanced auth form state
 */
@Composable
fun rememberAdvancedAuthFormState(): AdvancedAuthFormState {
    return remember { AdvancedAuthFormState() }
}

// Validation utilities (reused from original)
private fun validatePhoneNumber(number: String, countryCode: String): ValidationResult {
    return AuthFormValidator.validatePhoneNumber(number, countryCode)
}

private fun validateOtp(otp: String): ValidationResult {
    return AuthFormValidator.validateOtp(otp)
}

