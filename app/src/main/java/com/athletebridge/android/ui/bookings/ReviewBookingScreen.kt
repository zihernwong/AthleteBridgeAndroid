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
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewBookingScreen(
    bookingId: String,
    onBack: () -> Unit,
    viewModel: ReviewBookingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(bookingId) {
        viewModel.loadBooking(bookingId)
    }

    LaunchedEffect(uiState.actionComplete) {
        if (uiState.actionComplete) {
            onBack()
        }
    }

    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Booking") },
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
                Text("Review Booking", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

                // Coach(es) & Rates
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        val isGroup = (booking.isGroupBooking == true) || booking.allCoachIDs.size > 1

                        if (isGroup && !booking.coachRates.isNullOrEmpty()) {
                            Text("Coaches & Rates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            var totalCombinedCost = 0.0
                            booking.allCoachNames.forEachIndexed { index, name ->
                                val coachId = booking.allCoachIDs.getOrNull(index) ?: ""
                                val rate = booking.coachRates[coachId]
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                    rate?.let {
                                        Text("$${String.format("%.0f", it)}/hr", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (booking.startAt != null && booking.endAt != null) {
                                            val totalMinutes = (booking.endAt.time - booking.startAt.time) / 60000
                                            val durationHours = ((totalMinutes / 30.0).toLong() * 30) / 60.0
                                            totalCombinedCost += it * durationHours
                                        }
                                    }
                                }
                            }
                            if (totalCombinedCost > 0) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Combined Cost", fontWeight = FontWeight.Bold)
                                    Text("$${String.format("%.2f", totalCombinedCost)}", fontWeight = FontWeight.Bold, color = LogoGreen)
                                }
                            }
                        } else {
                            // Single coach
                            Text("Coach", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(booking.coachName ?: "Coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            booking.rateUSD?.let { rate ->
                                Spacer(Modifier.height(8.dp))
                                Text("Hourly Rate: $${String.format("%.0f", rate)}/hr", style = MaterialTheme.typography.bodyMedium)

                                if (booking.startAt != null && booking.endAt != null) {
                                    val totalMinutes = (booking.endAt.time - booking.startAt.time) / 60000
                                    val durationHours = ((totalMinutes / 30.0).toLong() * 30) / 60.0
                                    val totalCost = rate * durationHours
                                    Text(
                                        "Total Booking Cost: $${String.format("%.2f", totalCost)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = LogoGreen
                                    )
                                }
                            }
                        }
                    }
                }

                // Times
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        booking.startAt?.let { Text("Start: ${dateFmt.format(it)}", style = MaterialTheme.typography.bodyMedium) }
                        booking.endAt?.let { Text("End: ${dateFmt.format(it)}", style = MaterialTheme.typography.bodyMedium) }

                        if (booking.startAt != null && booking.endAt != null) {
                            val totalMinutes = (booking.endAt.time - booking.startAt.time) / 60000
                            val durationHours = ((totalMinutes / 30.0).toLong() * 30) / 60.0
                            Text("Duration: ${String.format("%.1f", durationHours)} hours", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Coach note
                booking.coachNote?.takeIf { it.isNotBlank() }?.let { cn ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Note from Coach", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(cn, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Error
                uiState.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.weight(1f))

                // Actions
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.declineBooking(bookingId) },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCancelled)
                    ) {
                        Text("Decline")
                    }

                    Button(
                        onClick = { viewModel.confirmBooking(bookingId) },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Confirm Booking")
                        }
                    }
                }
            }
        }
    }
}
