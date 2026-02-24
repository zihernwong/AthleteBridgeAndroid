package com.athletebridge.android.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesToPlayScreen(
    onBack: () -> Unit,
    onPlaceDetail: (String) -> Unit,
    viewModel: PlacesToPlayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlaceToPlay?>(null) }

    LaunchedEffect(Unit) { viewModel.loadPlaces() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Places to Play") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = LogoGreen
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Place")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.places.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No venues yet", style = MaterialTheme.typography.titleMedium)
                    Text("Tap + to add a badminton venue", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.places, key = { it.id }) { place ->
                    PlaceCard(
                        place = place,
                        currentUserId = viewModel.currentUserId,
                        onClick = { onPlaceDetail(place.id) },
                        onDelete = { deleteTarget = place }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlaceDialog(
            isSaving = uiState.isSaving,
            onDismiss = { showAddDialog = false },
            onCreate = { name, address, pricing ->
                viewModel.createPlace(name, address, emptyMap(), pricing.takeIf { it.isNotBlank() })
                showAddDialog = false
            }
        )
    }

    deleteTarget?.let { place ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove Venue") },
            text = { Text("Remove \"${place.name}\" from the list?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(place.id)
                    deleteTarget = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PlaceCard(
    place: PlaceToPlay,
    currentUserId: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(place.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${place.memberCount} member${if (place.memberCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LogoBlue
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (place.isMember(currentUserId)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Member", tint = LogoGreen, modifier = Modifier.size(20.dp))
                    }
                    if (onDelete != null && place.isCreator(currentUserId)) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (place.weeklySchedule.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                val scheduleText = place.weeklySchedule.entries.take(2).joinToString(" · ") { "${it.key}: ${it.value}" }
                Text(scheduleText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            place.pricing?.takeIf { it.isNotBlank() }?.let { pricing ->
                Text("Pricing: $pricing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddPlaceDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var pricing by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Venue") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Venue Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pricing, onValueChange = { pricing = it }, label = { Text("Pricing Info (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, address, pricing) },
                enabled = name.isNotBlank() && address.isNotBlank() && !isSaving
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
