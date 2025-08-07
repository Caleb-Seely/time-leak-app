package com.cs.timeleak.ui.onboarding.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a complete onboarding step with all necessary content and metadata
 */
data class OnboardingStep(
    val id: String,
    val title: String,
    val subtitle: String,
    val content: List<OnboardingContent>,
    val buttonText: String,
    val stepNumber: Int,
    val totalSteps: Int
)

/**
 * Individual content items within an onboarding step
 */
data class OnboardingContent(
    val type: ContentType,
    val title: String? = null,
    val description: String? = null,
    val icon: ImageVector? = null,
    val gradient: List<Color>? = null,
    val imageResource: Int? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Types of content that can be rendered in onboarding steps
 */
enum class ContentType {
    HEADER,
    VALUE_PROPOSITION_CARD,
    FEATURE_HIGHLIGHT,
    PRIVACY_POINT,
    HOW_IT_WORKS_POINT,
    AUDIENCE_TAG,
    INFO_TEXT,
    FORM_FIELD
}

/**
 * Navigation destinations for the onboarding flow
 */
sealed class OnboardingDestination(val route: String, val stepNumber: Int) {
    object Intro : OnboardingDestination("intro", 1)
    object HowItWorks : OnboardingDestination("how_it_works", 2)
    object Auth : OnboardingDestination("auth", 3)
    object Permissions : OnboardingDestination("permissions", 4)
    object Complete : OnboardingDestination("complete", 5)

    companion object {
        fun fromStepNumber(stepNumber: Int): OnboardingDestination {
            return when (stepNumber) {
                1 -> Intro
                2 -> HowItWorks
                3 -> Auth
                4 -> Permissions
                else -> Complete
            }
        }
        
        fun getAllDestinations(): List<OnboardingDestination> {
            return listOf(Intro, HowItWorks, Auth, Permissions, Complete)
        }
    }
}

/**
 * Actions that can be triggered from onboarding content
 */
sealed class OnboardingAction {
    object NextStep : OnboardingAction()
    object PreviousStep : OnboardingAction()
    data class NavigateToStep(val destination: OnboardingDestination) : OnboardingAction()
    data class SubmitAuth(val phoneNumber: String, val countryCode: String) : OnboardingAction()
    data class VerifyOtp(val otp: String) : OnboardingAction()
    object RequestPermission : OnboardingAction()
    object CompleteOnboarding : OnboardingAction()
}

/**
 * UI state for the entire onboarding flow
 */
data class OnboardingUiState(
    val currentDestination: OnboardingDestination = OnboardingDestination.Intro,
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val error: OnboardingError? = null,
    val canNavigateBack: Boolean = false,
    val canNavigateForward: Boolean = true,
    val progress: Float = 0.25f
)

/**
 * Error types that can occur during onboarding
 */
sealed class OnboardingError(
    val message: String, 
    val isRetryable: Boolean = true,
    val actionLabel: String? = null
) {
    data class NetworkError(val details: String) : OnboardingError(
        message = "Network error: $details",
        isRetryable = true,
        actionLabel = "Retry"
    )
    
    data class PermissionError(val details: String) : OnboardingError(
        message = "Permission error: $details",
        isRetryable = true,
        actionLabel = "Try Again"
    )
    
    data class AuthenticationError(val details: String) : OnboardingError(
        message = "Authentication failed: $details",
        isRetryable = true,
        actionLabel = "Retry"
    )
    
    data class ValidationError(val details: String) : OnboardingError(
        message = details,
        isRetryable = false,
        actionLabel = null
    )
    
    data class UnknownError(val details: String) : OnboardingError(
        message = "An unexpected error occurred: $details",
        isRetryable = true,
        actionLabel = "Retry"
    )
}

/**
 * Validation result for form inputs
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
    
    fun isValid(): Boolean = this is Success
    fun getErrorMessage(): String? = if (this is Error) message else null
}
