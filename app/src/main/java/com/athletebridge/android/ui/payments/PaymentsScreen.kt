package com.athletebridge.android.ui.payments

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.athletebridge.android.data.model.CoachTier
import com.athletebridge.android.ui.auth.AuthViewModel
import com.athletebridge.android.ui.theme.LogoBlue
import java.text.DateFormat
import java.util.*
import kotlinx.coroutines.launch

// ── Date-range filtering ─────────────────────────────────────────────────────

internal enum class SummaryRange(val label: String) {
    LAST_30("Last 30 Days"),
    LAST_90("Last 90 Days"),
    YTD("Year to Date"),
    CUSTOM("Custom")
}

internal fun filterInRange(
    booking: BookingItem,
    range: SummaryRange,
    customStart: Date,
    customEnd: Date
): Boolean {
    val date = booking.startAt ?: return false
    val now = Date()
    return when (range) {
        SummaryRange.LAST_30 -> {
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.time
            date >= start && date <= now
        }
        SummaryRange.LAST_90 -> {
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -90) }.time
            date >= start && date <= now
        }
        SummaryRange.YTD -> {
            val start = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.time
            date >= start && date <= now
        }
        SummaryRange.CUSTOM -> {
            // customStart/customEnd hold DatePicker selections (UTC midnight of the chosen days).
            // Compare calendar days so the whole end day counts and the evening before the start doesn't.
            val day = date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            day >= pickerDay(customStart) && day <= pickerDay(customEnd)
        }
    }
}

/** Material3 DatePicker selections are UTC-midnight millis; these convert them to and from calendar days. */
internal fun pickerDay(date: Date): java.time.LocalDate =
    date.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate()

internal fun pickerDate(day: java.time.LocalDate): Date =
    Date.from(day.atStartOfDay(java.time.ZoneOffset.UTC).toInstant())

// ── Helpers ──────────────────────────────────────────────────────────────────

internal fun formatUSD(amount: Double): String =
    if (amount >= 1000) String.format("$%.0f", amount) else String.format("$%.2f", amount)

private fun platformPlaceholder(platform: String): String = when (platform) {
    "cashapp" -> "e.g., \$username"
    "venmo" -> "e.g., @username"
    "paypal" -> "e.g., paypal.me/username"
    "zelle" -> "Phone number or email"
    else -> "Username or handle"
}

internal fun paymentDeepLink(platform: String, handle: String): String? =
    com.athletebridge.android.util.PaymentLinks.deepLink(platform, handle)

