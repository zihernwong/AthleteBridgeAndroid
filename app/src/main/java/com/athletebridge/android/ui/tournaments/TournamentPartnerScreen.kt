package com.athletebridge.android.ui.tournaments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.UserSummary
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.shared.ChipMultiSelect
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentPartnerScreen(
    onBack: () -> Unit,
    onMessagePartner: (String) -> Unit,
    viewModel: TournamentPartnerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find a Tournament Partner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Tournament")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tournament search
            item {
                OutlinedTextField(
                    value = uiState.searchText,
                    onValueChange = { viewModel.updateSearch(it) },
                    label = { Text("Search by tournament name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Tournament suggestions
            if (uiState.selectedTournament == null) {
                val suggestions = uiState.filteredTournaments
                if (suggestions.isNotEmpty()) {
                    item {
                        Text("Select a Tournament", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(suggestions) { tournament ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectTournament(tournament) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(tournament.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${SimpleDateFormat("MMM d", Locale.getDefault()).format(tournament.startDate)} - ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(tournament.endDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(tournament.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (uiState.searchText.isNotBlank()) {
                    item {
                        Text("No tournaments found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Selected tournament - preferences
            uiState.selectedTournament?.let { tournament ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tournament.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { viewModel.clearSelection() }) {
                                    Text("Change")
                                }
                            }
                        }
                    }
                }

                // Gender selector
                item {
                    Text("Gender", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Male", "Female").forEach { gender ->
                            FilterChip(
                                selected = uiState.selectedGender == gender,
                                onClick = { viewModel.setGender(gender) },
                                label = { Text(gender) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }

                // Event selector
                item {
                    Text("Events", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ChipMultiSelect(
                        items = listOf("Men's Doubles", "Women's Doubles", "Mixed Doubles"),
                        selection = uiState.selectedEvents,
                        onSelectionChange = { viewModel.setEvents(it) }
                    )
                }

                // Skill level selector
                item {
                    Text("Skill Levels", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ChipMultiSelect(
                        items = listOf("A", "B", "C", "D"),
                        selection = uiState.selectedSkillLevels,
                        onSelectionChange = { viewModel.setSkillLevels(it) }
                    )
                }

                // Action buttons
                item {
                    if (uiState.isParticipant) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.updatePreferences() },
                                enabled = uiState.selectedEvents.isNotEmpty() && !uiState.isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                                modifier = Modifier.weight(1f)
                            ) { Text("Update Preferences") }
                            OutlinedButton(
                                onClick = { viewModel.leaveTournament() },
                                enabled = !uiState.isSaving,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCancelled),
                                modifier = Modifier.weight(1f)
                            ) { Text("Leave Search") }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.joinTournament() },
                            enabled = uiState.selectedEvents.isNotEmpty() && !uiState.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("I'm Looking for a Partner") }
                    }
                }

                // Partners list
                item {
                    Text("Partners Looking to Play", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                val partners = uiState.matchingPartners
                if (partners.isEmpty()) {
                    item {
                        Text("No one else is looking for a partner yet", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(partners, key = { it.id }) { partner ->
                        PartnerCard(
                            partner = partner,
                            partnerInfo = tournament.participants[partner.id],
                            onMessage = { onMessagePartner(partner.id) }
                        )
                    }
                }
            }
        }
    }

    // Create tournament dialog
    if (showCreateDialog) {
        CreateTournamentDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, startDate, endDate, location, signupLink ->
                viewModel.createTournament(name, startDate, endDate, location, signupLink)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun PartnerCard(
    partner: UserSummary,
    partnerInfo: com.athletebridge.android.data.model.TournamentParticipantInfo?,
    onMessage: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(url = partner.photoURL, name = partner.name, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(partner.name, fontWeight = FontWeight.SemiBold)
                partnerInfo?.let { info ->
                    Text(
                        "${info.gender} | ${info.events.joinToString(", ")} | ${info.skillLevels.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                partner.tournamentSoftwareLink?.takeIf { it.isNotBlank() }?.let {
                    Text("Tournament link available", style = MaterialTheme.typography.bodySmall, color = LogoGreen)
                }
            }
            IconButton(onClick = onMessage) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Message", tint = LogoGreen)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTournamentDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Date, Date, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var signupLink by remember { mutableStateOf("") }
    var startDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var endDate by remember { mutableLongStateOf(System.currentTimeMillis() + 86400000) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Tournament") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tournament Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = signupLink, onValueChange = { signupLink = it }, label = { Text("Signup Link (optional)") }, modifier = Modifier.fillMaxWidth())
                Text("Start Date:")
                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(dateFmt.format(Date(startDate)))
                }
                Text("End Date:")
                OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(dateFmt.format(Date(endDate)))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, Date(startDate), Date(endDate), location, signupLink.takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank() && location.isNotBlank() && endDate >= startDate
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showStartPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { startDate = it }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showEndPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { endDate = it }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}
