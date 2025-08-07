package com.cs.timeleak.ui.onboarding.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs.timeleak.R
import com.cs.timeleak.ui.onboarding.model.*
import com.cs.timeleak.ui.onboarding.theme.OnboardingTheme

/**
 * Standardized page layout for all onboarding screens
 */
@Composable
fun OnboardingPageLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    bottomAction: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = OnboardingTheme.standardPadding)
                .padding(top = 40.dp, bottom = OnboardingTheme.standardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fixed header at top
            header()
            
            // Scrollable content in middle
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OnboardingTheme.standardPadding),
                content = content
            )
            
            // Fixed bottom action
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = OnboardingTheme.bottomActionPadding)
            ) {
                bottomAction()
            }
        }
    }
}

/**
 * Reusable header component with app icon, title, and subtitle
 */
@Composable
fun OnboardingHeader(
    step: OnboardingStep,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = OnboardingTheme.headerBottomPadding)
    ) {
        Image(
            painter = painterResource(R.drawable.icon),
            contentDescription = "TimeLeak App Icon",
            modifier = Modifier.size(OnboardingTheme.iconSize)
        )

        GradientText(
            text = step.title
        )

        Text(
            text = step.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * Gradient text component for app title
 */
@Composable
fun GradientText(
    text: String, 
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(
            brush = OnboardingTheme.primaryGradient(),
            fontWeight = FontWeight.Bold,
            fontSize = fontSize
        ),
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}

/**
 * Standardized feature card component
 */
@Composable
fun FeatureCard(
    content: OnboardingContent,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = OnboardingTheme.surfaceCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content.title?.let { title ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    content.imageResource?.let { resourceId ->
                        Image(
                            painter = painterResource(resourceId),
                            contentDescription = null,
                            modifier = Modifier.size(OnboardingTheme.featureIconSize)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            content.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * Value proposition card with primary container styling
 */
@Composable
fun ValuePropositionCard(
    content: OnboardingContent,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = OnboardingTheme.primaryContainerCardColors(),
        elevation = OnboardingTheme.standardCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(OnboardingTheme.standardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            content.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Privacy point component with icon and text
 */
@Composable
fun PrivacyPoint(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(OnboardingTheme.privacyIconSize)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * How it works point with gradient circle and content
 */
@Composable
fun HowItWorksPoint(
    number: Int,
    title: String,
    description: String,
    gradientColors: List<Color>? = null,
    modifier: Modifier = Modifier
) {
    val colors = gradientColors ?: listOf(
        OnboardingTheme.Colors.gradientStart,
        OnboardingTheme.Colors.gradientEnd
    )
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gradient circle with number
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    brush = Brush.linearGradient(colors = colors),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Title and description
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * Standardized onboarding button
 */
@Composable
fun OnboardingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(OnboardingTheme.buttonHeight),
        shape = OnboardingTheme.buttonShape
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * Feature highlights row with three columns
 */
@Composable
fun FeatureHighlights(
    features: List<OnboardingContent>,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        features.forEach { feature ->
            FeatureCard(
                content = feature,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Privacy explanation card with multiple privacy points
 */
@Composable
fun PrivacyExplanationCard(
    privacyPoints: List<OnboardingContent>,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = OnboardingTheme.privacyCardColors(),
        border = OnboardingTheme.privacyCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(OnboardingTheme.standardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            title?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            privacyPoints.forEach { point ->
                point.icon?.let { icon ->
                    PrivacyPoint(
                        icon = icon,
                        text = point.description ?: ""
                    )
                }
            }
        }
    }
}
