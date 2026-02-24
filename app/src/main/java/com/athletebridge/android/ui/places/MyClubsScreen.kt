package com.athletebridge.android.ui.places

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
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.data.repository.PlaceRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyClubsUiState(
    val isLoading: Boolean = false,
    val clubs: List<PlaceToPlay> = emptyList()
)

@HiltViewModel
class MyClubsViewModel @Inject constructor(
    private val placeRepo: PlaceRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyClubsUiState())
    val uiState: StateFlow<MyClubsUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun loadMyClubs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val uid = currentUserId
            val allPlaces = placeRepo.fetchPlaces()
            val myClubs = allPlaces.filter { it.isMember(uid) || it.isCreator(uid) }
            _uiState.update { it.copy(isLoading = false, clubs = myClubs) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyClubsScreen(
    onBack: () -> Unit,
    onPlaceDetail: (String) -> Unit,
    viewModel: MyClubsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadMyClubs() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Clubs") },
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
        } else if (uiState.clubs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No clubs yet", style = MaterialTheme.typography.titleMedium)
                    Text("Join clubs from Places to Play", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.clubs, key = { it.id }) { club ->
                    PlaceCard(
                        place = club,
                        currentUserId = viewModel.currentUserId,
                        onClick = { onPlaceDetail(club.id) },
                        onDelete = null
                    )
                }
            }
        }
    }
}
