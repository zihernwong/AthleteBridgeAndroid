package com.athletebridge.android.ui.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.PlayerToPlayWith
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.shared.ChipMultiSelect
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersToPlayWithScreen(
    onBack: () -> Unit,
    onMessagePlayer: (String) -> Unit,
    userName: String,
    photoURL: String?,
    viewModel: PlayersToPlayWithViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showListingSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Players to Play With") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showListingSheet = true }) {
                        Icon(
                            if (uiState.myListing != null) Icons.Default.Edit else Icons.Default.PersonAdd,
                            contentDescription = "My Listing"
                        )
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
            Column(Modifier.fillMaxSize()) {
                // Search bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search players...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )

                // Filters
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filter by Skill Level", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Beginner", "Intermediate", "Advanced").forEach { level ->
                            FilterChip(
                                selected = uiState.filterSkillLevel == level,
                                onClick = { viewModel.setFilterSkillLevel(level) },
                                label = { Text(level) }
                            )
                        }
                    }

                    Text("Filter by Availability", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Morning", "Evening", "Weekend").forEach { avail ->
                            FilterChip(
                                selected = uiState.filterAvailability == avail,
                                onClick = { viewModel.setFilterAvailability(avail) },
                                label = { Text(avail) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                if (uiState.isLoading && !uiState.isRefreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.filteredPlayers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("No players found", style = MaterialTheme.typography.titleMedium)
                            Text("Be the first to create a listing!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.filteredPlayers, key = { it.id }) { player ->
                            PlayerCard(
                                player = player,
                                onMessage = { onMessagePlayer(player.userId) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showListingSheet) {
        PlayerListingSheet(
            existing = uiState.myListing,
            isSaving = uiState.isSaving,
            onDismiss = { showListingSheet = false },
            onSave = { skillLevel, city, availability ->
                viewModel.saveListing(userName, photoURL, skillLevel, city, availability)
                showListingSheet = false
            },
            onDelete = {
                viewModel.deleteListing()
                showListingSheet = false
            }
        )
    }
}

@Composable
private fun PlayerCard(player: PlayerToPlayWith, onMessage: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(url = player.photoURLString, name = player.name, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(player.name, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        if (player.skillLevel.isNotBlank()) append(player.skillLevel)
                        if (player.city.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(player.city)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (player.availability.isNotEmpty()) {
                    Text(
                        player.availability.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = LogoBlue
                    )
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
private fun PlayerListingSheet(
    existing: PlayerToPlayWith?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>) -> Unit,
    onDelete: () -> Unit
) {
    var skillLevel by remember { mutableStateOf(existing?.skillLevel ?: "") }
    var city by remember { mutableStateOf(existing?.city ?: "") }
    var availability by remember { mutableStateOf(existing?.availability?.toSet() ?: emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit My Listing" else "Create Listing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Skill Level", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Beginner", "Intermediate", "Advanced").forEach { level ->
                        FilterChip(
                            selected = skillLevel == level,
                            onClick = { skillLevel = level },
                            label = { Text(level) }
                        )
                    }
                }

                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Availability", style = MaterialTheme.typography.labelMedium)
                ChipMultiSelect(
                    items = listOf("Morning", "Afternoon", "Evening", "Weekend"),
                    selection = availability,
                    onSelectionChange = { availability = it }
                )

                if (existing != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Remove My Listing") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(skillLevel, city, availability.toList()) },
                enabled = skillLevel.isNotBlank() && !isSaving
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
