package com.athletebridge.android.ui.roster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Client home → "My Coaches": every coach the client has booked with. */
@Composable
fun ClientCoachRosterScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onPersonDetail: (id: String, name: String) -> Unit,
    viewModel: RosterViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadCoachRoster() }
    RosterScaffold(
        title = "My Coaches",
        emptyText = "No coaches yet — book a session to see your coaches here",
        searchPrompt = "Search coaches",
        showPaid = false,
        onBack = onBack,
        onOpenChat = onOpenChat,
        onPersonDetail = onPersonDetail,
        viewModel = viewModel
    )
}

/** Coach home → "My Clients": every client the coach has had bookings with. */
@Composable
fun CoachClientRosterScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onPersonDetail: (id: String, name: String) -> Unit,
    viewModel: RosterViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadClientRoster() }
    RosterScaffold(
        title = "My Clients",
        emptyText = "No clients yet — accepted bookings will show your clients here",
        searchPrompt = "Search clients",
        showPaid = true,
        onBack = onBack,
        onOpenChat = onOpenChat,
        onPersonDetail = onPersonDetail,
        viewModel = viewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RosterScaffold(
    title: String,
    emptyText: String,
    searchPrompt: String,
    showPaid: Boolean,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onPersonDetail: (id: String, name: String) -> Unit,
    viewModel: RosterViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.chatToOpen) {
        uiState.chatToOpen?.let {
            viewModel.consumeChatNavigation()
            onOpenChat(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.entries.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(emptyText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    val filtered = if (searchText.isBlank()) uiState.entries
                    else uiState.entries.filter { it.name.contains(searchText, ignoreCase = true) }
                    Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text(searchPrompt) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.id }) { entry ->
                                RosterRow(
                                    entry = entry,
                                    photoUrl = uiState.photoUrls[entry.id],
                                    lastSeen = uiState.lastSeen[entry.id],
                                    showPaid = showPaid,
                                    onClick = { onPersonDetail(entry.id, entry.name) },
                                    onMessage = { viewModel.messageUser(entry.id) }
                                )
                            }
                        }
                    }
                }
            }
            if (uiState.isCreatingChat) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Opening chat...")
                    } }
                }
            }
        }
    }
}

@Composable
private fun RosterRow(
    entry: RosterEntry,
    photoUrl: String?,
    lastSeen: Date?,
    showPaid: Boolean,
    onClick: () -> Unit,
    onMessage: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarView(url = photoUrl, name = entry.name, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, fontWeight = FontWeight.SemiBold)
                val parts = buildList {
                    add("${entry.totalSessions} session${if (entry.totalSessions == 1) "" else "s"}")
                    if (showPaid && entry.totalPaid > 0) add(String.format(Locale.US, "$%.0f", entry.totalPaid))
                    entry.lastSessionDate?.let { add(relativeDateString(it)) }
                }
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                lastSeen?.let { PresenceLabel(it) }
            }
            IconButton(onClick = onMessage) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Message", tint = LogoBlue)
            }
        }
    }
}

@Composable
private fun PresenceLabel(lastSeen: Date) {
    val secondsAgo = (System.currentTimeMillis() - lastSeen.time) / 1000
    if (secondsAgo < 300) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Surface(color = Color(0xFF34C759), shape = CircleShape, modifier = Modifier.fillMaxSize()) {}
            }
            Text("Online", style = MaterialTheme.typography.labelSmall, color = Color(0xFF34C759))
        }
    } else {
        Text(
            "Last seen ${lastSeenString(secondsAgo, lastSeen)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Person detail: all bookings with one coach/client ────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterPersonScreen(
    personId: String,
    personName: String,
    isCoach: Boolean,
    onBack: () -> Unit,
    onBookingDetail: (String) -> Unit,
    viewModel: RosterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(personId) {
        if (isCoach) viewModel.loadClientRoster() else viewModel.loadCoachRoster()
    }

    val personBookings = remember(uiState.bookings, personId) {
        uiState.bookings.filter { booking ->
            if (isCoach) {
                booking.clientID == personId || booking.clientIDs?.contains(personId) == true
            } else {
                booking.coachID == personId || booking.coachIDs?.contains(personId) == true
            }
        }.sortedByDescending { it.startAt ?: Date(0) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(personName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (personBookings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No bookings found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(personBookings, key = { it.id }) { booking ->
                    PersonBookingRow(booking = booking, onClick = { onBookingDetail(booking.id) })
                }
            }
        }
    }
}

@Composable
private fun PersonBookingRow(booking: BookingItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    booking.startAt?.let {
                        SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(it)
                    } ?: "Date TBD",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    (booking.status ?: "Unknown").replace("_", " ")
                        .split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } },
                    style = MaterialTheme.typography.labelMedium,
                    color = rosterStatusColor(booking.status)
                )
            }
            booking.location?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            booking.rateUSD?.let {
                Text(String.format(Locale.US, "$%.2f/hr", it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun rosterStatusColor(status: String?): Color = when ((status ?: "").lowercase()) {
    "confirmed" -> LogoGreen
    "requested" -> LogoBlue
    "pending acceptance", "pending_payment", "payment_submitted" -> Color(0xFFF57C00)
    "rejected", "declined", "cancelled", "declined_by_client" -> StatusCancelled
    else -> Color.Gray
}

private fun relativeDateString(date: Date): String {
    val days = ((System.currentTimeMillis() - date.time) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        else -> SimpleDateFormat("M/d/yy", Locale.getDefault()).format(date)
    }
}

private fun lastSeenString(secondsAgo: Long, date: Date): String {
    return when {
        secondsAgo < 3600 -> "${secondsAgo / 60}m ago"
        secondsAgo < 86_400 -> "${secondsAgo / 3600}h ago"
        secondsAgo < 172_800 -> "yesterday"
        secondsAgo < 604_800 -> "${secondsAgo / 86_400}d ago"
        else -> SimpleDateFormat("M/d/yy", Locale.getDefault()).format(date)
    }
}
