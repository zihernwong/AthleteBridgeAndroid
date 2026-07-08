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
import javax.inject.Inject

data class AcceptBookingUiState(
    val booking: BookingItem? = null,
    val coachHourlyRate: Double? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val actionComplete: Boolean = false
)

@HiltViewModel
class AcceptBookingViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val coachRepository: CoachRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AcceptBookingUiState())
    val uiState: StateFlow<AcceptBookingUiState> = _uiState.asStateFlow()

    fun loadBooking(bookingId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val booking = bookingRepository.fetchBooking(bookingId, uid)
            val coach = coachRepository.fetchCoach(uid)
            _uiState.value = _uiState.value.copy(
                booking = booking,
                coachHourlyRate = coach?.hourlyRate,
                isLoading = false
            )
        }
    }

    fun acceptBooking(bookingId: String, rateUSD: Double, coachNote: String?) {
        val uid = authRepository.currentUser?.uid ?: return
        val booking = _uiState.value.booking ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val isGroup = (booking.isGroupBooking == true) ||
                    (booking.allCoachIDs.size > 1) ||
                    (booking.allClientIDs.size > 1)

            val coach = coachRepository.fetchCoach(uid)
            val requiresPaymentUpfront = coach?.subscriptionTier == com.athletebridge.android.data.model.CoachTier.PRO

            val result = if (isGroup) {
                bookingRepository.acceptGroupBookingAsCoach(
                    bookingId = bookingId,
                    coachId = uid,
                    rateUSD = rateUSD,
                    coachNote = coachNote,
                    allCoachIds = booking.allCoachIDs,
                    allClientIds = booking.allClientIDs
                )
            } else {
                bookingRepository.acceptBooking(
                    bookingId = bookingId,
                    coachId = uid,
                    clientId = booking.clientID,
                    rateUSD = rateUSD,
                    coachNote = coachNote,
                    requiresPaymentUpfront = requiresPaymentUpfront
                )
            }

            result.onSuccess {
                // Notify client(s)
                val notifTitle = if (requiresPaymentUpfront) "Payment Required" else "Booking Accepted"
                val notifBody = if (requiresPaymentUpfront) {
                    "Your booking has been accepted. Please review the payment details and submit payment to confirm your booking."
                } else {
                    "Your booking has been accepted by the coach. Please review and confirm."
                }
                for (clientId in booking.allClientIDs) {
                    notificationRepository.sendNotification(
                        recipientId = clientId,
                        title = notifTitle,
                        body = notifBody,
                        bookingId = bookingId,
                        senderId = uid,
                        type = "booking_accepted"
                    )
                }
                _uiState.value = _uiState.value.copy(isSaving = false, actionComplete = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }

    fun rejectBooking(bookingId: String, reason: String) {
        val uid = authRepository.currentUser?.uid ?: return
        val booking = _uiState.value.booking ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val result = bookingRepository.rejectBooking(
                bookingId = bookingId,
                coachId = uid,
                clientId = booking.clientID,
                reason = reason
            )
            result.onSuccess {
                notificationRepository.sendNotification(
                    recipientId = booking.clientID,
                    title = "Booking Rejected",
                    body = "Your booking request has been rejected. Reason: $reason",
                    bookingId = bookingId,
                    senderId = uid,
                    type = "booking_rejected"
                )
                _uiState.value = _uiState.value.copy(isSaving = false, actionComplete = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }
}
