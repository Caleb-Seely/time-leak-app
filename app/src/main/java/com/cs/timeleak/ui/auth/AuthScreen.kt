package com.cs.timeleak.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cs.timeleak.ui.onboarding.StandardizedHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Insights

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as android.app.Activity
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 40.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Standardized Header - Fixed position
            StandardizedHeader(
                subtitle = "Sign in with your phone number to track your screen time"
            )

            // Scrollable content
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                when {
                    authState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(16.dp)
                        )
                        Text(
                            text = authState.loadingMessage ?: "Loading...",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    authState.verificationId == null -> {
                        // Phone number input form (without button)
                        PhoneNumberForm(
                            countryCode = authState.countryCode,
                            onCountryCodeChange = { viewModel.updateCountryCode(it) },
                            phoneNumber = authState.phoneNumber,
                            onPhoneNumberChange = { viewModel.updatePhoneNumber(it) },
                            onSubmit = { viewModel.sendVerificationCode(context as android.app.Activity) },
                            isError = authState.error != null,
                            errorMessage = authState.error
                        )
                    }

                    else -> {
                        // OTP verification screen
                        OtpVerification(
                            otp = authState.otp,
                            onOtpChange = { viewModel.updateOtp(it) },
                            onVerifyCode = { viewModel.verifyCode(activity) },
                            onResendCode = { viewModel.sendVerificationCode(activity) },
                            isError = authState.error != null,
                            errorMessage = authState.error
                        )
                    }
                }
            }
            
            // Bottom Button Area - Fixed at bottom (only show for phone number input)
            if (!authState.isLoading && authState.verificationId == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Button(
                        onClick = { viewModel.sendVerificationCode(activity) },
                        enabled = authState.phoneNumber.length == 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Send Verification Code",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneNumberForm(
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isError: Boolean,
    errorMessage: String?
) {
    // Auto-submit when phone number reaches 10 digits
    LaunchedEffect(phoneNumber) {
        if (phoneNumber.length == 10) {
            onSubmit()
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Country Code Input
            OutlinedTextField(
                value = countryCode,
                onValueChange = { newValue ->
                    val cleaned = newValue.filter { it.isDigit() || it == '+' }
                    when {
                        cleaned.isEmpty() -> onCountryCodeChange("+1")
                        cleaned.startsWith("+") && cleaned.length <= 4 -> onCountryCodeChange(cleaned)
                        !cleaned.startsWith("+") && cleaned.length <= 3 -> onCountryCodeChange("+$cleaned")
                    }
                },
                label = { Text("Country") },
                placeholder = { Text("+1") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.width(100.dp),
                singleLine = true
            )

            // Phone Number Input with visual formatting
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { newValue ->
                    // Remove all non-digit characters from input
                    val digitsOnly = newValue.filter { it.isDigit() }
                    // Limit to 10 digits
                    if (digitsOnly.length <= 10) {
                        onPhoneNumberChange(digitsOnly)
                    }
                },
                label = { Text("Phone Number") },
                placeholder = { Text("555 - 123 - 4567") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = isError,
                visualTransformation = PhoneNumberVisualTransformation()
            )
        }

        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Privacy explanation section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {

                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    
                    // Phone number collection
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = "Phone data",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "We only ask for your phone number — no names, emails, or other info",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }

                    // Data linking
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = "Data linking",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Your number links your screen time to you, but it's never advertised",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }

                    // Visibility/accountability
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Insights,
                            contentDescription = "Visibility",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Anyone who knows your number can see yesterday's total — that's the point",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }

                    // Balance
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "Privacy balance",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Just enough for accountability, not enough for concern",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

// Custom VisualTransformation for phone number formatting
class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
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
            AnnotatedString(formatted),
            offsetMapping
        )
    }
}

// Helper function for display formatting
private fun formatPhoneNumberDisplay(digits: String): String {
    return when (digits.length) {
        0 -> ""
        1, 2, 3 -> digits
        4, 5, 6 -> "${digits.substring(0, 3)} - ${digits.substring(3)}"
        7, 8, 9, 10 -> "${digits.substring(0, 3)} - ${digits.substring(3, 6)} - ${digits.substring(6)}"
        else -> "${digits.substring(0, 3)} - ${digits.substring(3, 6)} - ${digits.substring(6, 10)}"
    }
}

@Composable
private fun OtpVerification(
    otp: String,
    onOtpChange: (String) -> Unit,
    onVerifyCode: () -> Unit,
    onResendCode: () -> Unit,
    isError: Boolean,
    errorMessage: String?
) {
    val focusRequesters = List(6) { remember { androidx.compose.ui.focus.FocusRequester() } }

    // Auto-submit when OTP is complete
    LaunchedEffect(otp) {
        if (otp.length == 6) {
            onVerifyCode()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in 0 until 6) {
                OutlinedTextField(
                    value = otp.getOrNull(i)?.toString() ?: "",
                    onValueChange = { value ->
                        if (value.length <= 1 && value.all { it.isDigit() }) {
                            val newOtp = StringBuilder(otp)
                            if (otp.length > i) {
                                newOtp.setCharAt(i, value.getOrNull(0) ?: ' ')
                            } else if (value.isNotEmpty()) {
                                while (newOtp.length < i) newOtp.append(' ')
                                newOtp.append(value[0])
                            }
                            onOtpChange(newOtp.toString().replace(" ", ""))
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
                    isError = isError
                )
            }
        }

        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = onVerifyCode,
            enabled = otp.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Verify Code")
        }

        TextButton(
            onClick = onResendCode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Resend Code")
        }
    }
}

@Composable
fun AuthScreenWithDebugNav(
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    currentStep: String,
    viewModel: AuthViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AuthScreen(
            onAuthSuccess = onAuthSuccess,
            viewModel = viewModel
        )

        // Debug navigation buttons at top
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
                        tint = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                IconButton(
                    onClick = onForward,
                    enabled = canGoForward
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Debug Forward",
                        tint = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