// ── Main Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    isCoach: Boolean,
    authViewModel: AuthViewModel = hiltViewModel(),
    viewModel: PaymentsViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uid = authState.user?.uid
    val coach = authState.currentCoach
    val client = authState.currentClient

    // Load bookings when uid is known
    LaunchedEffect(uid) {
        uid ?: return@LaunchedEffect
        if (isCoach) viewModel.loadCoachBookings(uid) else viewModel.loadClientBookings(uid)
    }

    // Watch for Google Play purchase results (e.g. if purchase initiated from this screen)
    LaunchedEffect(uid) {
        uid?.let { viewModel.handlePlayPurchaseResult(it) }
    }

    // Sheet visibility state
    var showCoachPaymentsSheet by remember { mutableStateOf(false) }
    var showClientSummary by remember { mutableStateOf(false) }
    var showCoachSummary by remember { mutableStateOf(false) }
    var showUpgradeAlert by remember { mutableStateOf(false) }
    var showCoachPickerForNotify by remember { mutableStateOf(false) }
    var coachPickerBooking by remember { mutableStateOf<BookingItem?>(null) }
    var selectedPaymentTab by remember { mutableIntStateOf(0) }

    // Payment handle form state
    val paymentPlatforms = listOf("paypal", "venmo", "zelle", "cashapp")
    var selectedPlatform by remember { mutableStateOf("paypal") }
    var handleInput by remember { mutableStateOf("") }

    val tier = coach?.subscriptionTier ?: CoachTier.FREE
    val totalPaid = uiState.paidBookings.sumOf { it.rateUSD ?: 0.0 }
    val canAccessSummary = tier.hasAccess("revenue_insights")

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.purchaseSuccess) {
        if (uiState.purchaseSuccess) {
            snackbarHostState.showSnackbar("Subscription activated successfully!")
            viewModel.clearPurchaseSuccess()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Payments") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isCoach) {
                // ── Coach ─────────────────────────────────────────────────

                // Revenue header
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CreditCard, contentDescription = null,
                            modifier = Modifier.size(36.dp), tint = LogoBlue
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Payments",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Total Revenue: ${formatUSD(totalPaid)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Revenue Summary button (gated)
                OutlinedButton(
                    onClick = { if (canAccessSummary) showCoachSummary = true else showUpgradeAlert = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (canAccessSummary) Icons.Default.BarChart else Icons.Default.Lock,
                        contentDescription = null, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Revenue Summary")
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (canAccessSummary) formatUSD(totalPaid) else "Plus / Pro",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider()

                // Payment Handles
                Text(
                    "Payment Handles",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val payments = coach?.payments
                if (!payments.isNullOrEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            payments.entries.sortedBy { it.key }.forEachIndexed { index, (platform, handle) ->
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Payment, contentDescription = null,
                                        tint = LogoBlue, modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        platform.replaceFirstChar { it.uppercase() },
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        handle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (index < payments.size - 1) {
                                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "No payment handles saved yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Add payment handle form
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Add Payment Handle", style = MaterialTheme.typography.titleSmall)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            paymentPlatforms.forEach { platform ->
                                FilterChip(
                                    selected = selectedPlatform == platform,
                                    onClick = { selectedPlatform = platform },
                                    label = { Text(platform.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = handleInput,
                            onValueChange = { handleInput = it },
                            label = { Text(platformPlaceholder(selectedPlatform)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val coachId = uid ?: return@Button
                                val handle = handleInput.trim()
                                if (handle.isNotEmpty()) {
                                    viewModel.savePaymentHandle(
                                        coachId, selectedPlatform, handle,
                                        coach?.payments ?: emptyMap()
                                    )
                                    handleInput = ""
                                }
                            },
                            enabled = handleInput.isNotBlank() && !uiState.isSavingPaymentHandle,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isSavingPaymentHandle) {
                                CircularProgressIndicator(
                                    Modifier.size(16.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Save Payment Handle")
                            }
                        }
                    }
                }

            } else {
                // ── Client ────────────────────────────────────────────────

                // Total paid header
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Total Paid: ${formatUSD(totalPaid)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Payments Summary button
                OutlinedButton(
                    onClick = { showClientSummary = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Payments Summary")
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatUSD(totalPaid),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Unpaid / Paid tabs
                TabRow(selectedTabIndex = selectedPaymentTab) {
                    Tab(
                        selected = selectedPaymentTab == 0,
                        onClick = { selectedPaymentTab = 0 },
                        text = { Text("Unpaid (${uiState.unpaidBookings.size})") }
                    )
                    Tab(
                        selected = selectedPaymentTab == 1,
                        onClick = { selectedPaymentTab = 1 },
                        text = { Text("Paid (${uiState.paidBookings.size})") }
                    )
                }

                val displayedBookings =
                    if (selectedPaymentTab == 0) uiState.unpaidBookings else uiState.paidBookings

                if (displayedBookings.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (selectedPaymentTab == 0) "No unpaid bookings." else "No paid bookings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    displayedBookings.forEach { booking ->
                        ClientBookingRow(
                            booking = booking,
                            showActions = selectedPaymentTab == 0,
                            onMakePayment = {
                                val coachName = booking.allCoachNames.firstOrNull()
                                    ?: booking.coachName ?: "Coach"
                                viewModel.loadCoachPayments(booking.coachID, coachName)
                                showCoachPaymentsSheet = true
                            },
                            onNotifyCoach = {
                                val coachIds = booking.allCoachIDs
                                if (coachIds.size > 1) {
                                    coachPickerBooking = booking
                                    showCoachPickerForNotify = true
                                } else {
                                    val coachId = coachIds.firstOrNull() ?: return@ClientBookingRow
                                    viewModel.notifyCoachOfPayment(
                                        coachId, booking.id, client?.name ?: "A client"
                                    )
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // ── Dialogs & Sheets ─────────────────────────────────────────────────────

    if (showUpgradeAlert) {
        AlertDialog(
            onDismissRequest = { showUpgradeAlert = false },
            title = { Text("Upgrade Required") },
            text = {
                Text(
                    "Revenue Insights is a Coach Plus feature. " +
                    "Coach Pro additionally lists you in the Coach Search Engine. Upgrade to unlock."
                )
            },
            confirmButton = {
                TextButton(onClick = { showUpgradeAlert = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpgradeAlert = false }) { Text("Cancel") }
            }
        )
    }

    if (showCoachPaymentsSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showCoachPaymentsSheet = false },
            sheetState = sheetState
        ) {
            CoachPaymentsContent(
                coachName = uiState.selectedCoachName,
                payments = uiState.selectedCoachPayments
            )
        }
    }

    if (showCoachPickerForNotify && coachPickerBooking != null) {
        val booking = coachPickerBooking!!
        AlertDialog(
            onDismissRequest = { showCoachPickerForNotify = false },
            title = { Text("Select Coach to Notify") },
            text = {
                Column {
                    booking.allCoachIDs.forEachIndexed { index, coachId ->
                        val name = booking.allCoachNames.getOrNull(index) ?: "Coach"
                        TextButton(onClick = {
                            showCoachPickerForNotify = false
                            viewModel.notifyCoachOfPayment(coachId, booking.id, client?.name ?: "A client")
                        }) { Text(name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCoachPickerForNotify = false }) { Text("Cancel") }
            }
        )
    }

    if (showClientSummary) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showClientSummary = false }, sheetState = sheetState) {
            SummarySheetContent(
                title = "Payments Summary",
                paidBookings = uiState.paidBookings,
                unpaidCount = uiState.unpaidBookings.size,
                groupLabel = "By Coach",
                groupName = { it.allCoachNames.firstOrNull() ?: it.coachName ?: "Coach" }
            )
        }
    }

    if (showCoachSummary) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showCoachSummary = false }, sheetState = sheetState) {
            SummarySheetContent(
                title = "Revenue Summary",
                paidBookings = uiState.paidBookings,
                unpaidCount = uiState.unpaidBookings.size,
                groupLabel = "By Client",
                groupName = { it.clientName ?: "Client" }
            )
        }
    }
}

// ── Helper Composables ───────────────────────────────────────────────────────

@Composable
private fun ClientBookingRow(
    booking: BookingItem,
    showActions: Boolean,
    onMakePayment: () -> Unit,
    onNotifyCoach: () -> Unit
) {
    val isGroup = booking.isGroupBooking == true || booking.allCoachIDs.size > 1
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isGroup) {
                Icon(
                    Icons.Default.Group, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = Color.Blue
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (isGroup) "Group Session"
                else (booking.allCoachNames.firstOrNull() ?: booking.coachName ?: "Coach"),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                (booking.paymentStatus ?: "unpaid").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isGroup && booking.allCoachNames.isNotEmpty()) {
            Text(
                "Coaches: ${booking.allCoachNames.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        booking.rateUSD?.let {
            Text(
                "${formatUSD(it)}/hr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        booking.startAt?.let {
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showActions) {
            Spacer(Modifier.height(6.dp))
            Row {
                OutlinedButton(onClick = onNotifyCoach) {
                    Text("Notify Coach", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onMakePayment) {
                    Text("Make Payment", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CoachPaymentsContent(
    coachName: String,
    payments: Map<String, String>
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Pay $coachName",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        if (payments.isEmpty()) {
            Text("No payment methods provided yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            payments.entries.sortedBy { it.key }.forEach { (platform, handle) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                platform.replaceFirstChar { it.uppercase() },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(handle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val deepLink = paymentDeepLink(platform, handle)
                        if (deepLink != null) {
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    CustomTabsIntent.Builder().build()
                                        .launchUrl(context, Uri.parse(deepLink))
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    "Open ${platform.replaceFirstChar { it.uppercase() }}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummarySheetContent(
    title: String,
    paidBookings: List<BookingItem>,
    unpaidCount: Int,
    groupLabel: String,
    groupName: (BookingItem) -> String
) {
    var selectedRange by remember { mutableStateOf(SummaryRange.LAST_30) }
    // Kept in DatePicker form (UTC midnight of the chosen day) — see filterInRange
    val defaultCustomStart = remember { pickerDate(java.time.LocalDate.now().minusDays(30)) }
    var customStart by remember { mutableStateOf(defaultCustomStart) }
    var customEnd by remember { mutableStateOf(pickerDate(java.time.LocalDate.now())) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val filteredPaid = remember(paidBookings, selectedRange, customStart, customEnd) {
        paidBookings.filter { filterInRange(it, selectedRange, customStart, customEnd) }
    }
    val totalFiltered = filteredPaid.sumOf { it.rateUSD ?: 0.0 }
    val distribution = remember(filteredPaid) {
        filteredPaid.groupBy { groupName(it) }
            .map { (name, list) -> Triple(name, list.size, list.sumOf { it.rateUSD ?: 0.0 }) }
            .sortedByDescending { it.third }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customStart.time)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customStart = Date(it) }
                    showStartPicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customEnd.time)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customEnd = Date(it) }
                    showEndPicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = state) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // Range selector
        Text(
            "Filter",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryRange.values().forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { selectedRange = range },
                    label = { Text(range.label, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }

        if (selectedRange == SummaryRange.CUSTOM) {
            // Picker dates are UTC midnight; format in UTC so the buttons show the chosen days
            val fmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start: ${fmt.format(customStart)}", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("End: ${fmt.format(customEnd)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider()

        // Totals
        Text(
            "Totals",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth()) {
            Text("Total Paid", modifier = Modifier.weight(1f))
            Text(formatUSD(totalFiltered), fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Paid Bookings", modifier = Modifier.weight(1f))
            Text("${filteredPaid.size}")
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Unpaid Bookings", modifier = Modifier.weight(1f))
            Text("$unpaidCount")
        }

        // Recent paid
        if (filteredPaid.isNotEmpty()) {
            HorizontalDivider()
            Text(
                "Recently Paid",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            filteredPaid.take(10).forEach { b ->
                Row(Modifier.fillMaxWidth()) {
                    Text(groupName(b), modifier = Modifier.weight(1f))
                    Text(
                        formatUSD(b.rateUSD ?: 0.0),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Distribution
        if (distribution.isNotEmpty()) {
            HorizontalDivider()
            Text(
                groupLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            distribution.forEach { (name, count, total) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(name, modifier = Modifier.weight(1f))
                    Text(
                        "$count • ${formatUSD(total)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
