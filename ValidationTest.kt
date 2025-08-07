import com.cs.timeleak.ui.onboarding.form.AuthFormState
import com.cs.timeleak.ui.onboarding.model.ValidationResult

fun main() {
    val formState = AuthFormState()
    
    println("=== Testing AuthFormState Validation ===")
    
    // Test 1: Initial state should not show errors
    println("\nTest 1: Initial State")
    println("Phone number: '${formState.phoneNumber}'")
    println("Validation result: ${formState.phoneNumberValidation}")
    println("Is valid: ${formState.phoneNumberValidation.isValid()}")
    println("Error message: ${formState.phoneNumberValidation.getErrorMessage()}")
    
    // Test 2: Partial typing should not show errors
    println("\nTest 2: Partial Input (3 digits)")
    formState.updatePhoneNumber("123")
    println("Phone number: '${formState.phoneNumber}'")
    println("Validation result: ${formState.phoneNumberValidation}")
    println("Is valid: ${formState.phoneNumberValidation.isValid()}")
    println("Error message: ${formState.phoneNumberValidation.getErrorMessage()}")
    
    // Test 3: After submission attempt, should show errors for invalid input
    println("\nTest 3: After Submission Attempt (partial number)")
    formState.markPhoneSubmissionAttempted()
    println("Phone number: '${formState.phoneNumber}'")
    println("Validation result: ${formState.phoneNumberValidation}")
    println("Is valid: ${formState.phoneNumberValidation.isValid()}")
    println("Error message: ${formState.phoneNumberValidation.getErrorMessage()}")
    
    // Test 4: Complete valid number should be valid
    println("\nTest 4: Complete Valid Number")
    formState.updatePhoneNumber("1234567890")
    println("Phone number: '${formState.phoneNumber}'")
    println("Validation result: ${formState.phoneNumberValidation}")
    println("Is valid: ${formState.phoneNumberValidation.isValid()}")
    println("Error message: ${formState.phoneNumberValidation.getErrorMessage()}")
    
    // Test 5: Clear should reset submission flag
    println("\nTest 5: After Clear")
    formState.clear()
    println("Phone number: '${formState.phoneNumber}'")
    println("Validation result: ${formState.phoneNumberValidation}")
    println("Is valid: ${formState.phoneNumberValidation.isValid()}")
    println("Error message: ${formState.phoneNumberValidation.getErrorMessage()}")
}
