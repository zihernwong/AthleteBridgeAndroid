package com.athletebridge.android.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingDetailUiState(
    val booking: BookingItem? = null,
    val isLoading: Boolean = true,
    val isCoach: Boolean = false,
    val isSavingNotes: Boolean = false,
    val isConfirmingPayment: Boolean = false,
    val selectedCoachPayments: Map<String, String>? = null,
    val selectedCoachName: String = "Coach",
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository,
    private val coachRepository: CoachRepository,
    private val clientRepository: ClientRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingDetailUiState())
    val uiState: StateFlow<BookingDetailUiState> = _uiState.asStateFlow()

    val currentUid: String get() = authRepository.currentUser?.uid ?: ""

    fun load(bookingId: String, isCoach: Boolean) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isCoach = isCoach)
            val booking = bookingRepository.fetchBookingAny(
                bookingId = bookingId,
                userId = uid,
                userType = if (isCoach) "COACH" else "CLIENT"
            )
            _uiState.value = _uiState.value.copy(
                booking = booking,
                isLoading = false,
                error = if (booking == null) "Booking not found" else null
            )
        }
    }

    fun saveSessionNotes(notes: String) {
        val booking = _uiState.value.booking ?: return
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingNotes = true)
            val result = bookingRepository.saveSessionNotes(
                bookingId = booking.id,
                coachId = if (booking.coachID.isNotEmpty()) booking.coachID else uid,
                clientId = booking.clientID,
                notes = notes
            )
            _uiState.value = _uiState.value.copy(
                isSavingNotes = false,
                booking = if (result.isSuccess) booking.copy(coachNote = notes) else booking,
                message = if (result.isSuccess) "Session notes saved" else null,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun saveSessionRecap(recap: String) {
        val booking = _uiState.value.booking ?: return
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingNotes = true)
            val result = bookingRepository.saveSessionRecap(
                bookingId = booking.id,
                coachId = if (booking.coachID.isNotEmpty()) booking.coachID else uid,
                clientId = booking.clientID,
                recap = recap
            )
            _uiState.value = _uiState.value.copy(
                isSavingNotes = false,
                booking = if (result.isSuccess) booking.copy(sessionRecap = recap) else booking,
                message = if (result.isSuccess) "Session recap shared with client" else null,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    /** Coach confirms the client's submitted payment: booking becomes confirmed + paid. */
    fun confirmPaymentReceived() {
        val booking = _uiState.value.booking ?: return
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConfirmingPayment = true)
            val result = bookingRepository.confirmPaymentReceived(
                bookingId = booking.id,
                coachId = if (booking.coachID.isNotEmpty()) booking.coachID else uid,
                clientId = booking.clientID
            )
            if (result.isSuccess) {
                val coachName = coachRepository.fetchCoach(uid)?.name ?: "Your coach"
                if (booking.clientID.isNotEmpty()) {
                    notificationRepository.sendNotification(
                        recipientId = booking.clientID,
                        title = "Booking Confirmed",
                        body = "$coachName has confirmed your payment. Your booking is now confirmed!",
                        bookingId = booking.id,
                        senderId = uid,
                        type = "booking_confirmed"
                    )
                }
            }
            _uiState.value = _uiState.value.copy(
                isConfirmingPayment = false,
                booking = if (result.isSuccess) booking.copy(status = "confirmed", paymentStatus = "paid") else booking,
                message = if (result.isSuccess) "Payment confirmed. Booking is now confirmed." else null,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    /** Client notifies a coach that payment was made outside the app. */
    fun notifyCoachOfPayment(coachId: String) {
        val booking = _uiState.value.booking ?: return
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            val clientName = clientRepository.fetchClient(uid)?.name?.takeIf { it.isNotBlank() } ?: "A client"
            notificationRepository.sendNotification(
                recipientId = coachId,
                title = "Payment Notification",
                body = "$clientName has marked booking as paid. Please confirm",
                bookingId = booking.id,
                senderId = uid,
                type = "payment_submitted"
            )
            _uiState.value = _uiState.value.copy(message = "Coach has been notified of your payment.")
        }
    }

    /** Load the coach's payment methods (Venmo/Zelle/etc.) so the client can pay. */
    fun loadCoachPayments() {
        val booking = _uiState.value.booking ?: return
        val coachId = booking.coachID.takeIf { it.isNotEmpty() } ?: booking.allCoachIDs.firstOrNull() ?: return
        viewModelScope.launch {
            val coach = coachRepository.fetchCoach(coachId)
            _uiState.value = _uiState.value.copy(
                selectedCoachPayments = coach?.payments ?: emptyMap(),
                selectedCoachName = coach?.name?.takeIf { it.isNotBlank() } ?: booking.coachName ?: "Coach"
            )
        }
    }

    fun dismissCoachPayments() {
        _uiState.value = _uiState.value.copy(selectedCoachPayments = null)
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}
