package com.cs.timeleak.ui.onboarding.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs.timeleak.R
import com.cs.timeleak.ui.onboarding.components.*
import com.cs.timeleak.ui.onboarding.model.*
import com.cs.timeleak.ui.onboarding.provider.OnboardingContentProvider

/**
 * IntroPage using component architecture.
 * This demonstrates how content is data-driven and components are reusable.
 */
@Composable
fun IntroPage(
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val stepContent = OnboardingContentProvider.getIntroContent()
    
    OnboardingPageLayout(
        modifier = modifier,
        header = {
            OnboardingHeader(step = stepContent)
        },
        content = {
            IntroPageContent(
                stepContent = stepContent,
                onAction = onAction
            )
        },
        bottomAction = {
            IntroPageBottomAction(
                stepContent = stepContent,
                onAction = onAction
            )
        }
    )
}

/**
 * Content section for intro page
 */
@Composable
private fun ColumnScope.IntroPageContent(
    stepContent: OnboardingStep,
    onAction: (OnboardingAction) -> Unit
) {
    // Find different types of content from the step
    val valueProposition = stepContent.content.find { it.type == ContentType.VALUE_PROPOSITION_CARD }
    val featureHighlights = stepContent.content.filter { it.type == ContentType.FEATURE_HIGHLIGHT }
    val audienceTag = stepContent.content.find { it.type == ContentType.AUDIENCE_TAG }
    val infoText = stepContent.content.find { it.type == ContentType.INFO_TEXT }
    
    // Value proposition card
    valueProposition?.let { content ->
        ValuePropositionCard(content = content)
    }
    
    // Feature highlights in a row
    if (featureHighlights.isNotEmpty()) {
        FeatureHighlights(
            features = featureHighlights.map { feature ->
                // Add the appropriate image resource for each feature
                feature.copy(
                    imageResource = when (feature.title) {
                        "Simple" -> R.drawable.baseline_check_circle_24
                        "Growth" -> R.drawable.baseline_spa_24
                        "Together" -> R.drawable.baseline_group_24
                        else -> null
                    }
                )
            }
        )
    }
    
    // Audience targeting
    audienceTag?.let { content ->
        AudienceTagSection(content = content)
    }
}

/**
 * Bottom action section for intro page
 */
@Composable
private fun IntroPageBottomAction(
    stepContent: OnboardingStep,
    onAction: (OnboardingAction) -> Unit
) {
    val infoText = stepContent.content.find { it.type == ContentType.INFO_TEXT }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Info text above button
        infoText?.description?.let { description ->
            androidx.compose.material3.Text(
                text = description,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Main action button
        OnboardingButton(
            text = stepContent.buttonText,
            onClick = { onAction(OnboardingAction.NextStep) }
        )
    }
}

/**
 * Audience tag section component
 */
@Composable
private fun AudienceTagSection(
    content: OnboardingContent,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        content.title?.let { title ->
            androidx.compose.material3.Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        content.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Debug wrapper version with navigation controls
 */
@Composable
fun IntroPageWithDebug(
    onAction: (OnboardingAction) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    currentStep: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        IntroPage(onAction = onAction)
        
        // Debug navigation overlay
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopCenter)
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                text = "DEBUG: $currentStep",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                androidx.compose.material3.IconButton(
                    onClick = onBack,
                    enabled = canGoBack
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Debug Back",
                        tint = if (canGoBack) {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
                
                androidx.compose.material3.IconButton(
                    onClick = onForward,
                    enabled = canGoForward
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Debug Forward",
                        tint = if (canGoForward) {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
            }
        }
    }
}
