package com.athletebridge.android.ui.bookings

import androidx.compose.foundation.clickable
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
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientConfirmedBookingsScreen(
    onBack: () -> Unit,
    onReviewBooking: (String) -> Unit = {},
    onBookingDetail: (String) -> Unit = {},
    viewModel: ClientConfirmedBookingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCancelDialog by remember { mutableStateOf<BookingItem?>(null) }
    var showRescheduleDialog by remember { mutableStateOf<BookingItem?>(null) }

    LaunchedEffect(Unit) { viewModel.loadBookings() }

    val now = Date()
    val upcoming = uiState.bookings.filter { (it.endAt ?: Date(0)) > now }
    val past = uiState.bookings.filter { (it.endAt ?: Date(0)) <= now }
    val displayedBookings = if (selectedTab == 0) upcoming else past

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmed Bookings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                    Text(
                        "No ${if (selectedTab == 0) "upcoming" else "past"} confirmed bookings",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val dateFmt = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedBookings, key = { it.id }) { booking ->
                        ClientConfirmedBookingCard(
                            booking = booking,
                            dateFmt = dateFmt,
                            isPastBooking = selectedTab == 1,
                            isSaving = uiState.isSaving,
                            onClick = { onBookingDetail(booking.id) },
                            onCancel = { showCancelDialog = booking },
                            onReschedule = { showRescheduleDialog = booking },
                            onReview = { onReviewBooking(booking.id) }
                        )
                    }
                }
            }
        }
    }

    showCancelDialog?.let { booking ->
        val insideWindow = viewModel.isInsideCancellationWindow(booking)
        val windowHours = uiState.coachCancellationWindows[booking.coachID] ?: 0
        AlertDialog(
            onDismissRequest = { showCancelDialog = null },
            title = { Text("Cancel Booking") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to cancel this booking with ${booking.coachName ?: "your coach"}?")
                    if (insideWindow) {
                        Text(
                            if (booking.requiresPaymentUpfront == true)
                                "This coach has a $windowHours-hour cancellation policy. Cancelling now means your deposit will not be refunded."
                            else
                                "Heads up: this coach has a $windowHours-hour cancellation policy and you're inside that window.",
                            color = StatusCancelled,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
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
private fun ClientConfirmedBookingCard(
    booking: BookingItem,
    dateFmt: SimpleDateFormat,
    isPastBooking: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onReschedule: () -> Unit,
    onReview: () -> Unit
) {
    val isPaid = booking.paymentStatus?.lowercase() == "paid"
    val coachDisplay = booking.allCoachNames.joinToString(", ").ifEmpty { "Coach" }

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(coachDisplay, fontWeight = FontWeight.SemiBold)
                    booking.startAt?.let {
                        Text(dateFmt.format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (isPaid) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LogoGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Paid", color = LogoGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            booking.location?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            booking.rateUSD?.let { rate ->
                Text(
                    "Rate: $${String.format(Locale.US, "%.0f", rate)}/hr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Cancel/Reschedule hidden for past bookings or once payment is acknowledged (matches iOS)
            if (!isPastBooking && !isPaid) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReschedule, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                        Text("Reschedule", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isSaving,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCancelled),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel Booking", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (isPastBooking) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
                    Text("Leave a Review", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
