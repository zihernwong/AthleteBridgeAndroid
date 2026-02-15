package com.athletebridge.android.ui.coach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachDetailScreen(
    coachId: String,
    onBack: () -> Unit = {},
    onMessageCoach: (String) -> Unit = {},
    onBookCoach: (String) -> Unit = {},
    coachViewModel: CoachViewModel = hiltViewModel()
) {
    val uiState by coachViewModel.uiState.collectAsState()
    val coach = uiState.coaches.find { it.id == coachId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(coach?.name ?: "Coach") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (coach == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (uiState.isLoading) CircularProgressIndicator()
                else Text("Coach not found")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar + name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarView(url = coach.photoURLString, name = coach.name, size = 80.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(coach.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (coach.city != null) {
                            Text(coach.city, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Specialties
                if (coach.specialties.isNotEmpty()) {
                    Text("Specialties", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        coach.specialties.forEach { specialty ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(specialty) }
                            )
                        }
                    }
                }

                // Rate
                coach.minimumRate?.let { min ->
                    val max = coach.maximumRate
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachMoney, contentDescription = null, tint = LogoGreen)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Rate", style = MaterialTheme.typography.labelMedium)
                                val rateText = if (max != null && max != min) {
                                    String.format("$%.2f - $%.2f per hour", min, max)
                                } else {
                                    String.format("$%.2f per hour", min)
                                }
                                Text(rateText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Experience + Availability
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (coach.experienceYears > 0) {
                        Card(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Experience", style = MaterialTheme.typography.labelMedium)
                                Text("${coach.experienceYears} years", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    coach.meetingPreference?.let { pref ->
                        Card(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Meeting", style = MaterialTheme.typography.labelMedium)
                                Text(pref, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Availability
                if (coach.availability.isNotEmpty()) {
                    Text("Availability", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        coach.availability.forEach { avail ->
                            SuggestionChip(onClick = {}, label = { Text(avail) })
                        }
                    }
                }

                // Bio
                if (!coach.bio.isNullOrBlank()) {
                    Text("About", style = MaterialTheme.typography.labelLarge)
                    Text(coach.bio, style = MaterialTheme.typography.bodyMedium)
                }

                // Action buttons
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onMessageCoach(coach.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = LogoBlue),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Message")
                    }
                    Button(
                        onClick = { onBookCoach(coach.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Book")
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
