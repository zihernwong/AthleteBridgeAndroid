package com.athletebridge.android.ui.places

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    placeId: String,
    onBack: () -> Unit,
    onAnnouncements: (String) -> Unit,
    onEvents: (String) -> Unit,
    onCreateEvent: (String) -> Unit,
    viewModel: PlaceDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Club photo picker (admins only): decode, downscale, and upload as JPEG
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val original = BitmapFactory.decodeStream(stream) ?: return@use null
                val maxDim = 1280f
                val scale = minOf(maxDim / original.width, maxDim / original.height, 1f)
                val resized = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * scale).toInt(),
                        (original.height * scale).toInt(),
                        true
                    )
                } else original
                java.io.ByteArrayOutputStream().apply {
                    resized.compress(Bitmap.CompressFormat.JPEG, 75, this)
                }.toByteArray()
            }
        }.getOrNull()
        bytes?.let { viewModel.uploadClubPhoto(it) }
    }

    LaunchedEffect(placeId) { viewModel.loadPlace(placeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.place?.name ?: "Venue") },
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
        } else {
            val place = uiState.place
            if (place == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Venue not found")
                }
            } else {
                val uid = viewModel.currentUserId
                val isMember = place.isMember(uid)
                val isPending = place.isPending(uid)
                val isCreator = place.isCreator(uid)

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Club photo (or map snapshot) with admin-only change control
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.athletebridge.android.ui.components.PlaceImage(
                                place = place,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            )
                            if (place.isAdmin(uid)) {
                                TextButton(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    enabled = !uiState.isUpdating,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (place.photoURL.isNullOrBlank()) "Add Club Photo" else "Change Club Photo")
                                }
                            }
                        }
                    }

                    // Venue info
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(place.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = LogoBlue)
                                    Spacer(Modifier.width(4.dp))
                                    Text(place.address, style = MaterialTheme.typography.bodyMedium)
                                }

                                // Open in Maps
                                TextButton(
                                    onClick = {
                                        val uri = Uri.parse("geo:0,0?q=${Uri.encode(place.address)}")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open in Maps")
                                }

                                place.pricing?.takeIf { it.isNotBlank() }?.let {
                                    Text("Pricing: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Weekly schedule
                    if (place.weeklySchedule.isNotEmpty()) {
                        item {
                            Text("Weekly Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(place.weeklySchedule.entries.toList()) { (day, hours) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(day, fontWeight = FontWeight.Medium)
                                Text(hours, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Membership action
                    item {
                        when {
                            isMember -> OutlinedButton(
                                onClick = { viewModel.leaveMembership() },
                                enabled = !uiState.isUpdating,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCancelled),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Leave Club") }
                            isPending -> OutlinedButton(
                                onClick = { viewModel.cancelMembershipRequest() },
                                enabled = !uiState.isUpdating,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Cancel Request (Pending)") }
                            else -> Button(
                                onClick = { viewModel.requestMembership() },
                                enabled = !uiState.isUpdating,
                                colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Request to Join") }
                        }
                    }

                    // Members count
                    item {
                        Text(
                            "${place.memberCount} member${if (place.memberCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Announcements link
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAnnouncements(placeId) }
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Campaign, contentDescription = null, tint = LogoBlue)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Announcements", fontWeight = FontWeight.SemiBold)
                                    Text("${uiState.announcements.size} announcement${if (uiState.announcements.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Events link
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEvents(placeId) }
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Event, contentDescription = null, tint = LogoBlue)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Events", fontWeight = FontWeight.SemiBold)
                                    val upcoming = uiState.events.filter { it.eventDate?.after(java.util.Date()) == true }
                                    Text("${upcoming.size} upcoming event${if (upcoming.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Create event (members only)
                    if (isMember || isCreator) {
                        item {
                            OutlinedButton(
                                onClick = { onCreateEvent(placeId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create Event")
                            }
                        }
                    }
                }
            }
        }
    }
}

