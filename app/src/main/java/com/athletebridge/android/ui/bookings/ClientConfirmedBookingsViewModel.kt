package com.athletebridge.android.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.BookingItem
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

data class ClientConfirmedBookingsUiState(
    val bookings: List<BookingItem> = emptyList(),
    val coachCancellationWindows: Map<String, Int> = emptyMap(), // coachId -> hours
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ClientConfirmedBookingsViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository,
    private val coachRepository: CoachRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientConfirmedBookingsUiState())
    val uiState: StateFlow<ClientConfirmedBookingsUiState> = _uiState.asStateFlow()

    fun loadBookings() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            bookingRepository.observeClientBookings(uid).collect { allBookings ->
                val confirmed = allBookings.filter {
                    val s = (it.status ?: "").lowercase()
                    s == "confirmed" || s == "fully_confirmed"
                }.sortedByDescending { it.startAt }
                _uiState.value = _uiState.value.copy(bookings = confirmed, isLoading = false)
                loadCancellationWindows(confirmed)
            }
        }
    }

    private fun loadCancellationWindows(bookings: List<BookingItem>) {
        viewModelScope.launch {
            val coachIds = bookings.flatMap { it.allCoachIDs }.distinct()
                .filter { it.isNotEmpty() && !_uiState.value.coachCancellationWindows.containsKey(it) }
            for (coachId in coachIds) {
                val hours = coachRepository.fetchCoach(coachId)?.cancellationWindowHours ?: 0
                _uiState.value = _uiState.value.copy(
                    coachCancellationWindows = _uiState.value.coachCancellationWindows + (coachId to hours)
                )
            }
        }
    }

    /** True when cancelling [booking] now falls inside the coach's cancellation window. */
    fun isInsideCancellationWindow(booking: BookingItem): Boolean {
        val hours = _uiState.value.coachCancellationWindows[booking.coachID] ?: 0
        val start = booking.startAt ?: return false
        if (hours <= 0) return false
        return start.time - System.currentTimeMillis() < hours * 3_600_000L
    }

    fun cancelBooking(booking: BookingItem) {
        val uid = authRepository.currentUser?.uid ?: return
        val forfeit = isInsideCancellationWindow(booking) && booking.requiresPaymentUpfront == true
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            bookingRepository.cancelBooking(
                bookingId = booking.id,
                coachId = booking.coachID,
                clientId = if (booking.clientID.isNotEmpty()) booking.clientID else uid,
                forfeitDeposit = forfeit
            )
            for (coachId in booking.allCoachIDs) {
                notificationRepository.sendNotification(
                    recipientId = coachId,
                    title = "Booking Cancelled",
                    body = "${booking.clientName ?: "Your client"} has cancelled the booking.",
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
                coachId = booking.coachID,
                clientId = if (booking.clientID.isNotEmpty()) booking.clientID else uid,
                newStart = newStart,
                newEnd = newEnd
            )
            for (coachId in booking.allCoachIDs) {
                notificationRepository.sendNotification(
                    recipientId = coachId,
                    title = "Booking Rescheduled",
                    body = "${booking.clientName ?: "Your client"} has proposed new times for your booking. Please review and confirm.",
                    bookingId = booking.id,
                    senderId = uid,
                    type = "booking_rescheduled"
                )
            }
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }
}
