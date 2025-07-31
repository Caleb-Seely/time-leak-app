package com.cs.timeleak.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cs.timeleak.R

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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-80).dp), // Shift content up to place it in top third
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Icon
            Image(
                painter = painterResource(R.drawable.icon),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(96.dp)
                    .padding(bottom = 8.dp)
            )

            // Gradient Welcome Text
            GradientText(
                text = "Welcome to TimeLeak",
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Sign in with your phone number to track your screen time",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                    // Phone number input screen
                    PhoneNumberInput(
                        countryCode = authState.countryCode,
                        onCountryCodeChange = { viewModel.updateCountryCode(it) },
                        phoneNumber = authState.phoneNumber,
                        onPhoneNumberChange = { viewModel.updatePhoneNumber(it) },
                        onSendCode = { viewModel.sendVerificationCode(activity) },
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
    }
}

@Composable
private fun PhoneNumberInput(
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onSendCode: () -> Unit,
    isError: Boolean,
    errorMessage: String?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

        Button(
            onClick = onSendCode,
            enabled = phoneNumber.length == 10,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Send Verification Code")
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
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
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
fun GradientText(text: String, modifier: Modifier = Modifier) {
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF2196F3), Color(0xFF9C27B0)), // Blue to Purple
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(400f, 0f)
    )
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(
            brush = gradient,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp
        ),
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}