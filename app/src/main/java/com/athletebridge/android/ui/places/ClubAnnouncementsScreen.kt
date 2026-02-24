package com.athletebridge.android.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.ClubAnnouncement
import com.athletebridge.android.data.repository.PlaceRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ClubAnnouncementsUiState(
    val isLoading: Boolean = false,
    val announcements: List<ClubAnnouncement> = emptyList(),
    val isMember: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ClubAnnouncementsViewModel @Inject constructor(
    private val placeRepo: PlaceRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClubAnnouncementsUiState())
    val uiState: StateFlow<ClubAnnouncementsUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun load(placeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val announcements = placeRepo.fetchAnnouncements(placeId)
            val place = placeRepo.fetchPlace(placeId)
            val isMember = place?.isMember(currentUserId) == true || place?.isCreator(currentUserId) == true
            _uiState.update { it.copy(isLoading = false, announcements = announcements, isMember = isMember) }
        }
    }

    fun postAnnouncement(placeId: String, title: String, body: String) {
        viewModelScope.launch {
            placeRepo.createAnnouncement(placeId, title, body, currentUserId)
            val announcements = placeRepo.fetchAnnouncements(placeId)
            _uiState.update { it.copy(announcements = announcements) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubAnnouncementsScreen(
    placeId: String,
    onBack: () -> Unit,
    viewModel: ClubAnnouncementsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPostDialog by remember { mutableStateOf(false) }

    LaunchedEffect(placeId) { viewModel.load(placeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Announcements") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isMember) {
                        IconButton(onClick = { showPostDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Post Announcement")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.announcements.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No announcements yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.announcements, key = { it.id }) { announcement ->
                    AnnouncementCard(announcement)
                }
            }
        }
    }

    if (showPostDialog) {
        PostAnnouncementDialog(
            onDismiss = { showPostDialog = false },
            onPost = { title, body ->
                viewModel.postAnnouncement(placeId, title, body)
                showPostDialog = false
            }
        )
    }
}

@Composable
private fun AnnouncementCard(announcement: ClubAnnouncement) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(announcement.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(announcement.body, style = MaterialTheme.typography.bodyMedium)
            announcement.createdAt?.let { date ->
                Text(
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PostAnnouncementDialog(
    onDismiss: () -> Unit,
    onPost: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post Announcement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPost(title, body) },
                enabled = title.isNotBlank() && body.isNotBlank()
            ) { Text("Post") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
