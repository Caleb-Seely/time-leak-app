package com.cs.timeleak.ui.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cs.timeleak.data.UserPrefs

/**
 * Modal dialog for editing daily screen time goals with validation.
 * 
 * The modal validates that goals do not exceed the user's baseline screen time.
 * If baseline screen time is not saved, it will automatically calculate and save
 * it using the current 30-day average, ensuring consistent validation logic.
 * 
 * Validation rules:
 * - Goals cannot exceed baseline screen time (original or calculated from 30-day average)
 * - Goals must be between 1 minute and 24 hours
 * 
 * This prevents users from setting unrealistic goals that are higher than their
 * actual usage patterns, encouraging achievable goal-setting.
 *
 * @param currentGoal Current goal time in milliseconds
 * @param monthlyAverage 30-day average screen time in milliseconds (used to calculate baseline if missing)
 * @param onGoalSaved Callback when goal is successfully saved
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun GoalEditModal(
    currentGoal: Long,
    monthlyAverage: Long? = null,
    onGoalSaved: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var baselineScreenTime by remember { mutableStateOf(UserPrefs.getBaselineScreenTime(context)) }
    
    // If baseline is not saved and we have a 30-day average, calculate and save it as baseline
    LaunchedEffect(baselineScreenTime, monthlyAverage) {
        if (baselineScreenTime == null && monthlyAverage != null) {
            UserPrefs.saveBaselineScreenTime(context, monthlyAverage)
            baselineScreenTime = monthlyAverage
        }
    }
    
    var hours by remember { mutableStateOf((currentGoal / (1000 * 60 * 60)).toString()) }
    var minutes by remember { mutableStateOf(((currentGoal % (1000 * 60 * 60)) / (1000 * 60)).toString()) }
    var hoursError by remember { mutableStateOf<String?>(null) }
    var minutesError by remember { mutableStateOf<String?>(null) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Set Daily Goal",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Hours",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { 
                                hours = it.filter { char -> char.isDigit() }
                                hoursError = null
                                minutesError = null // Clear any baseline error when user changes input
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = hoursError != null,
                            supportingText = hoursError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Minutes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { 
                                minutes = it.filter { char -> char.isDigit() }
                                minutesError = null
                                hoursError = null // Clear any baseline error when user changes input
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = minutesError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Display error message below the input fields, spanning full width
                minutesError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            val hoursInt = hours.toIntOrNull() ?: 0
                            val minutesInt = minutes.toIntOrNull() ?: 0
                            
                            if (hoursInt < 0 || hoursInt > 24) {
                                hoursError = "Hours must be 0-24"
                                return@Button
                            }
                            
                            if (minutesInt < 0 || minutesInt > 59) {
                                minutesError = "Minutes must be 0-59"
                                return@Button
                            }
                            
                            if (hoursInt == 0 && minutesInt == 0) {
                                minutesError = "Goal must be greater than 0"
                                return@Button
                            }
                            
                            val totalMillis = (hoursInt * 60 * 60 * 1000L) + (minutesInt * 60 * 1000L)
                            
                            // === GOAL VALIDATION LOGIC ===
                            // Always validate against baseline screen time (either original or calculated from 30-day average)
                            baselineScreenTime?.let { baseline ->
                                if (totalMillis > baseline) {
                                    val baselineHours = baseline / (60 * 60 * 1000)
                                    val baselineMinutes = (baseline % (60 * 60 * 1000)) / (60 * 1000)
                                    minutesError = "Goal cannot exceed your baseline screen time of ${baselineHours}h ${baselineMinutes}m"
                                    return@Button
                                }
                            }
                            
                            onGoalSaved(totalMillis)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
