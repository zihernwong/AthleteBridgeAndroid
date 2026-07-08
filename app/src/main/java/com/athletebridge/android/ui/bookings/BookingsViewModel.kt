package com.athletebridge.android.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.NotificationRepository
import com.google.firebase.firestore.DocumentSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingsUiState(
    val bookings: List<BookingItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val currentUserId: String = "",
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false
)

@HiltViewModel
class BookingsViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingsUiState())
    val uiState: StateFlow<BookingsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastIsCoach: Boolean = false
    private var paginationLastDoc: DocumentSnapshot? = null

    fun loadBookings(isCoach: Boolean) {
        lastIsCoach = isCoach
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // Wait for Firebase Auth to restore the session if not yet available
            val uid = authRepository.currentUser?.uid
                ?: authRepository.observeAuthState().filterNotNull().map { it.uid }.first()
            _uiState.value = _uiState.value.copy(currentUserId = uid)
            val flow = if (isCoach) {
                bookingRepository.observeCoachBookings(uid)
            } else {
                bookingRepository.observeClientBookings(uid)
            }
            flow.collect { bookings ->
                _uiState.value = BookingsUiState(
                    bookings = bookings.sortedByDescending { it.startAt },
                    isLoading = false,
                    isRefreshing = false,
                    currentUserId = uid
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            paginationLastDoc = null
            loadBookings(lastIsCoach)
        }
    }

    fun confirmPaymentReceived(booking: BookingItem) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            bookingRepository.confirmPaymentReceived(
                bookingId = booking.id,
                coachId = uid,
                clientId = booking.clientID
            ).onSuccess {
                for (clientId in booking.allClientIDs) {
                    notificationRepository.sendNotification(
                        recipientId = clientId,
                        title = "Booking Confirmed",
                        body = "Your coach has confirmed your payment. Your booking is now confirmed!",
                        bookingId = booking.id,
                        senderId = uid,
                        type = "booking_confirmed"
                    )
                }
            }
        }
    }

    fun loadMoreBookings() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        val uid = _uiState.value.currentUserId.ifEmpty { return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val userType = if (lastIsCoach) "COACH" else "CLIENT"
            val (newBookings, newLastDoc) = bookingRepository.fetchBookingsPage(
                userId = uid,
                userType = userType,
                lastDoc = paginationLastDoc
            )
            paginationLastDoc = newLastDoc
            val combined = (_uiState.value.bookings + newBookings)
                .distinctBy { it.id }
                .sortedByDescending { it.startAt }
            _uiState.value = _uiState.value.copy(
                bookings = combined,
                isLoadingMore = false,
                hasMore = newLastDoc != null
            )
        }
    }
}
