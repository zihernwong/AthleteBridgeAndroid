package com.athletebridge.android.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.BookingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingsUiState(
    val bookings: List<BookingItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class BookingsViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingsUiState())
    val uiState: StateFlow<BookingsUiState> = _uiState.asStateFlow()

    fun loadBookings(isCoach: Boolean) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val flow = if (isCoach) {
                bookingRepository.observeCoachBookings(uid)
            } else {
                bookingRepository.observeClientBookings(uid)
            }
            flow.collect { bookings ->
                _uiState.value = BookingsUiState(
                    bookings = bookings.sortedByDescending { it.startAt },
                    isLoading = false
                )
            }
        }
    }
}
