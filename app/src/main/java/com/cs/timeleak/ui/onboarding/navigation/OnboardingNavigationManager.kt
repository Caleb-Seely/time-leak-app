package com.cs.timeleak.ui.onboarding.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.cs.timeleak.ui.onboarding.model.OnboardingDestination

/**
 * Centralized navigation state manager for onboarding flow.
 * Handles navigation history, validation, and state transitions.
 */
class OnboardingNavigationManager {
    
    private val _currentDestination = MutableStateFlow<OnboardingDestination>(OnboardingDestination.Intro)
    val currentDestination: StateFlow<OnboardingDestination> = _currentDestination.asStateFlow()
    
    private val navigationHistory = mutableListOf<OnboardingDestination>()
    
    /**
     * Navigate to a specific destination
     */
    fun navigateTo(destination: OnboardingDestination): NavigationResult {
        val currentDestination = _currentDestination.value
        
        // Validate navigation is allowed
        val validationResult = validateNavigation(currentDestination, destination)
        if (validationResult != NavigationResult.Success) {
            return validationResult
        }
        
        // Add current destination to history if it's different
        if (currentDestination != destination) {
            navigationHistory.add(currentDestination)
        }
        
        _currentDestination.value = destination
        return NavigationResult.Success
    }
    
    /**
     * Navigate back to previous destination
     */
    fun navigateBack(): NavigationResult {
        return if (navigationHistory.isNotEmpty()) {
            val previousDestination = navigationHistory.removeLastOrNull()
            previousDestination?.let { destination ->
                _currentDestination.value = destination
                NavigationResult.Success
            } ?: NavigationResult.Error("No previous destination in history")
        } else {
            NavigationResult.Error("Cannot navigate back from ${_currentDestination.value.route}")
        }
    }
    
    /**
     * Navigate to next step in the flow
     */
    fun navigateNext(): NavigationResult {
        val current = _currentDestination.value
        val nextDestination = getNextDestination(current)
        
        return if (nextDestination != null) {
            navigateTo(nextDestination)
        } else {
            NavigationResult.Error("No next destination available from ${current.route}")
        }
    }
    
    /**
     * Skip to a specific step (for debug purposes)
     */
    fun skipToStep(stepNumber: Int): NavigationResult {
        val destination = OnboardingDestination.fromStepNumber(stepNumber)
        return navigateTo(destination)
    }
    
    /**
     * Check if navigation back is possible
     */
    fun canNavigateBack(): Boolean {
        return navigationHistory.isNotEmpty() || _currentDestination.value != OnboardingDestination.Intro
    }
    
    /**
     * Check if navigation forward is possible
     */
    fun canNavigateForward(): Boolean {
        return getNextDestination(_currentDestination.value) != null
    }
    
    /**
     * Reset navigation to initial state
     */
    fun reset() {
        navigationHistory.clear()
        _currentDestination.value = OnboardingDestination.Intro
    }
    
    /**
     * Get navigation progress as percentage
     */
    fun getProgress(): Float {
        return when (_currentDestination.value) {
            OnboardingDestination.Intro -> 0.25f
            OnboardingDestination.HowItWorks -> 0.5f
            OnboardingDestination.Auth -> 0.75f
            OnboardingDestination.Permissions -> 1.0f
            OnboardingDestination.Complete -> 1.0f
        }
    }
    
    /**
     * Get current navigation state for debugging
     */
    fun getNavigationState(): NavigationState {
        return NavigationState(
            currentDestination = _currentDestination.value,
            canGoBack = canNavigateBack(),
            canGoForward = canNavigateForward(),
            progress = getProgress(),
            historySize = navigationHistory.size,
            history = navigationHistory.toList()
        )
    }
    
    /**
     * Validate if navigation from source to target is allowed
     */
    private fun validateNavigation(
        from: OnboardingDestination, 
        to: OnboardingDestination
    ): NavigationResult {
        // Define allowed navigation transitions
        val allowedTransitions = mapOf(
            OnboardingDestination.Intro to setOf(
                OnboardingDestination.HowItWorks,
                OnboardingDestination.Auth, // Allow skip for debug
                OnboardingDestination.Permissions, // Allow skip for debug
                OnboardingDestination.Complete // Allow skip for debug
            ),
            OnboardingDestination.HowItWorks to setOf(
                OnboardingDestination.Intro,
                OnboardingDestination.Auth,
                OnboardingDestination.Permissions, // Allow skip for debug
                OnboardingDestination.Complete // Allow skip for debug
            ),
            OnboardingDestination.Auth to setOf(
                OnboardingDestination.Intro,
                OnboardingDestination.HowItWorks,
                OnboardingDestination.Permissions,
                OnboardingDestination.Complete // Allow skip for debug
            ),
            OnboardingDestination.Permissions to setOf(
                OnboardingDestination.Intro,
                OnboardingDestination.HowItWorks,
                OnboardingDestination.Auth,
                OnboardingDestination.Complete
            ),
            OnboardingDestination.Complete to setOf(
                OnboardingDestination.Intro, // Allow reset
                OnboardingDestination.HowItWorks,
                OnboardingDestination.Auth,
                OnboardingDestination.Permissions
            )
        )
        
        val allowed = allowedTransitions[from]?.contains(to) ?: false
        
        return if (allowed) {
            NavigationResult.Success
        } else {
            NavigationResult.Error("Navigation from ${from.route} to ${to.route} is not allowed")
        }
    }
    
    /**
     * Get the next destination in the normal flow
     */
    private fun getNextDestination(current: OnboardingDestination): OnboardingDestination? {
        return when (current) {
            OnboardingDestination.Intro -> OnboardingDestination.HowItWorks
            OnboardingDestination.HowItWorks -> OnboardingDestination.Auth
            OnboardingDestination.Auth -> OnboardingDestination.Permissions
            OnboardingDestination.Permissions -> OnboardingDestination.Complete
            OnboardingDestination.Complete -> null // End of flow
        }
    }
}

/**
 * Result of a navigation operation
 */
sealed class NavigationResult {
    object Success : NavigationResult()
    data class Error(val message: String) : NavigationResult()
}

/**
 * Complete navigation state for debugging and UI updates
 */
data class NavigationState(
    val currentDestination: OnboardingDestination,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val progress: Float,
    val historySize: Int,
    val history: List<OnboardingDestination>
)
