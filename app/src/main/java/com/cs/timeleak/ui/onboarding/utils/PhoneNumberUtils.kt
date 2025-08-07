package com.cs.timeleak.ui.onboarding.utils

import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Utility functions for phone number formatting and display
 */

/**
 * Visual transformation for phone number input fields
 * Formats digits as: 555 - 123 - 4567
 */
class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
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
            androidx.compose.ui.text.AnnotatedString(formatted),
            offsetMapping
        )
    }
}

/**
 * Format phone number digits for display
 * 
 * @param digits Raw phone number digits (no formatting)
 * @return Formatted phone number string
 */
fun formatPhoneNumberDisplay(digits: String): String {
    return when (digits.length) {
        0 -> ""
        1, 2, 3 -> digits
        4, 5, 6 -> "${digits.substring(0, 3)} - ${digits.substring(3)}"
        7, 8, 9, 10 -> "${digits.substring(0, 3)} - ${digits.substring(3, 6)} - ${digits.substring(6)}"
        else -> "${digits.substring(0, 3)} - ${digits.substring(3, 6)} - ${digits.substring(6, 10)}"
    }
}

/**
 * Extract just the digits from a phone number string
 * 
 * @param phoneNumber Phone number with or without formatting
 * @return Just the digits
 */
fun extractDigits(phoneNumber: String): String {
    return phoneNumber.filter { it.isDigit() }
}

/**
 * Validate phone number format (US format)
 * 
 * @param phoneNumber Raw digits
 * @return true if valid format
 */
fun isValidPhoneNumber(phoneNumber: String): Boolean {
    val digits = extractDigits(phoneNumber)
    return digits.length == 10 && digits.all { it.isDigit() }
}

/**
 * Format country code for display
 * 
 * @param countryCode Raw country code (with or without +)
 * @return Formatted country code with +
 */
fun formatCountryCode(countryCode: String): String {
    return when {
        countryCode.isEmpty() -> "+1"
        countryCode.startsWith("+") && countryCode.length <= 4 -> countryCode
        !countryCode.startsWith("+") && countryCode.length <= 3 -> "+$countryCode"
        else -> "+1" // Default fallback
    }
}
