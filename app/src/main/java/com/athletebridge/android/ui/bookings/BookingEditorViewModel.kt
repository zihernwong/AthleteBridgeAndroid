package com.athletebridge.android.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.AwayTime
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.AwayTimeRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class BookingEditorUiState(
    val coach: Coach? = null,
    val clientName: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val bookingCreated: Boolean = false,
    val selectedCalendarDate: Long = run {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    },
    val bookingsForDay: List<BookingItem> = emptyList(),
    val awayTimesForDay: List<AwayTime> = emptyList(),
    val isLoadingCalendar: Boolean = false
)

@HiltViewModel
class BookingEditorViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val awayTimeRepository: AwayTimeRepository,
    private val coachRepository: CoachRepository,
    private val clientRepository: ClientRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingEditorUiState())
    val uiState: StateFlow<BookingEditorUiState> = _uiState.asStateFlow()

    fun loadCoach(coachId: String) {
        viewModelScope.launch {
            val coach = coachRepository.fetchCoach(coachId)
            val uid = authRepository.currentUser?.uid
            val clientName = uid?.let { clientRepository.fetchClient(it)?.name }
                ?.takeIf { it.isNotBlank() }
                ?: authRepository.currentUser?.displayName
                ?: authRepository.currentUser?.email?.substringBefore("@")
                ?: ""
            _uiState.value = _uiState.value.copy(coach = coach, clientName = clientName)
        }
        // Load availability for today
        selectCalendarDate(coachId, _uiState.value.selectedCalendarDate)
    }

    /** Change the selected calendar date and reload the day's bookings. */
    fun selectCalendarDate(coachId: String, dateMillis: Long) {
        if (coachId.isEmpty()) return
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val dayEnd = Calendar.getInstance().apply {
            timeInMillis = dayStart.time
            add(Calendar.DAY_OF_MONTH, 1)
        }.time
        _uiState.value = _uiState.value.copy(
            selectedCalendarDate = dayStart.time,
            isLoadingCalendar = true
        )
        viewModelScope.launch {
            val bookings = bookingRepository.fetchBookingsForCoach(coachId, dayStart, dayEnd)
            val awayTimes = awayTimeRepository.fetchAwayTimes(coachId, dayStart, dayEnd)
            _uiState.value = _uiState.value.copy(
                bookingsForDay = bookings,
                awayTimesForDay = awayTimes,
                isLoadingCalendar = false
            )
        }
    }

    fun createBooking(
        coachId: String,
        startAt: Date,
        endAt: Date,
        location: String?,
        notes: String?,
        repeatWeeks: Int = 1
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

        val weeks = repeatWeeks.coerceIn(1, 12)
        val recurrenceGroupId = if (weeks > 1) java.util.UUID.randomUUID().toString() else null

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            var firstBookingId: String? = null
            var failure: Throwable? = null
            for (week in 0 until weeks) {
                val offsetMs = week * 7L * 24 * 3_600_000
                val result = bookingRepository.createBooking(
                    clientId = uid,
                    clientName = clientName,
                    coachId = coachId,
                    coachName = coach.name,
                    startAt = Date(startAt.time + offsetMs),
                    endAt = Date(endAt.time + offsetMs),
                    location = location,
                    notes = notes,
                    recurrenceGroupId = recurrenceGroupId
                )
                result.onSuccess { if (firstBookingId == null) firstBookingId = it }
                    .onFailure { if (failure == null) failure = it }
            }
            val bookingId = firstBookingId
            if (bookingId != null) {
                notificationRepository.sendNotification(
                    recipientId = coachId,
                    title = "New Booking Request",
                    body = if (weeks > 1)
                        "$clientName has requested a weekly booking with you ($weeks weeks)"
                    else
                        "$clientName has requested a booking with you",
                    bookingId = bookingId,
                    senderId = uid,
                    type = "booking_request"
                )
                _uiState.value = _uiState.value.copy(isSaving = false, bookingCreated = true)
            } else {
                _uiState.value = _uiState.value.copy(isSaving = false, error = failure?.message)
            }
        }
    }
}
