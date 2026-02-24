package com.athletebridge.android.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.SignupEvent
import com.athletebridge.android.data.repository.PlaceRepository
import com.athletebridge.android.ui.theme.LogoGreen
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class SignupEventsUiState(
    val isLoading: Boolean = false,
    val events: List<SignupEvent> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SignupEventsViewModel @Inject constructor(
    private val placeRepo: PlaceRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignupEventsUiState())
    val uiState: StateFlow<SignupEventsUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun load(placeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val events = placeRepo.fetchEvents(placeId)
            _uiState.update { it.copy(isLoading = false, events = events) }
        }
    }

    fun register(placeId: String, eventId: String) {
        viewModelScope.launch {
            placeRepo.registerForEvent(placeId, eventId, currentUserId)
            val events = placeRepo.fetchEvents(placeId)
            _uiState.update { it.copy(events = events) }
        }
    }

    fun unregister(placeId: String, eventId: String) {
        viewModelScope.launch {
            placeRepo.unregisterFromEvent(placeId, eventId, currentUserId)
            val events = placeRepo.fetchEvents(placeId)
            _uiState.update { it.copy(events = events) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupEventsScreen(
    placeId: String,
    onBack: () -> Unit,
    onEventDetail: (String) -> Unit,
    viewModel: SignupEventsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(placeId) { viewModel.load(placeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Events") },
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
        } else if (uiState.events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No upcoming events", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.events, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        currentUserId = viewModel.currentUserId,
                        onClick = { onEventDetail(event.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun EventCard(
    event: SignupEvent,
    currentUserId: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            event.date?.let { date ->
                Text(
                    SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (event.capacity > 0) {
                val spotsText = if (event.isFull) "Full" else "${event.spotsRemaining} spots remaining"
                Text(spotsText, style = MaterialTheme.typography.bodySmall, color = if (event.isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (event.isRegistered(currentUserId)) {
                Text("You're registered", style = MaterialTheme.typography.labelSmall, color = LogoGreen)
            }
        }
    }
}
