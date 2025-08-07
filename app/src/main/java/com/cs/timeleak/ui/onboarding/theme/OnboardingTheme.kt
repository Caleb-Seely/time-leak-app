package com.cs.timeleak.ui.onboarding.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Theme constants and styling for onboarding screens
 */
object OnboardingTheme {
    
    // Spacing and dimensions
    val cardElevation = 4.dp
    val cardCornerRadius = 12.dp
    val standardPadding = 20.dp
    val buttonHeight = 52.dp
    val iconSize = 96.dp
    val featureIconSize = 16.dp
    val privacyIconSize = 18.dp
    val bottomActionPadding = 16.dp
    val headerBottomPadding = 32.dp
    
    // Shapes
    val buttonShape = RoundedCornerShape(cardCornerRadius)
    val cardShape = RoundedCornerShape(cardCornerRadius)
    
    // Colors
    object Colors {
        val gradientStart = Color(0xFF2196F3) // Blue
        val gradientEnd = Color(0xFF9C27B0) // Purple
    }
    
    /**
     * Primary gradient used for app title and branding
     */
    @Composable
    fun primaryGradient(): Brush = Brush.linearGradient(
        colors = listOf(Colors.gradientStart, Colors.gradientEnd),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(400f, 0f)
    )
    
    /**
     * Standard card colors for surface variants
     */
    @Composable
    fun surfaceCardColors() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
    
    /**
     * Primary container card colors
     */
    @Composable
    fun primaryContainerCardColors() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    )
    
    /**
     * Secondary container card colors with transparency
     */
    @Composable
    fun secondaryContainerCardColors() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    )
    
    /**
     * Privacy card colors with primary container and transparency
     */
    @Composable
    fun privacyCardColors() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    )
    
    /**
     * Standard card elevation
     */
    @Composable
    fun standardCardElevation() = CardDefaults.cardElevation(
        defaultElevation = cardElevation
    )
    
    /**
     * Outlined card border
     */
    @Composable
    fun outlinedCardBorder() = CardDefaults.outlinedCardBorder()
    
    /**
     * Privacy card border with primary color
     */
    @Composable
    fun privacyCardBorder() = androidx.compose.foundation.BorderStroke(
        width = 1.dp, 
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    )
}

/**
 * Configuration for onboarding behavior and features
 */
data class OnboardingConfiguration(
    val enableDebugNavigation: Boolean = false,
    val autoSubmitPhoneNumber: Boolean = true,
    val autoSubmitOtp: Boolean = true,
    val permissionCheckInterval: Long = 500L,
    val maxPermissionRetries: Int = 3,
    val enableAnimations: Boolean = true,
    val animationDurationMs: Int = 300
) {
    companion object {
        /**
         * Default configuration for production builds
         */
        val Default = OnboardingConfiguration()
        
        /**
         * Debug configuration with enhanced features
         */
        val Debug = OnboardingConfiguration(
            enableDebugNavigation = true,
            autoSubmitPhoneNumber = false,
            autoSubmitOtp = false
        )
    }
}
