package com.cs.timeleak.ui.onboarding.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cs.timeleak.ui.onboarding.components.*
import com.cs.timeleak.ui.onboarding.model.*
import com.cs.timeleak.ui.onboarding.provider.OnboardingContentProvider

/**
 * HowItWorksPage using component architecture.
 * Demonstrates data-driven content rendering with reusable components.
 */
@Composable
fun HowItWorksPage(
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val stepContent = OnboardingContentProvider.getHowItWorksContent()
    
    OnboardingPageLayout(
        modifier = modifier,
        header = {
            OnboardingHeader(step = stepContent)
        },
        content = {
            HowItWorksContent(
                stepContent = stepContent,
                onAction = onAction
            )
        },
        bottomAction = {
            HowItWorksBottomAction(
                stepContent = stepContent,
                onAction = onAction
            )
        }
    )
}

/**
 * Content section for how it works page
 */
@Composable
private fun ColumnScope.HowItWorksContent(
    stepContent: OnboardingStep,
    onAction: (OnboardingAction) -> Unit
) {
    // Get the how it works points from content
    val howItWorksPoints = stepContent.content.filter { it.type == ContentType.HOW_IT_WORKS_POINT }
    
    // Render all points in a card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            howItWorksPoints.forEachIndexed { index, point ->
                HowItWorksPoint(
                    number = index + 1,
                    title = point.title ?: "",
                    description = point.description ?: "",
                    gradientColors = point.gradient
                )
            }
        }
    }
}

/**
 * Bottom action section for how it works page
 */
@Composable
private fun HowItWorksBottomAction(
    stepContent: OnboardingStep,
    onAction: (OnboardingAction) -> Unit
) {
    OnboardingButton(
        text = stepContent.buttonText,
        onClick = { onAction(OnboardingAction.NextStep) }
    )
}

/**
 * Debug wrapper version with navigation controls
 */
@Composable
fun HowItWorksPageWithDebug(
    onAction: (OnboardingAction) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    currentStep: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        HowItWorksPage(onAction = onAction)
        
        // Debug navigation overlay - reusing the same pattern from IntroPage
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopCenter)
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
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
