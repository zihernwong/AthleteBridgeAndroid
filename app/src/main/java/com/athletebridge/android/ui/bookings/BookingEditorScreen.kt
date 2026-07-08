package com.athletebridge.android.ui.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.AwayTime
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.ui.theme.LogoGreen
import java.text.SimpleDateFormat
import java.util.*

// ── Time slot model ───────────────────────────────────────────────────────────

private data class TimeSlot(val startMillis: Long, val endMillis: Long, val label: String)

private fun generateTimeSlots(dateMillis: Long): List<TimeSlot> {
    val slots = mutableListOf<TimeSlot>()
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    val cal = Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    for (hour in 6 until 22) {
        for (minute in listOf(0, 30)) {
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            val startMs = cal.timeInMillis
            val endMs = startMs + 30L * 60 * 1000
            slots.add(TimeSlot(startMs, endMs, fmt.format(Date(startMs))))
        }
    }
    return slots
}

private val BLOCKED_STATUSES = setOf("cancelled", "declined", "declined_by_client", "rejected")

private fun blockedByBooking(slot: TimeSlot, bookings: List<BookingItem>): BookingItem? =
    bookings.firstOrNull { b ->
        val s = b.startAt?.time ?: return@firstOrNull false
        val e = b.endAt?.time ?: return@firstOrNull false
        val status = b.status?.lowercase() ?: ""
        if (status in BLOCKED_STATUSES) return@firstOrNull false
        s < slot.endMillis && e > slot.startMillis
    }

