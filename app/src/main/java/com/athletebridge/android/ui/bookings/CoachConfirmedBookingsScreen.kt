package com.athletebridge.android.ui.bookings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachConfirmedBookingsScreen(
    onBack: () -> Unit,
    viewModel: CoachConfirmedBookingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCancelDialog by remember { mutableStateOf<BookingItem?>(null) }
    var showRescheduleDialog by remember { mutableStateOf<BookingItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadBookings()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val now = Date()
    val upcoming = uiState.bookings.filter { (it.endAt ?: Date(0)) > now }
    val past = uiState.bookings.filter { (it.endAt ?: Date(0)) <= now }
    val displayedBookings = if (selectedTab == 0) upcoming else past

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "Upcoming Bookings" else "Past Bookings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Tab selector
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Upcoming (${upcoming.size})", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Past (${past.size})", modifier = Modifier.padding(12.dp))
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (displayedBookings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No ${if (selectedTab == 0) "upcoming" else "past"} bookings", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val dateFmt = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedBookings, key = { it.id }) { booking ->
                        ConfirmedBookingCard(
                            booking = booking,
                            dateFmt = dateFmt,
                            onAcknowledgePayment = { viewModel.acknowledgePayment(booking) },
                            onCancel = { showCancelDialog = booking },
                            onReschedule = { showRescheduleDialog = booking },
                            isSaving = uiState.isSaving
                        )
                    }
                }
            }
        }
    }

    // Cancel confirmation dialog
    showCancelDialog?.let { booking ->
        AlertDialog(
            onDismissRequest = { showCancelDialog = null },
            title = { Text("Cancel Booking") },
            text = { Text("Are you sure you want to cancel this booking with ${booking.clientName ?: "the client"}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelBooking(booking)
                        showCancelDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusCancelled)
                ) { Text("Cancel Booking") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = null }) { Text("Keep") }
            }
        )
    }

    // Reschedule dialog
    showRescheduleDialog?.let { booking ->
        RescheduleDialog(
            booking = booking,
            onDismiss = { showRescheduleDialog = null },
            onReschedule = { newStart, newEnd ->
                viewModel.rescheduleBooking(booking, newStart, newEnd)
                showRescheduleDialog = null
            }
        )
    }
}

@Composable
fun ConfirmedBookingCard(
    booking: BookingItem,
    dateFmt: SimpleDateFormat,
    onAcknowledgePayment: () -> Unit,
    onCancel: () -> Unit,
    onReschedule: () -> Unit,
    isSaving: Boolean
) {
    val isPaid = booking.paymentStatus?.lowercase() == "paid"

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(booking.clientName ?: "Client", fontWeight = FontWeight.SemiBold)
                    booking.startAt?.let { Text(dateFmt.format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (isPaid) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LogoGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Paid", color = LogoGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (booking.startAt != null && booking.endAt != null) {
                val totalMinutes = (booking.endAt.time - booking.startAt.time) / 60000
                val durationHours = ((totalMinutes / 30.0).toLong() * 30) / 60.0
                Text("Duration: ${String.format("%.1f", durationHours)} hrs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            booking.rateUSD?.let { rate ->
                Text("Rate: $${String.format("%.0f", rate)}/hr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (!isPaid) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAcknowledgePayment,
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("Acknowledge Payment", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCancel, enabled = !isSaving) {
                        Text("Cancel", color = StatusCancelled, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onReschedule, enabled = !isSaving) {
                        Text("Reschedule", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDialog(
    booking: BookingItem,
    onDismiss: () -> Unit,
    onReschedule: (Date, Date) -> Unit
) {
    val startCal = Calendar.getInstance().apply {
        time = booking.startAt ?: Date()
    }
    val endCal = Calendar.getInstance().apply {
        time = booking.endAt ?: Date()
    }

    var newStartDate by remember { mutableLongStateOf(startCal.timeInMillis) }
    var newEndDate by remember { mutableLongStateOf(endCal.timeInMillis) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("MMM d h:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule Booking") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("New Start Time:")
                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(dateFmt.format(Date(newStartDate)))
                }
                Text("New End Time:")
                OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(dateFmt.format(Date(newEndDate)))
                }
                val dur = (newEndDate - newStartDate) / 60000
                if (dur < 60) {
                    Text("Minimum 1 hour duration required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onReschedule(Date(newStartDate), Date(newEndDate)) },
                enabled = (newEndDate - newStartDate) >= 3600000
            ) { Text("Reschedule") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showStartPicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = newStartDate }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = (cal.get(Calendar.MINUTE) / 30) * 30
        )
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            title = { Text("Start Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply { timeInMillis = newStartDate }
                    newCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    newCal.set(Calendar.MINUTE, (timePickerState.minute / 30) * 30)
                    newStartDate = newCal.timeInMillis
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showEndPicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = newEndDate }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = (cal.get(Calendar.MINUTE) / 30) * 30
        )
        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            title = { Text("End Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply { timeInMillis = newEndDate }
                    newCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    newCal.set(Calendar.MINUTE, (timePickerState.minute / 30) * 30)
                    newEndDate = newCal.timeInMillis
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        )
    }
}
