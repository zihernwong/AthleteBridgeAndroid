package com.athletebridge.android.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.ClubAnnouncement
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.data.model.SignupEvent
import com.athletebridge.android.data.repository.PlaceRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaceDetailUiState(
    val isLoading: Boolean = false,
    val place: PlaceToPlay? = null,
    val announcements: List<ClubAnnouncement> = emptyList(),
    val events: List<SignupEvent> = emptyList(),
    val isUpdating: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    private val placeRepo: PlaceRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceDetailUiState())
    val uiState: StateFlow<PlaceDetailUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun loadPlace(placeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val place = placeRepo.fetchPlace(placeId)
            val announcements = placeRepo.fetchAnnouncements(placeId)
            val events = placeRepo.fetchEvents(placeId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    place = place,
                    announcements = announcements,
                    events = events
                )
            }
        }
    }

    fun requestMembership() {
        val placeId = _uiState.value.place?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            placeRepo.requestMembership(placeId, currentUserId)
            val place = placeRepo.fetchPlace(placeId)
            _uiState.update { it.copy(isUpdating = false, place = place) }
        }
    }

    fun cancelMembershipRequest() {
        val placeId = _uiState.value.place?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            placeRepo.cancelMembershipRequest(placeId, currentUserId)
            val place = placeRepo.fetchPlace(placeId)
            _uiState.update { it.copy(isUpdating = false, place = place) }
        }
    }

    fun leaveMembership() {
        val placeId = _uiState.value.place?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            placeRepo.leaveMembership(placeId, currentUserId)
            val place = placeRepo.fetchPlace(placeId)
            _uiState.update { it.copy(isUpdating = false, place = place) }
        }
    }
}
