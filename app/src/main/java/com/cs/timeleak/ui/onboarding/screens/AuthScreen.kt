package com.cs.timeleak.ui.onboarding.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.cs.timeleak.ui.auth.AuthViewModel
import com.cs.timeleak.ui.onboarding.components.*
import com.cs.timeleak.ui.onboarding.error.*
import com.cs.timeleak.ui.onboarding.form.*
import com.cs.timeleak.ui.onboarding.model.*
import com.cs.timeleak.ui.onboarding.provider.OnboardingContentProvider

/**
 * AuthScreen manages the phone number authentication flow during onboarding.
 * 
 * Features:
 * - Phone number input with real-time validation and formatting
 * - OTP verification with auto-submit and resend functionality
 * - State synchronization between AuthViewModel and AuthFormState
 * - Comprehensive error handling with user feedback
 * - Auto-submit logic with infinite loop prevention
 * 
 * Architecture:
 * - Uses AuthViewModel for Firebase authentication
 * - AuthFormState for local form state management
 * - ErrorStateManager for centralized error handling
 * - Component-based UI with reusable elements
 */
@Composable
fun AuthScreen(
    onAction: (OnboardingAction) -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val TAG = "AuthScreen"
    val context = LocalContext.current
    val activity = context as android.app.Activity
    val authState by authViewModel.authState.collectAsState()
    val formState = rememberAuthFormState()
    val errorManager = rememberErrorStateManager()
    
    // Sync waiting for OTP state from auth view model
    LaunchedEffect(authState.verificationId) {
        formState.updateWaitingForOtp(authState.verificationId != null)
    }
    
    // CRITICAL FIX: Sync phone number state between AuthViewModel and AuthFormState
    // This prevents infinite loops by ensuring both states are synchronized
    LaunchedEffect(authState.phoneNumber) {
        // Check if AuthViewModel cleared the phone number but FormState still has it
        if (authState.phoneNumber.isEmpty() && formState.phoneNumber.isNotEmpty()) {
            Log.i(TAG, "Syncing states: AuthViewModel cleared phone number, clearing FormState too")
            // This should trigger PhoneInputFields reset logic and break the infinite loop
            formState.updatePhoneNumber("")
        }
    }
    
    // Handle authentication success
    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            onAction(OnboardingAction.NextStep)
        }
    }
    
    // Handle errors - use a more stable approach to prevent infinite loops
    var lastErrorMessage by remember { mutableStateOf(null as String?) }
    LaunchedEffect(authState.error) {
        val currentError = authState.error
        if (currentError != null && currentError != lastErrorMessage) {
            lastErrorMessage = currentError
            // Clear submission progress to prevent infinite loops
            formState.clearSubmissionInProgress()
            // Show the error to user (verification state is already cleared by ViewModel)
            errorManager.setError(OnboardingError.AuthenticationError(currentError))
        } else if (currentError == null) {
            lastErrorMessage = null
        }
    }
    
    val stepContent = OnboardingContentProvider.getAuthContent()
    
    OnboardingPageLayout(
        modifier = modifier,
        header = {
            OnboardingHeader(step = stepContent)
        },
        content = {
            AuthScreenContent(
                stepContent = stepContent,
                formState = formState,
                authState = authState,
                errorManager = errorManager,
                onSubmitPhone = { phone, countryCode ->
                    authViewModel.updateCountryCode(countryCode)
                    authViewModel.updatePhoneNumber(phone)
                    authViewModel.sendVerificationCode(activity)
                },
                onSubmitOtp = { otp ->
                    authViewModel.updateOtp(otp)
                    authViewModel.verifyCode(activity)
                },
                onResendCode = {
                    authViewModel.sendVerificationCode(activity)
                },
                onAction = onAction
            )
        },
        bottomAction = {
            AuthScreenBottomAction(
                stepContent = stepContent,
                formState = formState,
                authState = authState,
                onAction = onAction,
                onSubmitPhone = { phone, countryCode ->
                    authViewModel.updateCountryCode(countryCode)
                    authViewModel.updatePhoneNumber(phone)
                    authViewModel.sendVerificationCode(activity)
                }
            )
        }
    )
}

