package com.athletebridge.android.ui.places

import androidx.compose.foundation.layout.*
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
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled

data class SignupEventDetailUiState(
    val isLoading: Boolean = false,
    val event: SignupEvent? = null,
    val placeId: String = "",
    val isUpdating: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SignupEventDetailViewModel @Inject constructor(
    private val placeRepo: PlaceRepository,
    private val clientRepo: com.athletebridge.android.data.repository.ClientRepository,
    private val coachRepo: com.athletebridge.android.data.repository.CoachRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignupEventDetailUiState())
    val uiState: StateFlow<SignupEventDetailUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    private suspend fun currentUserName(): String =
        clientRepo.fetchClient(currentUserId)?.name?.takeIf { it.isNotBlank() }
            ?: coachRepo.fetchCoach(currentUserId)?.name?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.displayName ?: "Unknown"

    fun load(placeId: String, eventId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, placeId = placeId) }
            val events = placeRepo.fetchEvents(placeId)
            val event = events.find { it.id == eventId }
            _uiState.update { it.copy(isLoading = false, event = event) }
        }
    }

    fun register() {
        val placeId = _uiState.value.placeId
        val eventId = _uiState.value.event?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val result = placeRepo.registerForEvent(
                eventId = eventId,
                userId = currentUserId,
                userName = currentUserName(),
                userEmail = auth.currentUser?.email ?: ""
            )
            val events = placeRepo.fetchEvents(placeId)
            val event = events.find { it.id == eventId }
            _uiState.update {
                it.copy(isUpdating = false, event = event, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun unregister() {
        val placeId = _uiState.value.placeId
        val eventId = _uiState.value.event?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            placeRepo.unregisterFromEvent(eventId, currentUserId)
            val events = placeRepo.fetchEvents(placeId)
            val event = events.find { it.id == eventId }
            _uiState.update { it.copy(isUpdating = false, event = event) }
        }
    }

    fun joinWaitlist() {
        val placeId = _uiState.value.placeId
        val eventId = _uiState.value.event?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val result = placeRepo.joinWaitlist(
                eventId = eventId,
                userId = currentUserId,
                userName = currentUserName(),
                userEmail = auth.currentUser?.email ?: ""
            )
            val events = placeRepo.fetchEvents(placeId)
            val event = events.find { it.id == eventId }
            _uiState.update {
                it.copy(isUpdating = false, event = event, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun leaveWaitlist() {
        val placeId = _uiState.value.placeId
        val eventId = _uiState.value.event?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            placeRepo.leaveWaitlist(eventId, currentUserId)
            val events = placeRepo.fetchEvents(placeId)
            val event = events.find { it.id == eventId }
            _uiState.update { it.copy(isUpdating = false, event = event) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupEventDetailScreen(
    placeId: String,
    eventId: String,
    onBack: () -> Unit,
    viewModel: SignupEventDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(placeId, eventId) { viewModel.load(placeId, eventId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
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
            val event = uiState.event
            if (event == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Event not found")
                }
            } else {
                val uid = viewModel.currentUserId
                val isRegistered = event.isRegistered(uid)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                            event.eventDate?.let { date ->
                                Text(
                                    SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(date),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            event.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }

                            if (event.maxSignups > 0) {
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Capacity", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${event.signupCount} / ${event.maxSignups}")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Spots Remaining", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (event.isFull) "Full" else "${event.spotsRemaining}",
                                        color = if (event.isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    val isWaitlisted = event.isWaitlisted(uid)
                    when {
                        isRegistered -> Button(
                            onClick = { viewModel.unregister() },
                            enabled = !uiState.isUpdating,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Unregister") }

                        isWaitlisted -> {
                            val position = event.waitlistPosition(uid)
                            Text(
                                "You're #${position ?: "?"} on the waitlist — we'll notify you when a spot opens up.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { viewModel.leaveWaitlist() },
                                enabled = !uiState.isUpdating,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Leave Waitlist") }
                        }

                        event.isFull -> {
                            if (event.waitlist.isNotEmpty()) {
                                Text(
                                    "${event.waitlist.size} ${if (event.waitlist.size == 1) "person is" else "people are"} on the waitlist.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.joinWaitlist() },
                                enabled = !uiState.isUpdating,
                                colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Event Full — Join Waitlist") }
                        }

                        else -> Button(
                            onClick = { viewModel.register() },
                            enabled = !uiState.isUpdating,
                            colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Register") }
                    }
                }
            }
        }
    }
}
