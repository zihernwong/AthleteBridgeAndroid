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
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignupEventDetailUiState())
    val uiState: StateFlow<SignupEventDetailUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

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
            placeRepo.registerForEvent(placeId, eventId, currentUserId)
            val events = placeRepo.fetchEvents(placeId)
            val event = events.find { it.id == eventId }
            _uiState.update { it.copy(isUpdating = false, event = event) }
        }
    }

    fun unregister() {
        val placeId = _uiState.value.placeId
        val eventId = _uiState.value.event?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            placeRepo.unregisterFromEvent(placeId, eventId, currentUserId)
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

                            event.date?.let { date ->
                                Text(
                                    SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(date),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            event.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }

                            if (event.capacity > 0) {
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Capacity", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${event.registeredUserIds.size} / ${event.capacity}")
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

                    if (isRegistered) {
                        Button(
                            onClick = { viewModel.unregister() },
                            enabled = !uiState.isUpdating,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Unregister") }
                    } else {
                        Button(
                            onClick = { viewModel.register() },
                            enabled = !uiState.isUpdating && !event.isFull,
                            colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (event.isFull) "Event Full" else "Register") }
                    }
                }
            }
        }
    }
}