/**
 * Content section for auth screen
 */
@Composable
private fun ColumnScope.AuthScreenContent(
    stepContent: OnboardingStep,
    formState: AuthFormState,
    authState: com.cs.timeleak.ui.auth.AuthState,
    errorManager: ErrorStateManager,
    onSubmitPhone: (String, String) -> Unit,
    onSubmitOtp: (String) -> Unit,
    onResendCode: () -> Unit,
    onAction: (OnboardingAction) -> Unit
) {
    val currentError by errorManager.currentError.collectAsState()
    
    // Show error if present
    currentError?.let { error ->
        OnboardingErrorCard(
            error = error,
            onRetry = { 
                errorManager.clearError()
                // Retry the last action
            },
            onDismiss = { errorManager.clearError() }
        )
    }
    
    // Loading state
    LoadingStateOverlay(
        isLoading = authState.isLoading,
        message = authState.loadingMessage
    )
    
    when {
        authState.isLoading -> {
            // Loading is handled by overlay above
        }
        
        authState.verificationId == null -> {
            // Phone number input form
            PhoneNumberForm(
                formState = formState,
                onSubmitPhone = onSubmitPhone,
                stepContent = stepContent
            )
        }
        
        else -> {
            // OTP verification
            OtpVerificationForm(
                formState = formState,
                onSubmitOtp = onSubmitOtp,
                onResendCode = onResendCode,
                authState = authState
            )
        }
    }
}

/**
 * Phone number input form component
 */
@Composable
private fun PhoneNumberForm(
    formState: AuthFormState,
    onSubmitPhone: (String, String) -> Unit,
    stepContent: OnboardingStep
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Phone input fields
        PhoneInputFields(
            formState = formState,
            onSubmit = { onSubmitPhone(formState.phoneNumber, formState.countryCode) }
        )
        
        // Privacy explanation
        val privacyPoints = stepContent.content.filter { it.type == ContentType.PRIVACY_POINT }
        if (privacyPoints.isNotEmpty()) {
            PrivacyExplanationCard(
                privacyPoints = privacyPoints,
                title = "Privacy First"
            )
        }
    }
}

/**
 * Phone input fields component
 */
