package com.athletebridge.android.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.AwayTime
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.AwayTimeRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.NotificationRepository
import com.athletebridge.android.data.repository.PlaceRepository
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
    /** The coach's preferred locations (linked Places to Play). Booking requires picking one. */
    val coachPlaces: List<PlaceToPlay> = emptyList(),
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
    /** Start of the week shown in the calendar strip. */
    val weekStartMillis: Long = startOfWeekMillis(System.currentTimeMillis()),
    /** Bookings/away times for the whole visible week, so day switching is instant
     *  and per-day availability chips can be computed. */
    val bookingsForWeek: List<BookingItem> = emptyList(),
    val awayTimesForWeek: List<AwayTime> = emptyList(),
    val isLoadingCalendar: Boolean = false
)

/** Start of the (locale-aware) week containing the given moment. */
fun startOfWeekMillis(dateMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    }
    // Setting DAY_OF_WEEK can land after the source date near week boundaries;
    // walk back so the week always contains the original day.
    if (cal.timeInMillis > dateMillis) cal.add(Calendar.DAY_OF_YEAR, -7)
    return cal.timeInMillis
}

@HiltViewModel
class BookingEditorViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val awayTimeRepository: AwayTimeRepository,
    private val coachRepository: CoachRepository,
    private val clientRepository: ClientRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val placeRepository: PlaceRepository
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
            // Coach's preferred locations — the client must book at one of these
            val coachPlaces = coach?.linkedPlaceIds?.takeIf { it.isNotEmpty() }?.let { ids ->
                placeRepository.fetchPlaces().filter { ids.contains(it.id) }
            } ?: emptyList()
            _uiState.value = _uiState.value.copy(coach = coach, clientName = clientName, coachPlaces = coachPlaces)
        }
        // Load availability for today
        selectCalendarDate(coachId, _uiState.value.selectedCalendarDate)
    }

    private var loadedWeekStart: Long? = null

    /** Change the selected day; the containing week is fetched only when it changes. */
    fun selectCalendarDate(coachId: String, dateMillis: Long) {
        if (coachId.isEmpty()) return
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekStart = startOfWeekMillis(dayStart)
        _uiState.value = _uiState.value.copy(
            selectedCalendarDate = dayStart,
            weekStartMillis = weekStart
        )
        if (loadedWeekStart != weekStart) loadWeek(coachId, weekStart)
    }

    private fun loadWeek(coachId: String, weekStartMs: Long) {
        loadedWeekStart = weekStartMs
        _uiState.value = _uiState.value.copy(isLoadingCalendar = true)
        val weekStartDate = Date(weekStartMs)
        val weekEndDate = Calendar.getInstance().apply {
            timeInMillis = weekStartMs
            add(Calendar.DAY_OF_YEAR, 7)
        }.time
        viewModelScope.launch {
            val bookings = bookingRepository.fetchBookingsForCoach(coachId, weekStartDate, weekEndDate)
            val awayTimes = awayTimeRepository.fetchAwayTimes(coachId, weekStartDate, weekEndDate)
            _uiState.value = _uiState.value.copy(
                bookingsForWeek = bookings,
                awayTimesForWeek = awayTimes,
                isLoadingCalendar = false
            )
        }
    }

    fun createBooking(
        coachId: String,
        startAt: Date,
        endAt: Date,
        location: String?,
        locationPlaceId: String? = null,
        notes: String?,
        repeatWeeks: Int = 1
    ) {
        val uid = authRepository.currentUser?.uid ?: return
        val coach = _uiState.value.coach ?: return
        val clientName = _uiState.value.clientName

        // Validate
        // Location is mandatory: the client must pick one of the coach's preferred locations
        if (_uiState.value.coachPlaces.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "This coach hasn't added any preferred locations yet. A location is required to book — please message the coach so they can add locations to their profile.")
            return
        }
        if (locationPlaceId.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Please select a location for the session")
            return
        }
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
                    locationPlaceId = locationPlaceId,
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
