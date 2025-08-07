package com.cs.timeleak.ui.onboarding.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.cs.timeleak.ui.onboarding.model.*
import com.cs.timeleak.ui.onboarding.provider.OnboardingContentProvider

/**
 * ViewModel for managing onboarding flow navigation and state
 */
class OnboardingViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    private val navigationHistory = mutableListOf<OnboardingDestination>()
    
    init {
        // Initialize with first step
        updateProgress()
    }
    
    /**
     * Navigate to the next step in the onboarding flow
     */
    fun navigateNext() {
        val currentDestination = _uiState.value.currentDestination
        val nextDestination = getNextDestination(currentDestination)
        
        if (nextDestination != null) {
            navigateTo(nextDestination)
        }
    }
    
    /**
     * Navigate back to the previous step
     */
    fun navigateBack(): Boolean {
        return if (navigationHistory.isNotEmpty()) {
            val previousDestination = navigationHistory.removeLastOrNull()
            previousDestination?.let { destination ->
                _uiState.value = _uiState.value.copy(
                    currentDestination = destination,
                    error = null
                )
                updateNavigationState()
                updateProgress()
                true
            } ?: false
        } else {
            false
        }
    }
    
    /**
     * Navigate directly to a specific destination
     */
    fun navigateTo(destination: OnboardingDestination) {
        val currentDestination = _uiState.value.currentDestination
        
        // Add current destination to history if it's different
        if (currentDestination != destination) {
            navigationHistory.add(currentDestination)
        }
        
        _uiState.value = _uiState.value.copy(
            currentDestination = destination,
            error = null
        )
        
        updateNavigationState()
        updateProgress()
    }
    
    /**
     * Skip directly to a specific step (for debug purposes)
     */
    fun skipToStep(stepNumber: Int) {
        val destination = OnboardingDestination.fromStepNumber(stepNumber)
        navigateTo(destination)
    }
    
    /**
     * Handle onboarding actions
     */
    fun handleAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.NextStep -> navigateNext()
            is OnboardingAction.PreviousStep -> navigateBack()
            is OnboardingAction.NavigateToStep -> navigateTo(action.destination)
            is OnboardingAction.CompleteOnboarding -> completeOnboarding()
            else -> {
                // Delegate other actions to specific handlers
                // This will be expanded in Phase 2 when we add form handling
            }
        }
    }
    
    /**
     * Set loading state
     */
    fun setLoading(isLoading: Boolean, message: String? = null) {
        _uiState.value = _uiState.value.copy(
            isLoading = isLoading,
            loadingMessage = message
        )
    }
    
    /**
     * Set error state
     */
    fun setError(error: OnboardingError?) {
        _uiState.value = _uiState.value.copy(
            error = error,
            isLoading = false
        )
    }
    
    /**
     * Clear any current error
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Get content for the current step
     */
    fun getCurrentStepContent(): OnboardingStep {
        return OnboardingContentProvider.getContentForDestination(_uiState.value.currentDestination)
    }
    
    /**
     * Complete the onboarding flow
     */
    private fun completeOnboarding() {
        navigateTo(OnboardingDestination.Complete)
    }
    
    /**
     * Get the next destination in the flow
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
    
    /**
     * Update navigation state based on current position
     */
    private fun updateNavigationState() {
        val current = _uiState.value.currentDestination
        val canGoBack = navigationHistory.isNotEmpty() || current != OnboardingDestination.Intro
        val canGoForward = current != OnboardingDestination.Complete
        
        _uiState.value = _uiState.value.copy(
            canNavigateBack = canGoBack,
            canNavigateForward = canGoForward
        )
    }
    
    /**
     * Update progress based on current step
     */
    private fun updateProgress() {
        val progress = when (_uiState.value.currentDestination) {
            OnboardingDestination.Intro -> 0.25f
            OnboardingDestination.HowItWorks -> 0.5f
            OnboardingDestination.Auth -> 0.75f
            OnboardingDestination.Permissions -> 1.0f
            OnboardingDestination.Complete -> 1.0f
        }
        
        _uiState.value = _uiState.value.copy(progress = progress)
    }
    
    /**
     * Reset the onboarding flow to the beginning
     */
    fun reset() {
        navigationHistory.clear()
        _uiState.value = OnboardingUiState()
        updateProgress()
    }
    
    /**
     * Get debug information about current state
     */
    fun getDebugInfo(): Map<String, String> {
        val current = _uiState.value.currentDestination
        return mapOf(
            "current_step" to current.route,
            "step_number" to "${current.stepNumber}/4",
            "can_go_back" to _uiState.value.canNavigateBack.toString(),
            "can_go_forward" to _uiState.value.canNavigateForward.toString(),
            "history_size" to navigationHistory.size.toString(),
            "progress" to "${(_uiState.value.progress * 100).toInt()}%"
        )
    }
}