private fun blockedByAwayTime(slot: TimeSlot, awayTimes: List<AwayTime>): AwayTime? =
    awayTimes.firstOrNull { a ->
        val s = a.startAt?.time ?: return@firstOrNull false
        val e = a.endAt?.time ?: return@firstOrNull false
        s < slot.endMillis && e > slot.startMillis
    }

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingEditorScreen(
    coachId: String,
    onBack: () -> Unit,
    onBookingCreated: () -> Unit,
    viewModel: BookingEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(coachId) { viewModel.loadCoach(coachId) }

    // Date/time pickers state
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
    var repeatWeekly by remember { mutableStateOf(false) }
    var repeatWeeks by remember { mutableIntStateOf(4) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val calDateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    val todayStartMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val slots = remember(uiState.selectedCalendarDate) { generateTimeSlots(uiState.selectedCalendarDate) }

    val durationMin = (endDate - startDate) / 60_000L

    LaunchedEffect(uiState.bookingCreated) {
        if (uiState.bookingCreated) onBookingCreated()
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Coach info ────────────────────────────────────────────────────
            uiState.coach?.let { coach ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Coach",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                coach.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            coach.hourlyRate?.let { rate ->
                                Text(
                                    "Hourly Rate: \$${String.format("%.0f", rate)}/hr",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (coach.specialties.isNotEmpty()) {
                                Text(
                                    "Specialties: ${coach.specialties.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Calendar header ───────────────────────────────────────────────
            item {
                Text(
                    "Coach Availability",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tap an available slot to set your booking time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Date navigation ───────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val canGoPrev = uiState.selectedCalendarDate > todayStartMillis
                    IconButton(
                        onClick = {
                            viewModel.selectCalendarDate(coachId, uiState.selectedCalendarDate - 86_400_000L)
                        },
                        enabled = canGoPrev
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous day",
                            tint = if (canGoPrev)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    Text(
                        calDateFormat.format(Date(uiState.selectedCalendarDate)),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = {
                            viewModel.selectCalendarDate(coachId, uiState.selectedCalendarDate + 86_400_000L)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                }
            }

            // ── Slot grid ─────────────────────────────────────────────────────
            if (uiState.isLoadingCalendar) {
                item {
                    Box(
                        Modifier.fillMaxWidth().height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
            } else {
                items(slots, key = { it.startMillis }) { slot ->
                    val blockingBooking = blockedByBooking(slot, uiState.bookingsForDay)
                    val blockingAway = if (blockingBooking == null) blockedByAwayTime(slot, uiState.awayTimesForDay) else null
                    val isBlocked = blockingBooking != null || blockingAway != null
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            slot.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isBlocked) Color(0xFFE53935).copy(alpha = 0.85f)
                                    else LogoGreen.copy(alpha = 0.15f)
                                )
                                .then(
                                    if (!isBlocked) Modifier.clickable {
                                        startDate = slot.startMillis
                                        endDate = maxOf(slot.endMillis, slot.startMillis + 3_600_000L)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            when {
                                blockingBooking != null -> {
                                    val timeStr = if (blockingBooking.startAt != null && blockingBooking.endAt != null) {
                                        "${timeFormat.format(blockingBooking.startAt)} – ${timeFormat.format(blockingBooking.endAt)}"
                                    } else ""
                                    val statusStr = blockingBooking.status
                                        ?.replace("_", " ")
                                        ?.replaceFirstChar { it.uppercase() }
                                        ?: ""
                                    Text(
                                        if (timeStr.isNotEmpty()) "$timeStr · $statusStr" else statusStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                }
                                blockingAway != null -> {
                                    Text(
                                        blockingAway.notes?.takeIf { it.isNotBlank() } ?: "Away",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                }
                                else -> {
                                    Text(
                                        "Available",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Booking form ──────────────────────────────────────────────────
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            item {
                Text(
                    "Booking Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Start
            item {
                Text("Start", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(dateFormat.format(Date(startDate))) }
                    OutlinedButton(
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(timeFormat.format(Date(startDate))) }
                }
            }

            // End
            item {
                Text("End", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(dateFormat.format(Date(endDate))) }
                    OutlinedButton(
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(timeFormat.format(Date(endDate))) }
                }
            }

            // Duration + estimated cost
            if (durationMin > 0) {
                item {
                    val hours = durationMin / 60.0
                    Text(
                        "Duration: ${String.format("%.1f", hours)} hours",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    uiState.coach?.hourlyRate?.let { rate ->
                        val cost = rate * (((durationMin / 30.0).toLong() * 30) / 60.0)
                        Text(
                            "Estimated Cost: \$${String.format("%.2f", cost)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = LogoGreen
                        )
                    }
                }
            }

            // Location
            item {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Notes
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }

            // Repeat weekly
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Repeat weekly", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Book this same time slot for several weeks in a row",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = repeatWeekly, onCheckedChange = { repeatWeekly = it })
                        }
                        if (repeatWeekly) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Weeks:")
                                OutlinedButton(onClick = { if (repeatWeeks > 2) repeatWeeks-- }) { Text("−") }
                                Text("$repeatWeeks", fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { if (repeatWeeks < 12) repeatWeeks++ }) { Text("+") }
                            }
                            Text(
                                "This will send $repeatWeeks booking requests, one per week starting from the selected date.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Validation errors
            uiState.error?.let { error ->
                item {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (durationMin < 60 && endDate > startDate) {
                item {
                    Text(
                        "Minimum booking duration is 1 hour",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (startDate <= System.currentTimeMillis()) {
                item {
                    Text(
                        "Start time must be in the future",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Request button
            item {
                Button(
                    onClick = {
                        viewModel.createBooking(
                            coachId = coachId,
                            startAt = Date(startDate),
                            endAt = Date(endDate),
                            location = location.takeIf { it.isNotBlank() },
                            notes = notes.takeIf { it.isNotBlank() },
                            repeatWeeks = if (repeatWeekly) repeatWeeks else 1
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving &&
                            durationMin >= 60 &&
                            startDate < endDate &&
                            startDate > System.currentTimeMillis(),
                    colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Request Booking")
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // ── Date / time picker dialogs ────────────────────────────────────────────

    if (showStartDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
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
        ) { DatePicker(state = dpState) }
    }

    if (showEndDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
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
        ) { DatePicker(state = dpState) }
    }

    if (showStartTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = startDate }
        val tpState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = (cal.get(Calendar.MINUTE) / 30) * 30
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("Select Start Time") },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply { timeInMillis = startDate }
                    newCal.set(Calendar.HOUR_OF_DAY, tpState.hour)
                    newCal.set(Calendar.MINUTE, (tpState.minute / 30) * 30)
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
        val tpState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = (cal.get(Calendar.MINUTE) / 30) * 30
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("Select End Time") },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply { timeInMillis = endDate }
                    newCal.set(Calendar.HOUR_OF_DAY, tpState.hour)
                    newCal.set(Calendar.MINUTE, (tpState.minute / 30) * 30)
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
