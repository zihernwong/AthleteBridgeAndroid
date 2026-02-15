package com.athletebridge.android.ui.bookings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import com.athletebridge.android.ui.theme.StatusConfirmed
import com.athletebridge.android.ui.theme.StatusPending
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingsScreen(
    isCoach: Boolean,
    onCreateBooking: () -> Unit = {},
    onAcceptBooking: (String) -> Unit = {},
    onReviewBooking: (String) -> Unit = {},
    onConfirmedBookings: () -> Unit = {},
    viewModel: BookingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(isCoach) {
        viewModel.loadBookings(isCoach)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bookings") }) },
        floatingActionButton = {
            if (!isCoach) {
                FloatingActionButton(
                    onClick = onCreateBooking,
                    containerColor = LogoGreen
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Booking", tint = Color.White)
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.bookings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No bookings yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isCoach) "Booking requests will appear here" else "Tap + to create a booking",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Coach: link to confirmed bookings
                if (isCoach) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirmedBookings() },
                            colors = CardDefaults.cardColors(containerColor = LogoBlue.copy(alpha = 0.08f))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LogoGreen)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Confirmed Bookings", fontWeight = FontWeight.SemiBold)
                                    Text("View upcoming and past confirmed sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Pending bookings - actionable
                val awaitingAction = uiState.bookings.filter { booking ->
                    val s = (booking.status ?: "").lowercase()
                    if (isCoach) {
                        s == "requested" || s == "partially_accepted"
                    } else {
                        s.contains("pending") && s.contains("acceptance") || s == "partially_confirmed"
                    }
                }.filter { booking ->
                    // Only show future bookings for pending
                    (booking.endAt ?: Date(0)) > Date()
                }

                if (awaitingAction.isNotEmpty()) {
                    item {
                        Text(
                            if (isCoach) "Awaiting Your Acceptance" else "Awaiting Your Confirmation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(awaitingAction, key = { "action_${it.id}" }) { booking ->
                        BookingCard(
                            booking = booking,
                            isCoach = isCoach,
                            onClick = {
                                if (isCoach) onAcceptBooking(booking.id)
                                else onReviewBooking(booking.id)
                            }
                        )
                    }
                }

                // Confirmed bookings
                val confirmed = uiState.bookings.filter {
                    val s = (it.status ?: "").lowercase()
                    s == "confirmed" || s == "fully_confirmed"
                }
                if (confirmed.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Confirmed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(confirmed, key = { "confirmed_${it.id}" }) { booking ->
                        BookingCard(booking = booking, isCoach = isCoach)
                    }
                }

                // Other bookings (cancelled, rejected, declined, etc.)
                val other = uiState.bookings.filter {
                    val s = (it.status ?: "").lowercase()
                    s.contains("cancelled") || s.contains("rejected") || s.contains("declined")
                }
                if (other.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Completed / Other", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(other, key = { "other_${it.id}" }) { booking ->
                        BookingCard(booking = booking, isCoach = isCoach)
                    }
                }
            }
        }
    }
}

@Composable
fun BookingCard(
    booking: BookingItem,
    isCoach: Boolean,
    onClick: (() -> Unit)? = null
) {
    val status = (booking.status ?: "unknown").lowercase()
    val statusColor = when {
        status == "confirmed" || status == "fully_confirmed" -> StatusConfirmed
        status.contains("pending") || status.contains("requested") || status.contains("partially") -> StatusPending
        status.contains("cancelled") -> StatusCancelled
        status.contains("rejected") || status.contains("declined") -> StatusCancelled
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val isGroupBooking = (booking.isGroupBooking == true) || booking.allCoachIDs.size > 1 || booking.allClientIDs.size > 1

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isCoach) booking.allClientNames.joinToString(", ").ifBlank { "Client" }
                            else booking.allCoachNames.joinToString(", ").ifBlank { "Coach" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isGroupBooking) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Groups, contentDescription = "Group", modifier = Modifier.size(16.dp), tint = LogoBlue)
                        }
                    }
                    booking.startAt?.let { start ->
                        val fmt = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                        Text(fmt.format(start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        booking.status?.replaceFirstChar { it.uppercase() } ?: "Unknown",
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (booking.startAt != null && booking.endAt != null) {
                val durationMin = (booking.endAt.time - booking.startAt.time) / 60000
                val hours = durationMin / 60.0
                Spacer(Modifier.height(4.dp))
                Text("Duration: ${String.format("%.1f", hours)} hrs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            booking.location?.takeIf { it.isNotBlank() }?.let { loc ->
                Text("Location: $loc", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            booking.rateUSD?.let { rate ->
                Text("Rate: $${String.format("%.0f", rate)}/hr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val paymentStatus = (booking.paymentStatus ?: "").lowercase()
            if (paymentStatus == "paid") {
                Spacer(Modifier.height(4.dp))
                Text("Payment: Paid", style = MaterialTheme.typography.labelSmall, color = LogoGreen, fontWeight = FontWeight.Bold)
            }

            if (onClick != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isCoach) "Tap to accept or reject" else "Tap to review and confirm",
                    style = MaterialTheme.typography.labelSmall,
                    color = LogoBlue
                )
            }
        }
    }
}
