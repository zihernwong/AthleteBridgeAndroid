package com.athletebridge.android.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class BookingEditorUiState(
    val coach: Coach? = null,
    val clientName: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val bookingCreated: Boolean = false
)

@HiltViewModel
class BookingEditorViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val coachRepository: CoachRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingEditorUiState())
    val uiState: StateFlow<BookingEditorUiState> = _uiState.asStateFlow()

    fun loadCoach(coachId: String) {
        viewModelScope.launch {
            val coach = coachRepository.fetchCoach(coachId)
            val clientName = authRepository.currentUser?.displayName ?: ""
            _uiState.value = _uiState.value.copy(coach = coach, clientName = clientName)
        }
    }

    fun createBooking(
        coachId: String,
        startAt: Date,
        endAt: Date,
        location: String?,
        notes: String?
    ) {
        val uid = authRepository.currentUser?.uid ?: return
        val coach = _uiState.value.coach ?: return
        val clientName = _uiState.value.clientName

        // Validate
        if (startAt >= endAt) {
            _uiState.value = _uiState.value.copy(error = "End time must be after start time")
            return
        }
        val durationMin = (endAt.time - startAt.time) / 60000
        if (durationMin < 60) {
            _uiState.value = _uiState.value.copy(error = "Minimum booking duration is 1 hour")
            return
        }
        if (startAt.before(Date())) {
            _uiState.value = _uiState.value.copy(error = "Start time must be in the future")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val result = bookingRepository.createBooking(
                clientId = uid,
                clientName = clientName,
                coachId = coachId,
                coachName = coach.name,
                startAt = startAt,
                endAt = endAt,
                location = location,
                notes = notes
            )
            result.onSuccess { bookingId ->
                // Send notification to coach
                notificationRepository.sendNotification(
                    recipientId = coachId,
                    title = "New Booking Request",
                    body = "$clientName has requested a booking with you",
                    bookingId = bookingId,
                    senderId = uid,
                    type = "booking_request"
                )
                _uiState.value = _uiState.value.copy(isSaving = false, bookingCreated = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }
}
