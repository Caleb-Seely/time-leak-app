package com.cs.timeleak.ui.onboarding.provider

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import com.cs.timeleak.ui.onboarding.model.*

/**
 * Provides content for all onboarding steps.
 * Centralizes all text, configuration, and structure for easy modification.
 */
object OnboardingContentProvider {
    
    private const val TOTAL_STEPS = 4
    
    /**
     * Gets content for the intro/welcome page
     */
    fun getIntroContent(): OnboardingStep {
        return OnboardingStep(
            id = "intro",
            title = "TimeLeak",
            subtitle = "Gentle accountability for your digital habits",
            stepNumber = 1,
            totalSteps = TOTAL_STEPS,
            buttonText = "Start Your Journey",
            content = listOf(
                // Value proposition card
                OnboardingContent(
                    type = ContentType.VALUE_PROPOSITION_CARD,
                    title = "Privacy-First Accountability",
                    description = "Share only your total daily screen time—no app breakdowns, no live tracking, no personal data collection. Just a gentle daily reminder that your time is valuable."
                ),
                
                // Feature highlights
                OnboardingContent(
                    type = ContentType.FEATURE_HIGHLIGHT,
                    title = "Simple",
                    description = "No feeds, no dopamine loops"
                ),
                OnboardingContent(
                    type = ContentType.FEATURE_HIGHLIGHT,
                    title = "Growth",
                    description = "A mirror, not a mandate"
                ),
                OnboardingContent(
                    type = ContentType.FEATURE_HIGHLIGHT,
                    title = "Together",
                    description = "Accountability with friends"
                ),
                
                // Audience targeting
                OnboardingContent(
                    type = ContentType.AUDIENCE_TAG,
                    title = "Perfect for:",
                    description = "Doomscroll refugees • Accountability partners • Mindful parents • Digital minimalists"
                ),
                
                // Setup info
                OnboardingContent(
                    type = ContentType.INFO_TEXT,
                    description = "Setup takes less than 2 minutes"
                )
            )
        )
    }
    
    /**
     * Gets content for the how it works page
     */
    fun getHowItWorksContent(): OnboardingStep {
        return OnboardingStep(
            id = "how_it_works",
            title = "TimeLeak",
            subtitle = "Privacy-first by design",
            stepNumber = 2,
            totalSteps = TOTAL_STEPS,
            buttonText = "Continue",
            content = listOf(
                OnboardingContent(
                    type = ContentType.HOW_IT_WORKS_POINT,
                    title = "We don't collect personal data.",
                    description = "Your screen time is tied only to your phone number — and unless someone knows it, your data is invisible to them.",
                    gradient = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)) // Blue-500 to Purple-500
                ),
                OnboardingContent(
                    type = ContentType.HOW_IT_WORKS_POINT,
                    title = "We don't track what you do.",
                    description = "Your phone itself calculates your screen time. Our app just uploads the daily total — not what you did, not when, not where.",
                    gradient = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)) // Purple-500 to Pink-500
                ),
                OnboardingContent(
                    type = ContentType.HOW_IT_WORKS_POINT,
                    title = "Your data disappears every day.",
                    description = "At midnight, yesterday's total is replaced. We don't keep long-term history so no lookups for any other day is even possible.",
                    gradient = listOf(Color(0xFFEC4899), Color(0xFFEF4444)) // Pink-500 to Red-500
                ),
                OnboardingContent(
                    type = ContentType.HOW_IT_WORKS_POINT,
                    title = "Set and forget.",
                    description = "Install it once, grant permission, and you're done. No need to open the app. Just live your life — and let your phone speak for itself.",
                    gradient = listOf(Color(0xFFEF4444), Color(0xFFF97316)) // Red-500 to Orange-500
                )
            )
        )
    }
    
    /**
     * Gets content for the authentication page
     */
    fun getAuthContent(): OnboardingStep {
        return OnboardingStep(
            id = "auth",
            title = "TimeLeak",
            subtitle = "Sign in with your phone number to track your screen time",
            stepNumber = 3,
            totalSteps = TOTAL_STEPS,
            buttonText = "Send Verification Code",
            content = listOf(
                // Privacy explanation points
                OnboardingContent(
                    type = ContentType.PRIVACY_POINT,
                    icon = Icons.Filled.Phone,
                    description = "We only ask for your phone number — no names, emails, or other info"
                ),
                OnboardingContent(
                    type = ContentType.PRIVACY_POINT,
                    icon = Icons.Filled.Link,
                    description = "Your number links your screen time to you, but it's never advertised"
                ),
                OnboardingContent(
                    type = ContentType.PRIVACY_POINT,
                    icon = Icons.Filled.Insights,
                    description = "Anyone who knows your number can see yesterday's total — that's the point"
                ),
                OnboardingContent(
                    type = ContentType.PRIVACY_POINT,
                    icon = Icons.Filled.Visibility,
                    description = "Just enough for accountability, not enough for concern"
                )
            )
        )
    }
    
    /**
     * Gets content for the permissions page
     */
    fun getPermissionsContent(): OnboardingStep {
        return OnboardingStep(
            id = "permissions",
            title = "TimeLeak",
            subtitle = "Setup your privacy-first accountability",
            stepNumber = 4,
            totalSteps = TOTAL_STEPS,
            buttonText = "Grant Permission",
            content = listOf(
                OnboardingContent(
                    type = ContentType.INFO_TEXT,
                    description = "To show you meaningful insights about your device usage, we need permission to access screen time data."
                ),
                
                // Privacy points for permissions
                OnboardingContent(
                    type = ContentType.PRIVACY_POINT,
                    icon = Icons.Outlined.Timer,
                    title = "Privacy First",
                    description = "Only total time data is published"
                ),
                OnboardingContent(
                    type = ContentType.PRIVACY_POINT,
                    icon = Icons.Outlined.Block,
                    description = "No invasive data is shared"
                ),
                OnboardingContent(
                    type = ContentType.PRIVACY_POINT,
                    icon = Icons.Outlined.CloudOff,
                    description = "App specific data stays on your device"
                ),
                
                OnboardingContent(
                    type = ContentType.INFO_TEXT,
                    description = "This is the last step in setting up TimeLeak"
                )
            )
        )
    }
    
    /**
     * Gets content for the completion page (future use)
     */
    fun getCompleteContent(): OnboardingStep {
        return OnboardingStep(
            id = "complete",
            title = "TimeLeak",
            subtitle = "You're all set!",
            stepNumber = 5,
            totalSteps = TOTAL_STEPS,
            buttonText = "Get Started",
            content = listOf(
                OnboardingContent(
                    type = ContentType.VALUE_PROPOSITION_CARD,
                    title = "Welcome to TimeLeak",
                    description = "Your privacy-first screen time accountability starts now. Check back daily to see your progress."
                )
            )
        )
    }
    
    /**
     * Gets content for a specific destination
     */
    fun getContentForDestination(destination: OnboardingDestination): OnboardingStep {
        return when (destination) {
            OnboardingDestination.Intro -> getIntroContent()
            OnboardingDestination.HowItWorks -> getHowItWorksContent()
            OnboardingDestination.Auth -> getAuthContent()
            OnboardingDestination.Permissions -> getPermissionsContent()
            OnboardingDestination.Complete -> getCompleteContent()
        }
    }
}