@Composable
private fun PhoneInputFields(
    formState: AuthFormState,
    onSubmit: () -> Unit
) {
    val TAG = "PhoneInputFields"
    var hasAutoSubmitted by remember { mutableStateOf(false) }
    var lastSubmittedNumber by remember { mutableStateOf("") }

    // 🔧 KEY FIX: Reset auto-submit state when phone number is cleared
    LaunchedEffect(formState.phoneNumber) {
        if (formState.phoneNumber.isEmpty()) {
            Log.d(TAG, "📱 Phone number cleared - resetting auto-submit state")
            Log.d(TAG, "📱 Before reset: hasAutoSubmitted=$hasAutoSubmitted, lastSubmitted='$lastSubmittedNumber'")
            hasAutoSubmitted = false
            lastSubmittedNumber = ""
            Log.d(TAG, "📱 After reset: hasAutoSubmitted=$hasAutoSubmitted, lastSubmitted='$lastSubmittedNumber'")
        }
    }

    // Enhanced auto-submit logic
    LaunchedEffect(
        formState.phoneNumber,
        formState.canSubmitPhoneNumber,
        formState.isWaitingForOtp,
        formState.shouldShowPhoneError(),
        formState.isSubmissionInProgress // Add this dependency
    ) {
        val phoneNumber = formState.phoneNumber
        val hasError = formState.shouldShowPhoneError()
        
        Log.d(TAG, "Auto-submit LaunchedEffect triggered")

        // Reset auto-submit when user changes the number
        if (phoneNumber != lastSubmittedNumber && phoneNumber.isNotEmpty()) {
            Log.d(TAG, "Phone number changed from '$lastSubmittedNumber' to '$phoneNumber' - resetting hasAutoSubmitted")
            hasAutoSubmitted = false
        }

        // Check each condition individually for debugging
        val canSubmitCondition = formState.canSubmitPhoneNumber
        val notAutoSubmittedCondition = !hasAutoSubmitted
        val noErrorCondition = !hasError
        val notWaitingCondition = !formState.isWaitingForOtp
        val notEmptyCondition = phoneNumber.isNotEmpty()
        val length10Condition = phoneNumber.length == 10
        val differentNumberCondition = phoneNumber != lastSubmittedNumber
        val notInProgressCondition = !formState.isSubmissionInProgress

        // Only auto-submit if ALL conditions are met
        if (canSubmitCondition &&
            notAutoSubmittedCondition &&
            noErrorCondition &&
            notWaitingCondition &&
            notEmptyCondition &&  // This prevents auto-submit when cleared
            length10Condition &&  // Only submit complete numbers
            differentNumberCondition &&
            notInProgressCondition) { // Prevent during submission

            Log.d(TAG, "ALL CONDITIONS MET - Auto-submitting phone number: '$phoneNumber'")
            hasAutoSubmitted = true
            lastSubmittedNumber = phoneNumber
            formState.markPhoneSubmissionAttempted()
            onSubmit()
        } else {
            Log.d(TAG, "Auto-submit conditions not met - skipping submission")
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Country Code Input
            OutlinedTextField(
                value = formState.countryCode,
                onValueChange = { formState.updateCountryCode(it) },
                label = { Text("Country") },
                placeholder = { Text("+1") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
            
            // Phone Number Input
            OutlinedTextField(
                value = formState.phoneNumber,
                onValueChange = { formState.updatePhoneNumber(it) },
                label = { Text("Phone Number") },
                placeholder = { Text("555 - 123 - 4567") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = formState.shouldShowPhoneError(),
                visualTransformation = PhoneNumberVisualTransformation()
            )
        }
        
        // Show validation error only when we should display it to the user
        if (formState.shouldShowPhoneError()) {
            formState.phoneNumberValidation.getErrorMessage()?.let { errorMessage ->
                if (errorMessage.isNotEmpty()) {
                    InlineErrorText(
                        error = errorMessage
                    )
                }
            }
        }
    }
}

/**
 * OTP verification form component
 */
@Composable
private fun OtpVerificationForm(
    formState: AuthFormState,
    onSubmitOtp: (String) -> Unit,
    onResendCode: () -> Unit,
    authState: com.cs.timeleak.ui.auth.AuthState
) {
    val focusRequesters = List(6) { remember { FocusRequester() } }
    
    // Auto-submit when OTP is complete
    LaunchedEffect(formState.otp) {
        if (formState.canSubmitOtp) {
            formState.markOtpSubmissionAttempted()
            onSubmitOtp(formState.otp)
        }
    }
    
    // Countdown timer for resend code
    LaunchedEffect(Unit) {
        while(true) {
            if (formState.otpResendCooldown > 0) {
                kotlinx.coroutines.delay(1000L)
                formState.decrementResendCooldown()
            } else {
                kotlinx.coroutines.delay(500L)
            }
        }
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // OTP input fields
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in 0 until 6) {
                OutlinedTextField(
                    value = formState.otp.getOrNull(i)?.toString() ?: "",
                    onValueChange = { value ->
                        if (value.length <= 1 && value.all { it.isDigit() }) {
                            val newOtp = StringBuilder(formState.otp)
                            if (formState.otp.length > i) {
                                newOtp.setCharAt(i, value.getOrNull(0) ?: ' ')
                            } else if (value.isNotEmpty()) {
                                while (newOtp.length < i) newOtp.append(' ')
                                newOtp.append(value[0])
                            }
                            formState.updateOtp(newOtp.toString().replace(" ", ""))
                            if (value.isNotEmpty() && i < 5) {
                                focusRequesters[i + 1].requestFocus()
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .width(48.dp)
                        .padding(2.dp)
                        .focusRequester(focusRequesters[i]),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = formState.shouldShowOtpError()
                )
            }
        }
        
        // Show validation error only when we should display it to the user
        if (formState.shouldShowOtpError()) {
            formState.otpValidation.getErrorMessage()?.let { errorMessage ->
                if (errorMessage.isNotEmpty()) {
                    InlineErrorText(
                        error = errorMessage
                    )
                }
            }
        }
        
        // Action buttons
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnboardingButton(
                text = "Verify Code",
                onClick = { 
                    formState.markOtpSubmissionAttempted()
                    onSubmitOtp(formState.otp) 
                },
                enabled = formState.canSubmitOtp
            )
            
            TextButton(
                onClick = onResendCode,
                enabled = formState.canResendOtp
            ) {
                Text(
                    if (formState.otpResendCooldown > 0) {
                        "Resend Code (${formState.otpResendCooldown}s)"
                    } else {
                        "Resend Code"
                    }
                )
            }
        }
    }
}

/**
 * Bottom action section for auth screen
 */
@Composable
private fun AuthScreenBottomAction(
    stepContent: OnboardingStep,
    formState: AuthFormState,
    authState: com.cs.timeleak.ui.auth.AuthState,
    onAction: (OnboardingAction) -> Unit,
    onSubmitPhone: (String, String) -> Unit
) {
    // Only show button for phone number input
    if (!authState.isLoading && authState.verificationId == null) {
        OnboardingButton(
            text = stepContent.buttonText,
            onClick = { 
                formState.markPhoneSubmissionAttempted()
                onSubmitPhone(formState.phoneNumber, formState.countryCode) 
            },
            enabled = formState.canSubmitPhoneNumber
        )
    }
}

/**
 * Phone number visual transformation for formatting display
 */
private class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val formatted = formatPhoneNumberDisplay(digits)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return when {
                    offset <= 0 -> 0
                    offset <= 3 -> offset
                    offset <= 6 -> offset + 3 // Account for " - "
                    offset <= 10 -> offset + 6 // Account for both " - "
                    else -> formatted.length
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 0 -> 0
                    offset <= 3 -> offset
                    offset <= 7 -> offset - 3 // Remove " - "
                    offset <= 13 -> offset - 6 // Remove both " - "
                    else -> digits.length
                }
            }
        }

        return TransformedText(
            androidx.compose.ui.text.AnnotatedString(formatted),
            offsetMapping
        )
    }
}

/**
 * Format phone number for display
 */
private fun formatPhoneNumberDisplay(digits: String): String {
    return when (digits.length) {
        0 -> ""
        1, 2, 3 -> digits
        4, 5, 6 -> "${digits.substring(0, 3)} - ${digits.substring(3)}"
        7, 8, 9, 10 -> "${digits.substring(0, 3)} - ${digits.substring(3, 6)} - ${digits.substring(6)}"
        else -> "${digits.substring(0, 3)} - ${digits.substring(3, 6)} - ${digits.substring(6, 10)}"
    }
}

/**
 * Debug wrapper version with navigation controls
 */
@Composable
fun AuthScreenWithDebug(
    onAction: (OnboardingAction) -> Unit,
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onForward: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    currentStep: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        AuthScreen(
            onAction = onAction,
            authViewModel = authViewModel
        )
        
        // Debug navigation overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DEBUG: $currentStep",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Debug Back",
                        tint = if (canGoBack) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
                
                IconButton(
                    onClick = onForward,
                    enabled = canGoForward
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Debug Forward",
                        tint = if (canGoForward) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
            }
        }
    }
}
