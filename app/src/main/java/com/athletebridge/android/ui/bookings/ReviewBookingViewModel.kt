package com.athletebridge.android.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewBookingUiState(
    val booking: BookingItem? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val actionComplete: Boolean = false
)

@HiltViewModel
class ReviewBookingViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewBookingUiState())
    val uiState: StateFlow<ReviewBookingUiState> = _uiState.asStateFlow()

    fun loadBooking(bookingId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val booking = bookingRepository.fetchClientBooking(bookingId, uid)
            _uiState.value = _uiState.value.copy(booking = booking, isLoading = false)
        }
    }

    fun confirmBooking(bookingId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        val booking = _uiState.value.booking ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val isGroup = (booking.isGroupBooking == true) ||
                    (booking.allCoachIDs.size > 1) ||
                    (booking.allClientIDs.size > 1)

            val result = if (isGroup) {
                bookingRepository.confirmGroupBookingAsClient(
                    bookingId = bookingId,
                    clientId = uid,
                    allCoachIds = booking.allCoachIDs,
                    allClientIds = booking.allClientIDs
                )
            } else {
                bookingRepository.confirmBooking(
                    bookingId = bookingId,
                    coachId = booking.coachID,
                    clientId = uid
                )
            }

            result.onSuccess {
                // Notify coach(es)
                for (coachId in booking.allCoachIDs) {
                    notificationRepository.sendNotification(
                        recipientId = coachId,
                        title = "Booking Confirmed",
                        body = "${booking.clientName ?: "Client"} has confirmed the booking.",
                        bookingId = bookingId,
                        senderId = uid,
                        type = "booking_confirmed"
                    )
                }
                _uiState.value = _uiState.value.copy(isSaving = false, actionComplete = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }

    fun declineBooking(bookingId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        val booking = _uiState.value.booking ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val result = bookingRepository.declineBooking(
                bookingId = bookingId,
                coachId = booking.coachID,
                clientId = uid
            )
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, actionComplete = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }
}
