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
import java.util.Date
import javax.inject.Inject

data class CoachConfirmedBookingsUiState(
    val bookings: List<BookingItem> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CoachConfirmedBookingsViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachConfirmedBookingsUiState())
    val uiState: StateFlow<CoachConfirmedBookingsUiState> = _uiState.asStateFlow()

    fun loadBookings() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            bookingRepository.observeCoachBookings(uid).collect { allBookings ->
                val confirmed = allBookings.filter {
                    val s = (it.status ?: "").lowercase()
                    s == "confirmed" || s == "fully_confirmed"
                }.sortedByDescending { it.startAt }
                _uiState.value = CoachConfirmedBookingsUiState(bookings = confirmed, isLoading = false)
            }
        }
    }

    fun acknowledgePayment(booking: BookingItem) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            bookingRepository.acknowledgePayment(
                bookingId = booking.id,
                coachId = uid,
                clientId = booking.clientID
            )
            // Notify client(s)
            for (clientId in booking.allClientIDs) {
                notificationRepository.sendNotification(
                    recipientId = clientId,
                    title = "Payment Acknowledged",
                    body = "Your coach has acknowledged payment for the booking.",
                    bookingId = booking.id,
                    senderId = uid,
                    type = "payment_acknowledged"
                )
            }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun cancelBooking(booking: BookingItem) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            bookingRepository.updateBookingStatus(
                bookingId = booking.id,
                status = "cancelled",
                coachId = uid,
                clientId = booking.clientID
            )
            for (clientId in booking.allClientIDs) {
                notificationRepository.sendNotification(
                    recipientId = clientId,
                    title = "Booking Cancelled",
                    body = "Your coach has cancelled the booking.",
                    bookingId = booking.id,
                    senderId = uid,
                    type = "booking_cancelled"
                )
            }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }

    fun rescheduleBooking(booking: BookingItem, newStart: Date, newEnd: Date) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            bookingRepository.rescheduleBooking(
                bookingId = booking.id,
                coachId = uid,
                clientId = booking.clientID,
                newStart = newStart,
                newEnd = newEnd
            )
            for (clientId in booking.allClientIDs) {
                notificationRepository.sendNotification(
                    recipientId = clientId,
                    title = "Booking Rescheduled",
                    body = "Your coach has proposed new times for your booking. Please review and confirm.",
                    bookingId = booking.id,
                    senderId = uid,
                    type = "booking_rescheduled"
                )
            }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }
}
