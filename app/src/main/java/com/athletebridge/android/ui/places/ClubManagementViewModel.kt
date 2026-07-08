package com.athletebridge.android.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.data.model.SignupEvent
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.NotificationRepository
import com.athletebridge.android.data.repository.PlaceRepository
import com.athletebridge.android.data.repository.UserDirectoryRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ClubManagementUiState(
    val places: List<PlaceToPlay> = emptyList(),
    val events: List<SignupEvent> = emptyList(),
    val photoUrls: Map<String, String?> = emptyMap(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    fun myPlaces(uid: String): List<PlaceToPlay> = places.filter { it.contactUserId == uid }

    fun upcomingEvents(placeId: String): List<SignupEvent> {
        val now = Date()
        return events.filter { it.placeId == placeId && (it.eventDate?.after(now) == true) }
    }
}

@HiltViewModel
class ClubManagementViewModel @Inject constructor(
    private val placeRepo: PlaceRepository,
    private val clientRepo: ClientRepository,
    private val coachRepo: CoachRepository,
    private val userDirectory: UserDirectoryRepository,
    private val notificationRepository: NotificationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClubManagementUiState())
    val uiState: StateFlow<ClubManagementUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            refresh()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun refresh() {
        val places = placeRepo.fetchPlaces()
        val events = placeRepo.fetchAllEvents()
        _uiState.update { it.copy(places = places, events = events) }
        // Avatars for members + pending members of the clubs I manage
        val ids = places.filter { it.contactUserId == currentUserId }
            .flatMap { it.members.map { m -> m.id } + it.pendingMemberEntries.map { p -> p.id } }
            .distinct()
        for (id in ids) {
            if (_uiState.value.photoUrls.containsKey(id)) continue
            val url = userDirectory.fetchUserPhotoUrl(id)
            _uiState.update { it.copy(photoUrls = it.photoUrls + (id to url)) }
        }
    }

    private suspend fun currentUserName(): String =
        clientRepo.fetchClient(currentUserId)?.name?.takeIf { it.isNotBlank() }
            ?: coachRepo.fetchCoach(currentUserId)?.name?.takeIf { it.isNotBlank() }
            ?: "Contact"

    fun toggleContact(place: PlaceToPlay) {
        viewModelScope.launch {
            if (place.contactUserId == currentUserId) {
                placeRepo.removePlaceContact(place.id)
            } else if (place.contactUserId.isNullOrEmpty()) {
                placeRepo.assignPlaceContact(place.id, currentUserId, currentUserName())
            }
            refresh()
        }
    }

    fun approveMember(placeId: String, userId: String) {
        viewModelScope.launch {
            val result = placeRepo.approveMembership(placeId, userId)
            if (result.isSuccess) {
                val placeName = _uiState.value.places.firstOrNull { it.id == placeId }?.name ?: "the club"
                notificationRepository.sendNotification(
                    recipientId = userId,
                    title = "Welcome to $placeName!",
                    body = "Your request to join $placeName has been approved.",
                    type = "club_approved",
                    extraData = mapOf("placeId" to placeId)
                )
            }
            refresh()
        }
    }

    fun rejectMember(placeId: String, userId: String) {
        viewModelScope.launch {
            placeRepo.rejectMembership(placeId, userId)
            refresh()
        }
    }

    fun removeMember(placeId: String, userId: String) {
        viewModelScope.launch {
            placeRepo.removeMember(placeId, userId)
            refresh()
        }
    }

    fun sendAnnouncement(placeId: String, title: String, body: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val result = placeRepo.createAnnouncement(
                placeId = placeId,
                title = title,
                body = body,
                createdBy = currentUserId,
                senderName = currentUserName()
            )
            _uiState.update {
                it.copy(
                    isSending = false,
                    message = if (result.isSuccess) "Announcement sent" else null,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    // ── Event registration management ────────────────────────────────────────

    fun updateEventCapacity(eventId: String, newMax: Int) {
        viewModelScope.launch {
            placeRepo.updateEventCapacity(eventId, newMax)
            refresh()
        }
    }

    fun toggleSignupPaid(eventId: String, signupId: String, paid: Boolean) {
        viewModelScope.launch {
            placeRepo.toggleSignupPaid(eventId, signupId, paid)
            refresh()
        }
    }

    fun removeSignup(eventId: String, signupId: String) {
        viewModelScope.launch {
            placeRepo.removeSignup(eventId, signupId)
            refresh()
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}
