package com.athletebridge.android.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.data.model.SignupEvent
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Android port of iOS PlacesToPlayContactView: claim contact of a place, approve/decline
 * join requests, manage members, send announcements, and manage event registrations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubManagementScreen(
    onBack: () -> Unit,
    onManageEvent: (eventId: String) -> Unit,
    viewModel: ClubManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var announcementPlace by remember { mutableStateOf<PlaceToPlay?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(uiState.message, uiState.error) {
        (uiState.message ?: uiState.error)?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val uid = viewModel.currentUserId
    val myPlaces = uiState.myPlaces(uid)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Club Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // My Places — claim/release contact
                item {
                    Text("My Places", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Select the places you are the contact for. Other users will see your name and be able to message you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.places.isEmpty()) {
                    item { Text("No places to play have been added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(uiState.places.size) { i ->
                        val place = uiState.places[i]
                        val isMine = uiState.manages(place, uid)
                        Card(Modifier.fillMaxWidth().clickable { viewModel.toggleContact(place) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(
                                    if (isMine) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isMine) LogoGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(place.name)
                                    if (place.address.isNotEmpty()) {
                                        Text(place.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    val contact = place.contactName
                                    if (!contact.isNullOrEmpty() && !isMine) {
                                        Text("Contact: $contact", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Club management sections for the places I manage
                for (place in myPlaces) {
                    val pending = place.pendingMemberEntries
                    if (pending.isNotEmpty()) {
                        item(key = "pending_header_${place.id}") {
                            Text("${place.name} — Pending Requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(pending.size, key = { "pending_${place.id}_$it" }) { i ->
                            val p = pending[i]
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        AvatarView(url = uiState.photoUrls[p.id], name = p.name, size = 40.dp)
                                        Text(p.name, fontWeight = FontWeight.Medium)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(
                                            onClick = { viewModel.approveMember(place.id, p.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Accept")
                                        }
                                        Button(
                                            onClick = { viewModel.rejectMember(place.id, p.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Decline")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Members
                    item(key = "members_header_${place.id}") {
                        Text("${place.name} — Members (${place.members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (place.members.isEmpty()) {
                        item(key = "members_empty_${place.id}") {
                            Text("No members yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(place.members.size, key = { "member_${place.id}_$it" }) { i ->
                            val m = place.members[i]
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AvatarView(url = uiState.photoUrls[m.id], name = m.name, size = 36.dp)
                                    Text(m.name, modifier = Modifier.weight(1f))
                                    if (m.id != uid) {
                                        IconButton(onClick = { viewModel.removeMember(place.id, m.id) }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove member", tint = StatusCancelled)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Announcements
                    item(key = "announce_${place.id}") {
                        OutlinedButton(
                            onClick = { announcementPlace = place },
                            enabled = place.members.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, tint = LogoGreen)
                            Spacer(Modifier.width(8.dp))
                            Text("Send Announcement")
                        }
                    }
                }

                // Managing Registrations
                item {
                    Text("Managing Registrations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                if (myPlaces.isEmpty()) {
                    item { Text("Claim a place above to manage your club and registrations.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    for (place in myPlaces) {
                        val upcoming = uiState.upcomingEvents(place.id)
                        item(key = "registrations_${place.id}") {
                            Card(Modifier.fillMaxWidth()) {
                                Column {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.Groups, contentDescription = null, tint = LogoGreen)
                                        Column(Modifier.weight(1f)) {
                                            Text(place.name)
                                            Text(
                                                "${upcoming.size} upcoming event${if (upcoming.size == 1) "" else "s"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    upcoming.forEach { event ->
                                        HorizontalDivider()
                                        EventManageRow(event = event, onClick = { onManageEvent(event.id) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    announcementPlace?.let { place ->
        SendAnnouncementDialog(
            place = place,
            isSending = uiState.isSending,
            onSend = { title, body ->
                viewModel.sendAnnouncement(place.id, title, body)
                announcementPlace = null
            },
            onDismiss = { announcementPlace = null }
        )
    }
}

@Composable
private fun EventManageRow(event: SignupEvent, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.title, fontWeight = FontWeight.Medium)
            event.eventDate?.let {
                Text(
                    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${event.signupCount} / ${event.maxSignups} signed up",
                style = MaterialTheme.typography.labelMedium,
                color = if (event.isFull) StatusCancelled else LogoGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SendAnnouncementDialog(
    place: PlaceToPlay,
    isSending: Boolean,
    onSend: (title: String, body: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val isValid = title.isNotBlank() && body.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Announcement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                Text(
                    "This will send a push notification to all ${place.members.size} member${if (place.members.size == 1) "" else "s"} of ${place.name}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(title.trim(), body.trim()) },
                enabled = isValid && !isSending
            ) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Manage a single event's registrations ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageEventScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: ClubManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCapacityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    val event = uiState.events.firstOrNull { it.id == eventId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            event == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Event not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            event.eventDate?.let {
                                Text(
                                    SimpleDateFormat("EEEE, MMM d, yyyy h:mm a", Locale.getDefault()).format(it),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${event.signupCount} / ${event.maxSignups} signed up",
                                fontWeight = FontWeight.SemiBold,
                                color = if (event.isFull) StatusCancelled else LogoGreen
                            )
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { showCapacityDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Change Capacity (${event.maxSignups})")
                    }
                }
                item {
                    Text("Registrations (${event.signups.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (event.signups.isEmpty()) {
                    item { Text("No one has signed up yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(event.signups.size) { i ->
                        val signup = event.signups[i]
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(signup.name)
                                    if (signup.email.isNotEmpty()) {
                                        Text(signup.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                FilterChip(
                                    selected = signup.paid,
                                    onClick = { viewModel.toggleSignupPaid(event.id, signup.id, !signup.paid) },
                                    label = { Text(if (signup.paid) "Paid" else "Unpaid") }
                                )
                                IconButton(onClick = { viewModel.removeSignup(event.id, signup.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove signup", tint = StatusCancelled)
                                }
                            }
                        }
                    }
                }

                if (event.waitlist.isNotEmpty()) {
                    item {
                        Text(
                            "Waitlist (${event.waitlist.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            "The first person in line is promoted automatically when a spot opens.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val sortedWaitlist = event.waitlist.sortedBy { it.signedUpAt ?: java.util.Date(0) }
                    items(sortedWaitlist.size) { i ->
                        val entry = sortedWaitlist[i]
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("#${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name)
                                    if (entry.email.isNotEmpty()) {
                                        Text(entry.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCapacityDialog && event != null) {
        var capacityText by remember { mutableStateOf(event.maxSignups.toString()) }
        val newCapacity = capacityText.toIntOrNull()
        AlertDialog(
            onDismissRequest = { showCapacityDialog = false },
            title = { Text("Change Capacity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = capacityText,
                        onValueChange = { capacityText = it },
                        label = { Text("Max signups") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        "Current signups: ${event.signupCount}. New capacity must be at least ${event.signupCount}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        newCapacity?.let { viewModel.updateEventCapacity(event.id, it) }
                        showCapacityDialog = false
                    },
                    enabled = newCapacity != null && newCapacity >= event.signupCount
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCapacityDialog = false }) { Text("Cancel") } }
        )
    }
}
