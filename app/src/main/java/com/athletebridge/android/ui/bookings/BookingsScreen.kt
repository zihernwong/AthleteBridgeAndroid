package com.athletebridge.android.ui.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import com.athletebridge.android.ui.theme.StatusConfirmed
import com.athletebridge.android.ui.theme.StatusPending
import com.athletebridge.android.util.addBookingToCalendar
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingsScreen(
    isCoach: Boolean,
    onCreateBooking: () -> Unit = {},
    onInputAwayTime: () -> Unit = {},
    onAcceptBooking: (String) -> Unit = {},
    onReviewBooking: (String) -> Unit = {},
    onConfirmedBookings: () -> Unit = {},
    onClientConfirmedBookings: () -> Unit = {},
    onClientRequestedBookings: () -> Unit = {},
    onBookingDetail: (String) -> Unit = {},
    viewModel: BookingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(isCoach) { viewModel.loadBookings(isCoach) }

    var selectedDate by remember { mutableStateOf(Date()) }
    var currentMonthAnchor by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookings") },
                actions = {
                    if (!isCoach) {
                        IconButton(onClick = onCreateBooking) {
                            Icon(Icons.Default.Add, contentDescription = "New Booking")
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        if (uiState.isLoading && !uiState.isRefreshing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                // Coach header: "Input Time Away"
                if (isCoach) {
                    TextButton(
                        onClick = onInputAwayTime,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Input Time Away", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Month calendar
                MonthCalendarSection(
                    monthAnchor = currentMonthAnchor,
                    selectedDate = selectedDate,
                    bookings = uiState.bookings,
                    onPrevMonth = {
                        val cal = Calendar.getInstance().apply {
                            time = currentMonthAnchor
                            add(Calendar.MONTH, -1)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                        currentMonthAnchor = cal.time
                    },
                    onNextMonth = {
                        val cal = Calendar.getInstance().apply {
                            time = currentMonthAnchor
                            add(Calendar.MONTH, 1)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                        currentMonthAnchor = cal.time
                    },
                    onDateSelected = { selectedDate = it }
                )

                Spacer(Modifier.height(8.dp))

                // Selected date bookings
                SelectedDateSection(
                    selectedDate = selectedDate,
                    bookings = uiState.bookings,
                    isCoach = isCoach,
                    onAddToCalendar = { booking ->
                        val coachName = booking.allCoachNames.firstOrNull() ?: "Coach"
                        addBookingToCalendar(context, booking, coachName)
                    },
                    onBookingClick = { booking -> onBookingDetail(booking.id) }
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(12.dp))

                // Coach: awaiting acceptance + awaiting payment confirmation sections
                if (isCoach) {
                    val currentCoachId = uiState.currentUserId
                    val now = remember { Date() }
                    val awaitingAcceptance = uiState.bookings.filter { booking ->
                        val startAt = booking.startAt
                        if (startAt != null && startAt < now) return@filter false
                        when ((booking.status ?: "").lowercase()) {
                            "requested" -> true
                            "partially_accepted" ->
                                booking.coachAcceptances?.get(currentCoachId) != true
                            else -> false
                        }
                    }
                    val awaitingPaymentConfirmation = uiState.bookings.filter { booking ->
                        (booking.status ?: "").lowercase() == "payment_submitted"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Awaiting payment confirmation (Pro coaches)
                        if (awaitingPaymentConfirmation.isNotEmpty()) {
                            Text(
                                "Awaiting Payment Confirmation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            awaitingPaymentConfirmation.forEach { booking ->
                                BookingRowView(
                                    booking = booking,
                                    isCoach = true,
                                    actionButton = {
                                        Button(
                                            onClick = { viewModel.confirmPaymentReceived(booking) },
                                            colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                                        ) { Text("Confirm Payment") }
                                    },
                                    onClick = { onBookingDetail(booking.id) }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }

                        Text(
                            "Bookings Awaiting Your Acceptance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (awaitingAcceptance.isEmpty()) {
                            Text(
                                "No requested bookings",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            awaitingAcceptance.forEach { booking ->
                                BookingRowView(
                                    booking = booking,
                                    isCoach = true,
                                    actionButton = {
                                        Button(
                                            onClick = { onAcceptBooking(booking.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = LogoBlue)
                                        ) { Text("Accept") }
                                    },
                                    onClick = { onBookingDetail(booking.id) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onConfirmedBookings,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = LogoBlue)
                        ) { Text("View Confirmed Bookings") }
                    }
                }

                // Client sections
                if (!isCoach) {
                    val currentClientId = uiState.currentUserId
                    val now = remember { Date() }

                    // Pending confirmation: coach accepted, client needs to confirm or pay
                    val awaitingConfirmation = uiState.bookings.filter { booking ->
                        val startAt = booking.startAt
                        if (startAt != null && startAt < now) return@filter false
                        when ((booking.status ?: "").lowercase()) {
                            "pending acceptance", "pending_payment" -> true
                            "partially_confirmed" ->
                                booking.clientConfirmations?.get(currentClientId) != true
                            else -> false
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Awaiting client confirmation
                        Text(
                            "Bookings Awaiting Your Confirmation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (awaitingConfirmation.isEmpty()) {
                            Text(
                                "No pending bookings",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            awaitingConfirmation.forEach { booking ->
                                val isPendingPayment = (booking.status ?: "").lowercase() == "pending_payment" ||
                                        booking.requiresPaymentUpfront == true
                                BookingRowView(
                                    booking = booking,
                                    isCoach = false,
                                    actionButton = {
                                        Button(
                                            onClick = { onReviewBooking(booking.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                                        ) { Text(if (isPendingPayment) "Pay Now" else "Review Booking") }
                                    },
                                    onClick = { onBookingDetail(booking.id) }
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onClientRequestedBookings,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = LogoBlue)
                        ) { Text("View All Requested Bookings") }
                        Button(
                            onClick = onClientConfirmedBookings,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                        ) { Text("View Confirmed Bookings") }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Load-more indicator / trigger
                if (uiState.hasMore || uiState.isLoadingMore) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isLoadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            TextButton(onClick = { viewModel.loadMoreBookings() }) {
                                Text("Load more")
                            }
                        }
                    }
                    // Auto-trigger when visible
                    LaunchedEffect(uiState.hasMore) {
                        if (uiState.hasMore) viewModel.loadMoreBookings()
                    }
                }
            }
        }
        } // end PullToRefreshBox
    }
}

@Composable
private fun MonthCalendarSection(
    monthAnchor: Date,
    selectedDate: Date,
    bookings: List<BookingItem>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (Date) -> Unit
) {
    val monthTitle = remember(monthAnchor) {
        SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(monthAnchor)
    }

    val monthDays: List<Date> = remember(monthAnchor) {
        val cal = Calendar.getInstance().apply {
            time = monthAnchor
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun … 7=Sat
        val leadingBlanks = (firstDayOfWeek - 1 + 7) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val days = mutableListOf<Date>()
        // Leading days from previous month
        for (i in leadingBlanks downTo 1) {
            val d = Calendar.getInstance().apply { time = cal.time; add(Calendar.DAY_OF_MONTH, -i) }
            days.add(d.time)
        }
        // Days of current month
        for (day in 1..daysInMonth) {
            val d = Calendar.getInstance().apply { time = cal.time; set(Calendar.DAY_OF_MONTH, day) }
            days.add(d.time)
        }
        // Trailing days to fill the last week row
        while (days.size % 7 != 0) {
            val last = days.last()
            val d = Calendar.getInstance().apply { time = last; add(Calendar.DAY_OF_MONTH, 1) }
            days.add(d.time)
        }
        days
    }

    val bookingCounts: Map<String, Int> = remember(bookings) {
        val map = mutableMapOf<String, Int>()
        bookings.forEach { booking ->
            booking.startAt?.let { start ->
                val bCal = Calendar.getInstance().apply { time = start }
                val key = "${bCal.get(Calendar.YEAR)}-${bCal.get(Calendar.MONTH)}-${bCal.get(Calendar.DAY_OF_MONTH)}"
                map[key] = (map[key] ?: 0) + 1
            }
        }
        map
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Header: ‹ Month Year ›
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                monthTitle,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        // Weekday symbols row
        val weekdaySymbols: List<String> = remember {
            DateFormatSymbols.getInstance(Locale.getDefault()).getShortWeekdays()
                .drop(1) // index 0 is an empty string; [1]=Sun…[7]=Sat
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdaySymbols.forEach { symbol ->
                Text(
                    symbol,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        val anchorCal = Calendar.getInstance().apply { time = monthAnchor }
        val selectedCal = Calendar.getInstance().apply { time = selectedDate }
        val todayCal = Calendar.getInstance()

        monthDays.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val dCal = Calendar.getInstance().apply { time = date }
                    val isCurrentMonth = dCal.get(Calendar.YEAR) == anchorCal.get(Calendar.YEAR) &&
                            dCal.get(Calendar.MONTH) == anchorCal.get(Calendar.MONTH)
                    val isSelected = dCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                            dCal.get(Calendar.MONTH) == selectedCal.get(Calendar.MONTH) &&
                            dCal.get(Calendar.DAY_OF_MONTH) == selectedCal.get(Calendar.DAY_OF_MONTH)
                    val isToday = dCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                            dCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                            dCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)
                    val countKey = "${dCal.get(Calendar.YEAR)}-${dCal.get(Calendar.MONTH)}-${dCal.get(Calendar.DAY_OF_MONTH)}"
                    DayCell(
                        date = date,
                        isCurrentMonth = isCurrentMonth,
                        isSelected = isSelected,
                        isToday = isToday,
                        count = bookingCounts[countKey] ?: 0,
                        modifier = Modifier.weight(1f),
                        onClick = { onDateSelected(date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: Date,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val day = remember(date) {
        Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_MONTH)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) LogoBlue.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (isToday) {
                Box(modifier = Modifier.fillMaxSize().border(2.dp, LogoBlue, CircleShape))
            }
            Text(
                "$day",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentMonth) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        if (count > 0) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = LogoBlue,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LogoBlue.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SelectedDateSection(
    selectedDate: Date,
    bookings: List<BookingItem>,
    isCoach: Boolean,
    onAddToCalendar: ((BookingItem) -> Unit)? = null,
    onBookingClick: ((BookingItem) -> Unit)? = null
) {
    val cal = Calendar.getInstance().apply { time = selectedDate }
    val todayCal = Calendar.getInstance()
    val isToday = cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            cal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
            cal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)

    val headerText = if (isToday) "Today" else
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(selectedDate)

    val dayBookings = remember(bookings, selectedDate) {
        bookings.filter { booking ->
            booking.startAt?.let { start ->
                val bCal = Calendar.getInstance().apply { time = start }
                bCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                        bCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH) &&
                        bCal.get(Calendar.DAY_OF_MONTH) == cal.get(Calendar.DAY_OF_MONTH)
            } ?: false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(headerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (dayBookings.isEmpty()) {
            Text(
                if (isToday) "No bookings today" else "No bookings on this date",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            dayBookings.forEach { booking ->
                BookingRowView(
                    booking = booking,
                    isCoach = isCoach,
                    onAddToCalendar = onAddToCalendar?.let { cb -> { cb(booking) } },
                    onClick = onBookingClick?.let { cb -> { cb(booking) } }
                )
            }
        }
    }
}

@Composable
fun BookingRowView(
    booking: BookingItem,
    isCoach: Boolean,
    actionButton: (@Composable () -> Unit)? = null,
    onAddToCalendar: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val isGroup = (booking.isGroupBooking == true) ||
            booking.allCoachIDs.size > 1 ||
            booking.allClientIDs.size > 1

    val displayTitle = when {
        isGroup -> "Group Session"
        isCoach -> booking.allClientNames.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Client"
        else -> booking.allCoachNames.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Coach"
    }

    val statusLower = (booking.status ?: "").lowercase()

    val statusDisplay = when (statusLower) {
        "declined_by_client" -> if (isCoach) "Declined By Client" else "Declined"
        else -> (booking.status ?: "")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }

    val statusColor: Color = when (statusLower) {
        "confirmed", "fully_confirmed" -> StatusConfirmed
        "requested" -> LogoBlue
        "pending acceptance", "pending_payment" -> StatusPending
        "payment_submitted" -> LogoGreen
        "rejected", "declined", "declined_by_client", "cancelled" -> StatusCancelled
        "partially_accepted", "partially_confirmed" -> StatusPending
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val showRate = statusLower in listOf(
        "pending acceptance", "pending_payment", "payment_submitted",
        "confirmed", "fully_confirmed",
        "partially_accepted", "partially_confirmed",
        "rejected", "declined", "declined_by_client", "cancelled"
    )

    val dateTimeFmt = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Title + group icon + status badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isGroup) {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = LogoBlue
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        statusDisplay,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Group: coaches list with acceptance icons
            if (isGroup && booking.allCoachIDs.isNotEmpty()) {
                val bookingRejected = statusLower == "rejected"
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Coaches:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    booking.allCoachIDs.forEachIndexed { idx, coachId ->
                        val coachName = booking.allCoachNames.getOrElse(idx) { "Coach" }
                        val accepted = booking.coachAcceptances?.get(coachId) ?: false
                        val isRejector = booking.rejectedBy == coachId
                        val (icon, tint) = when {
                            isRejector || (bookingRejected && !accepted) ->
                                Icons.Default.Cancel to StatusCancelled
                            accepted -> Icons.Default.CheckCircle to StatusConfirmed
                            else -> Icons.Default.Schedule to StatusPending
                        }
                        ParticipantRow(name = coachName, icon = icon, tint = tint,
                            dimmed = isRejector || (bookingRejected && !accepted))
                    }
                }
            }

            // Group: clients list with confirmation icons
            if (isGroup) {
                if (booking.allClientIDs.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Clients:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        booking.allClientIDs.forEachIndexed { idx, clientId ->
                            val clientName = booking.allClientNames.getOrElse(idx) { "Client" }
                            val confirmed = booking.clientConfirmations?.get(clientId) ?: false
                            ParticipantRow(
                                name = clientName,
                                icon = if (confirmed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                tint = if (confirmed) StatusConfirmed else StatusPending
                            )
                        }
                    }
                } else if (booking.allClientNames.isNotEmpty()) {
                    Text(
                        "Clients: ${booking.allClientNames.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Participant summary
                val coachCount = booking.allCoachIDs.size
                val clientCount = booking.allClientIDs.size
                Text(
                    "$coachCount coach${if (coachCount != 1) "es" else ""}, " +
                            "$clientCount client${if (clientCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LogoBlue
                )
            }

            // Times
            booking.startAt?.let { start ->
                Text(
                    "Start: ${dateTimeFmt.format(start)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            booking.endAt?.let { end ->
                Text(
                    "End: ${dateTimeFmt.format(end)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Rate
            if (showRate) {
                if (isGroup && !booking.coachRates.isNullOrEmpty()) {
                    booking.allCoachIDs.forEachIndexed { idx, coachId ->
                        val coachName = booking.allCoachNames.getOrElse(idx) { "Coach" }
                        val rate = booking.coachRates[coachId]
                        if (rate != null) {
                            Text(
                                "$coachName: \$${String.format("%.2f", rate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    booking.rateUSD?.takeIf { it > 0 }?.let { rate ->
                        Text(
                            "Rate: \$${String.format("%.2f", rate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Rejection reason
            if (statusLower == "rejected") {
                booking.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(
                        "Reason: $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusCancelled
                    )
                }
            }

            // Decline reason
            if (statusLower in listOf("declined_by_client", "declined")) {
                booking.clientDeclineReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(
                        "Reason: $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusCancelled
                    )
                }
            }

            // Location
            booking.location?.takeIf { it.isNotBlank() }?.let { loc ->
                Text(loc, style = MaterialTheme.typography.bodyMedium)
            }

            // Notes
            booking.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Calendar button for confirmed bookings
            if (statusLower in listOf("confirmed", "fully_confirmed") && onAddToCalendar != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onAddToCalendar) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Add to Calendar",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add to Calendar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Inline action button (Accept / Review Booking)
            if (actionButton != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    actionButton()
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    name: String,
    icon: ImageVector,
    tint: Color,
    dimmed: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = if (dimmed) StatusCancelled else MaterialTheme.colorScheme.onSurface
        )
    }
}
