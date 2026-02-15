package com.athletebridge.android.ui.bookings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.theme.LogoGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingEditorScreen(
    coachId: String,
    onBack: () -> Unit,
    onBookingCreated: () -> Unit,
    viewModel: BookingEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(coachId) {
        viewModel.loadCoach(coachId)
    }

    // Date/time state
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val startCal = remember { Calendar.getInstance().apply { add(Calendar.HOUR, 1); set(Calendar.MINUTE, 0) } }
    val endCal = remember { Calendar.getInstance().apply { add(Calendar.HOUR, 2); set(Calendar.MINUTE, 0) } }

    var startDate by remember { mutableLongStateOf(startCal.timeInMillis) }
    var endDate by remember { mutableLongStateOf(endCal.timeInMillis) }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // Handle successful booking creation
    LaunchedEffect(uiState.bookingCreated) {
        if (uiState.bookingCreated) {
            onBookingCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Booking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Coach info
            uiState.coach?.let { coach ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Coach", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(coach.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        coach.hourlyRate?.let { rate ->
                            Text("Hourly Rate: $${String.format("%.0f", rate)}/hr", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (coach.specialties.isNotEmpty()) {
                            Text("Specialties: ${coach.specialties.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Start Date/Time
            Text("Start", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(dateFormat.format(Date(startDate)))
                }
                OutlinedButton(
                    onClick = { showStartTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(timeFormat.format(Date(startDate)))
                }
            }

            // End Date/Time
            Text("End", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(dateFormat.format(Date(endDate)))
                }
                OutlinedButton(
                    onClick = { showEndTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(timeFormat.format(Date(endDate)))
                }
            }

            // Duration display
            val durationMin = (endDate - startDate) / 60000
            if (durationMin > 0) {
                val hours = durationMin / 60.0
                Text(
                    "Duration: ${String.format("%.1f", hours)} hours",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                uiState.coach?.hourlyRate?.let { rate ->
                    val cost = rate * (((durationMin / 30.0).toLong() * 30) / 60.0)
                    Text(
                        "Estimated Cost: $${String.format("%.2f", cost)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LogoGreen
                    )
                }
            }

            // Location
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            // Validation message
            uiState.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Create Booking button
            Button(
                onClick = {
                    viewModel.createBooking(
                        coachId = coachId,
                        startAt = Date(startDate),
                        endAt = Date(endDate),
                        location = location.takeIf { it.isNotBlank() },
                        notes = notes.takeIf { it.isNotBlank() }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving && durationMin >= 60 && startDate < endDate && startDate > System.currentTimeMillis(),
                colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Request Booking")
                }
            }

            if (durationMin < 60 && endDate > startDate) {
                Text("Minimum booking duration is 1 hour", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (startDate <= System.currentTimeMillis()) {
                Text("Start time must be in the future", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // Date pickers
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val oldCal = Calendar.getInstance().apply { timeInMillis = startDate }
                        val newCal = Calendar.getInstance().apply { timeInMillis = millis }
                        newCal.set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                        newCal.set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                        startDate = newCal.timeInMillis
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val oldCal = Calendar.getInstance().apply { timeInMillis = endDate }
                        val newCal = Calendar.getInstance().apply { timeInMillis = millis }
                        newCal.set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                        newCal.set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                        endDate = newCal.timeInMillis
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = startDate }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = (cal.get(Calendar.MINUTE) / 30) * 30
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("Select Start Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply { timeInMillis = startDate }
                    newCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    newCal.set(Calendar.MINUTE, (timePickerState.minute / 30) * 30)
                    startDate = newCal.timeInMillis
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showEndTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = endDate }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = (cal.get(Calendar.MINUTE) / 30) * 30
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("Select End Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply { timeInMillis = endDate }
                    newCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    newCal.set(Calendar.MINUTE, (timePickerState.minute / 30) * 30)
                    endDate = newCal.timeInMillis
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}
