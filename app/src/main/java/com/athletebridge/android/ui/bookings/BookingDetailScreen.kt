package com.athletebridge.android.ui.bookings

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    bookingId: String,
    isCoach: Boolean,
    onBack: () -> Unit,
    viewModel: BookingDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bookingId) { viewModel.load(bookingId, isCoach) }

    LaunchedEffect(uiState.message, uiState.error) {
        (uiState.message ?: uiState.error)?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Booking Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val booking = uiState.booking
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            booking == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Booking not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusSection(booking)
                ParticipantsSection(booking)
                DateTimeSection(booking)
                booking.location?.takeIf { it.isNotEmpty() }?.let { LocationSection(it) }
                RateSection(booking)

                val statusLower = (booking.status ?: "").lowercase()
                val isPaid = (booking.paymentStatus ?: "").lowercase() == "paid"

                // Client payment actions — unpaid confirmed/pending-payment bookings
                if (!uiState.isCoach && !isPaid &&
                    statusLower in listOf("confirmed", "pending_payment")
                ) {
                    PaymentActionsSection(
                        onNotifyCoach = {
                            booking.allCoachIDs.firstOrNull { it.isNotEmpty() }?.let {
                                viewModel.notifyCoachOfPayment(it)
                            }
                        },
                        onMakePayment = { viewModel.loadCoachPayments() }
                    )
                }

                // Coach confirmation once the client has submitted payment
                if (uiState.isCoach && statusLower == "payment_submitted") {
                    CoachPaymentConfirmationSection(
                        isConfirming = uiState.isConfirmingPayment,
                        onConfirm = { viewModel.confirmPaymentReceived() }
                    )
                }

                NotesSection(
                    booking = booking,
                    isCoach = uiState.isCoach,
                    isSaving = uiState.isSavingNotes,
                    onSaveNotes = { viewModel.saveSessionNotes(it) }
                )

                SessionRecapSection(
                    booking = booking,
                    isCoach = uiState.isCoach,
                    isSaving = uiState.isSavingNotes,
                    onSaveRecap = { viewModel.saveSessionRecap(it) }
                )

                booking.rejectionReason?.takeIf { it.isNotEmpty() && statusLower == "rejected" }?.let {
                    ReasonSection(title = "Rejection Reason", reason = it)
                }
                booking.clientDeclineReason?.takeIf { it.isNotEmpty() && statusLower == "declined_by_client" }?.let {
                    ReasonSection(title = "Client Decline Reason", reason = it)
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Coach payment methods dialog (client "Make Payment")
    uiState.selectedCoachPayments?.let { payments ->
        CoachPaymentMethodsDialog(
            coachName = uiState.selectedCoachName,
            payments = payments,
            onDismiss = { viewModel.dismissCoachPayments() }
        )
    }
}

// ── Sections ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun StatusSection(booking: BookingItem) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor(booking.status).copy(alpha = 0.15f)
            ) {
                Text(
                    displayStatus(booking.status),
                    color = statusColor(booking.status),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        if (booking.isGroupBooking == true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Group, contentDescription = null, tint = LogoBlue, modifier = Modifier.size(18.dp))
                Text("Group Session", color = LogoBlue, style = MaterialTheme.typography.labelLarge)
            }
        }
        if ((booking.paymentStatus ?: "").lowercase() == "paid") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LogoGreen, modifier = Modifier.size(18.dp))
                Text("Paid", color = LogoGreen, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ParticipantsSection(booking: BookingItem) {
    SectionCard {
        Text("Participants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val coachNames = booking.allCoachNames.ifEmpty { listOf("Coach") }
        coachNames.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Person, contentDescription = null, tint = LogoGreen, modifier = Modifier.size(18.dp))
                Text(name)
                Text("Coach", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val clientNames = booking.allClientNames.ifEmpty { listOf("Client") }
        clientNames.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Person, contentDescription = null, tint = LogoBlue, modifier = Modifier.size(18.dp))
                Text(name)
                Text("Client", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DateTimeSection(booking: BookingItem) {
    SectionCard {
        Text("Date & Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val dateFmt = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }
        val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
        booking.startAt?.let { start ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(dateFmt.format(start))
            }
            booking.endAt?.let { end ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text("${timeFmt.format(start)} - ${timeFmt.format(end)}")
                    val minutes = ((end.time - start.time) / 60000L).toInt()
                    if (minutes > 0) {
                        Text("($minutes minutes)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } ?: Text("Date TBD", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LocationSection(location: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Location is stored as "Name \u2014 Address"; the address part geocodes/navigates best
    val address = location.split(" \u2014 ").lastOrNull() ?: location
    SectionCard {
        Text("Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(location)
        }
        com.athletebridge.android.ui.components.OsmMapSnapshot(
            address = address,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )
        Button(
            onClick = {
                val uri = "geo:0,0?q=${android.net.Uri.encode(address)}".toUri()
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Get Directions")
        }
    }
}

@Composable
private fun RateSection(booking: BookingItem) {
    SectionCard {
        Text("Pricing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val durationHours = if (booking.startAt != null && booking.endAt != null) {
            ((booking.endAt.time - booking.startAt.time).toDouble() / 3_600_000.0).coerceAtLeast(0.0)
        } else 1.0

        var total: Double? = null
        if (booking.isGroupBooking == true && !booking.coachRates.isNullOrEmpty()) {
            val rates = booking.coachRates
            booking.allCoachIDs.zip(booking.allCoachNames).forEach { (coachId, coachName) ->
                rates[coachId]?.let { rate ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(coachName)
                        Text(String.format(Locale.US, "$%.2f", rate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            total = rates.values.sum() * durationHours
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rate")
                Text(
                    booking.rateUSD?.let { String.format(Locale.US, "$%.2f/hr", it) } ?: "—",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            total = booking.rateUSD?.let { it * durationHours }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total Cost", fontWeight = FontWeight.SemiBold)
            Text(
                total?.let { String.format(Locale.US, "$%.2f", it) } ?: "—",
                fontWeight = FontWeight.SemiBold,
                color = if (total != null) LogoGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaymentActionsSection(onNotifyCoach: () -> Unit, onMakePayment: () -> Unit) {
    SectionCard {
        Text("Payment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onNotifyCoach, modifier = Modifier.weight(1f)) {
                Text("Notify Coach of Payment")
            }
            Button(
                onClick = onMakePayment,
                colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                modifier = Modifier.weight(1f)
            ) {
                Text("Make Payment")
            }
        }
    }
}

@Composable
private fun CoachPaymentConfirmationSection(isConfirming: Boolean, onConfirm: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Payment Submitted by Client", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF7A4E00))
            Text(
                "The client has submitted payment. Once you confirm receipt, the booking will be marked as confirmed and paid.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7A4E00)
            )
            Button(
                onClick = onConfirm,
                enabled = !isConfirming,
                colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isConfirming) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Confirm Payment Received")
                }
            }
        }
    }
}

@Composable
private fun NotesSection(
    booking: BookingItem,
    isCoach: Boolean,
    isSaving: Boolean,
    onSaveNotes: (String) -> Unit
) {
    val hasClientNotes = !booking.notes.isNullOrEmpty()
    if (!hasClientNotes && !isCoach) return

    var notesDraft by remember(booking.id) { mutableStateOf(booking.coachNote ?: "") }

    SectionCard {
        Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        if (hasClientNotes) {
            Text(booking.notes!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isCoach) HorizontalDivider()
        }

        if (isCoach) {
            Text("Session Notes (Private)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = notesDraft,
                onValueChange = { notesDraft = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                placeholder = { Text("Add private notes about this session…") }
            )
            if (notesDraft != (booking.coachNote ?: "")) {
                Button(
                    onClick = { onSaveNotes(notesDraft) },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = LogoBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Save Notes")
                    }
                }
            }
        }
    }
}

/** Coach-written recap shared with the client ("what we worked on / what to practice"). */
@Composable
private fun SessionRecapSection(
    booking: BookingItem,
    isCoach: Boolean,
    isSaving: Boolean,
    onSaveRecap: (String) -> Unit
) {
    val recap = booking.sessionRecap ?: ""
    if (!isCoach && recap.isEmpty()) return

    var recapDraft by remember(booking.id) { mutableStateOf(recap) }

    SectionCard {
        Text("Session Recap", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (isCoach) {
            Text(
                "Visible to the client — what you worked on and what to practice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = recapDraft,
                onValueChange = { recapDraft = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                placeholder = { Text("e.g. Worked on net kills and backhand clears. Practice shadow footwork this week.") }
            )
            if (recapDraft != recap) {
                Button(
                    onClick = { onSaveRecap(recapDraft) },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Share Recap with Client")
                    }
                }
            }
        } else {
            Text(recap)
        }
    }
}

@Composable
private fun ReasonSection(title: String, reason: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StatusCancelled.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Cancel, contentDescription = null, tint = StatusCancelled, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Coach payment methods dialog ─────────────────────────────────────────────

@Composable
private fun CoachPaymentMethodsDialog(
    coachName: String,
    payments: Map<String, String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Pay $coachName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (payments.isEmpty()) {
                    Text("No payment methods available for this coach.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    payments.entries.sortedBy { it.key }.forEach { (key, value) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(key.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                paymentDeepLink(key, value)?.let { url ->
                                    IconButton(onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", tint = LogoBlue)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun paymentDeepLink(key: String, value: String): String? {
    val v = value.trim().removePrefix("@")
    if (v.isEmpty()) return null
    return when (key.lowercase()) {
        "venmo" -> "https://venmo.com/u/$v"
        "paypal" -> "https://paypal.me/$v"
        "cashapp", "cash app" -> "https://cash.app/\$${v.removePrefix("$")}"
        else -> null
    }
}

// ── Status helpers (aligned with BookingsScreen conventions) ─────────────────

private fun displayStatus(status: String?): String = when ((status ?: "").lowercase()) {
    "confirmed" -> "Confirmed"
    "requested" -> "Requested"
    "pending acceptance" -> "Pending Acceptance"
    "pending_payment" -> "Pending Payment"
    "payment_submitted" -> "Payment Submitted"
    "rejected" -> "Rejected"
    "declined" -> "Declined"
    "declined_by_client" -> "Declined By Client"
    "cancelled" -> "Cancelled"
    else -> (status ?: "Unknown").replace("_", " ").replaceFirstChar { it.uppercase() }
}

private fun statusColor(status: String?): Color = when ((status ?: "").lowercase()) {
    "confirmed" -> LogoGreen
    "requested" -> LogoBlue
    "pending acceptance", "pending_payment", "payment_submitted" -> Color(0xFFF57C00)
    "rejected", "declined", "declined_by_client", "cancelled" -> StatusCancelled
    else -> Color.Gray
}
