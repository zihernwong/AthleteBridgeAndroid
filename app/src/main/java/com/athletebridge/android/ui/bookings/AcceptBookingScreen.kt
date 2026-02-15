package com.athletebridge.android.ui.bookings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptBookingScreen(
    bookingId: String,
    onBack: () -> Unit,
    viewModel: AcceptBookingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(bookingId) {
        viewModel.loadBooking(bookingId)
    }

    var rateText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showRejectOptions by remember { mutableStateOf(false) }
    var selectedRejectReason by remember { mutableStateOf("") }

    val rejectReasons = listOf("Not Qualified", "Coach Unavailable", "Other")
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

    // Prefill rate from coach's hourly rate
    LaunchedEffect(uiState.coachHourlyRate) {
        if (rateText.isEmpty() && uiState.coachHourlyRate != null) {
            rateText = String.format("%.0f", uiState.coachHourlyRate)
        }
    }

    // Handle success
    LaunchedEffect(uiState.actionComplete) {
        if (uiState.actionComplete) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accept Booking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val booking = uiState.booking
        if (uiState.isLoading || booking == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Booking info
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Booking Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Client: ${booking.clientName ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium)
                        booking.startAt?.let { Text("Start: ${dateFmt.format(it)}", style = MaterialTheme.typography.bodyMedium) }
                        booking.endAt?.let { Text("End: ${dateFmt.format(it)}", style = MaterialTheme.typography.bodyMedium) }

                        if (booking.startAt != null && booking.endAt != null) {
                            val totalMinutes = (booking.endAt.time - booking.startAt.time) / 60000
                            val durationHours = ((totalMinutes / 30.0).toLong() * 30) / 60.0
                            Text("Duration: ${String.format("%.1f", durationHours)} hours", style = MaterialTheme.typography.bodyMedium)
                        }

                        booking.location?.takeIf { it.isNotBlank() }?.let {
                            Text("Location: $it", style = MaterialTheme.typography.bodyMedium)
                        }
                        booking.notes?.takeIf { it.isNotBlank() }?.let {
                            Text("Notes: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Group booking coach acceptances
                        if (booking.isGroupBooking == true && !booking.coachAcceptances.isNullOrEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Coach Acceptances", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            booking.allCoachNames.forEachIndexed { index, name ->
                                val coachId = booking.allCoachIDs.getOrNull(index) ?: ""
                                val accepted = booking.coachAcceptances[coachId] == true
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (accepted) Icons.Default.CheckCircle else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (accepted) LogoGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(name, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // Hourly Rate input
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Hourly Rate (USD)") },
                    prefix = { Text("$") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Total cost calculation
                val rate = rateText.toDoubleOrNull() ?: 0.0
                if (rate > 0 && booking.startAt != null && booking.endAt != null) {
                    val totalMinutes = (booking.endAt.time - booking.startAt.time) / 60000
                    val durationHours = ((totalMinutes / 30.0).toLong() * 30) / 60.0
                    val totalCost = rate * durationHours
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = LogoGreen.copy(alpha = 0.1f))) {
                        Text(
                            "Total Booking Cost: $${String.format("%.2f", totalCost)}",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = LogoGreen
                        )
                    }
                }

                // Note to client
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note to Client (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                // Error
                uiState.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // Accept button
                Button(
                    onClick = {
                        viewModel.acceptBooking(bookingId, rate, note.takeIf { it.isNotBlank() })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving && rate > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Accept Booking")
                    }
                }

                // Reject section
                HorizontalDivider()

                var expandReject by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { expandReject = !expandReject },
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusCancelled)
                ) {
                    Text(if (expandReject) "Hide Reject Options" else "Reject Booking...")
                }

                if (expandReject) {
                    Text("Select a reason:", style = MaterialTheme.typography.labelMedium)
                    rejectReasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedRejectReason == reason,
                                onClick = { selectedRejectReason = reason }
                            )
                            Text(reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Button(
                        onClick = { viewModel.rejectBooking(bookingId, selectedRejectReason) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedRejectReason.isNotBlank() && !uiState.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                    ) {
                        Text("Reject Booking")
                    }
                }
            }
        }
    }
}
