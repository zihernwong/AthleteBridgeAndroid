package com.athletebridge.android.ui.client

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.ui.coach.CoachViewModel
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.shared.ChipMultiSelect
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFormScreen(
    onCoachSelected: (String) -> Unit = {},
    onPlacesToPlay: () -> Unit = {},
    onPlayersToPlayWith: () -> Unit = {},
    onStringing: () -> Unit = {},
    coachViewModel: CoachViewModel = hiltViewModel()
) {
    val coachState by coachViewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var goals by remember { mutableStateOf("") }
    var selectedAvailability by remember { mutableStateOf(setOf<String>()) }
    var showResults by remember { mutableStateOf(false) }

    val availabilityOptions = listOf("Morning", "Afternoon", "Evening")

    // Name suggestions
    val suggestions = remember(searchText, coachState.coaches) {
        if (searchText.length >= 1) {
            coachState.coaches.map { it.name }
                .filter { it.lowercase().startsWith(searchText.trim().lowercase()) }
                .take(6)
        } else emptyList()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Find a Coach") }) }
    ) { padding ->
        if (!showResults) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Search by coach name
                item {
                    Text("Search by coach name", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Search coaches by name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Name suggestions
                if (suggestions.isNotEmpty() && searchText.isNotBlank()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.forEach { name ->
                                SuggestionChip(
                                    onClick = { searchText = name },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }

                // Goals
                item {
                    Text("Desired Improvement Areas", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = goals,
                        onValueChange = { goals = it },
                        label = { Text("e.g. Confidence, Leadership") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Availability
                item {
                    Text("Preferred Availability", style = MaterialTheme.typography.labelLarge)
                    ChipMultiSelect(
                        items = availabilityOptions,
                        selection = selectedAvailability,
                        onSelectionChange = { selectedAvailability = it }
                    )
                }

                // Find Coaches button
                item {
                    Button(
                        onClick = { showResults = true },
                        colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Find Coaches", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Community shortcuts
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Badminton Community",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    ClientShortcutCard(
                        icon = Icons.Default.LocationOn,
                        title = "Places to Play",
                        subtitle = "Find courts and clubs near you",
                        onClick = onPlacesToPlay
                    )
                }

                item {
                    ClientShortcutCard(
                        icon = Icons.Default.Build,
                        title = "Badminton Stringers",
                        subtitle = "Get your racket strung",
                        onClick = onStringing
                    )
                }

                item {
                    ClientShortcutCard(
                        icon = Icons.Default.People,
                        title = "Players to Play With",
                        subtitle = "Find casual hitting partners",
                        onClick = onPlayersToPlayWith
                    )
                }
            }
        } else {
            // Results screen
            val goalsList = goals.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val filteredCoaches = coachViewModel.filterCoaches(
                searchQuery = searchText,
                goals = goalsList,
                availability = selectedAvailability.toList()
            )

            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Back button
                TextButton(onClick = { showResults = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Back to Search")
                }

                if (coachState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredCoaches.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No coaches found matching your criteria", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            Text(
                                "${filteredCoaches.size} coach${if (filteredCoaches.size != 1) "es" else ""} found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(filteredCoaches, key = { it.id }) { coach ->
                            CoachResultCard(
                                coach = coach,
                                onClick = { onCoachSelected(coach.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientShortcutCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = LogoBlue.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = LogoBlue, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CoachResultCard(coach: Coach, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(
                url = coach.photoURLString,
                name = coach.name,
                size = 50.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(coach.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (coach.specialties.isNotEmpty()) {
                    Text(
                        coach.specialties.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    coach.minimumRate?.let { min ->
                        val max = coach.maximumRate
                        val rateText = if (max != null && max != min) {
                            String.format("$%.0f - $%.0f/hr", min, max)
                        } else {
                            String.format("$%.0f/hr", min)
                        }
                        Text(rateText, style = MaterialTheme.typography.labelSmall, color = LogoGreen)
                    }
                    if (coach.experienceYears > 0) {
                        Text("${coach.experienceYears} yrs exp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
