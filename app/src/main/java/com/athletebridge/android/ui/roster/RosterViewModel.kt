package com.athletebridge.android.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.ChatRepository
import com.athletebridge.android.data.repository.UserDirectoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/** Unique person aggregated from booking history (a coach for clients, a client for coaches). */
data class RosterEntry(
    val id: String,
    val name: String,
    val totalSessions: Int,
    val totalPaid: Double,          // confirmed sessions only; shown on the coach roster
    val lastSessionDate: Date?
)

private class ClientAcc(
    var name: String,
    var sessions: Int,
    var paid: Double,
    var lastDate: Date?
)

data class RosterUiState(
    val entries: List<RosterEntry> = emptyList(),
    val bookings: List<BookingItem> = emptyList(),
    val photoUrls: Map<String, String?> = emptyMap(),
    val lastSeen: Map<String, Date> = emptyMap(),
    val isLoading: Boolean = true,
    val isCreatingChat: Boolean = false,
    val chatToOpen: String? = null
)

@HiltViewModel
class RosterViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val userDirectory: UserDirectoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RosterUiState())
    val uiState: StateFlow<RosterUiState> = _uiState.asStateFlow()

    /** Client's "My Coaches": aggregate unique coaches from the client's bookings. */
    fun loadCoachRoster() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            bookingRepository.observeClientBookings(uid).collect { bookings ->
                val map = mutableMapOf<String, Triple<String, Int, Date?>>()
                for (booking in bookings) {
                    val coachId = booking.coachID
                    if (coachId.isEmpty() || coachId == uid) continue
                    val coachName = booking.coachName ?: coachId
                    val prev = map[coachId]
                    val sessions = (prev?.second ?: 0) + 1
                    val lastDate = maxOfDates(prev?.third, booking.startAt)
                    val name = prev?.first?.takeIf { it.isNotEmpty() && it != coachId } ?: coachName
                    map[coachId] = Triple(name, sessions, lastDate)
                }
                val entries = map.map { (id, v) ->
                    RosterEntry(id = id, name = v.first, totalSessions = v.second, totalPaid = 0.0, lastSessionDate = v.third)
                }.sortedByDescending { it.lastSessionDate ?: Date(0) }
                _uiState.value = _uiState.value.copy(entries = entries, bookings = bookings, isLoading = false)
                loadPhotos(entries.map { it.id })
            }
        }
    }

    /** Coach's "My Clients": aggregate unique clients (incl. group participants) with totals + presence. */
    fun loadClientRoster() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            bookingRepository.observeCoachBookings(uid).collect { bookings ->
                val map = mutableMapOf<String, ClientAcc>()
                for (booking in bookings) {
                    val participants = mutableListOf<Pair<String, String>>()
                    if (booking.clientID.isNotEmpty()) {
                        participants.add(booking.clientID to (booking.clientName ?: booking.clientID))
                    }
                    val ids = booking.clientIDs
                    val names = booking.clientNames
                    if (ids != null && names != null) {
                        ids.zip(names).forEach { (id, name) ->
                            if (id.isNotEmpty() && id != booking.clientID) participants.add(id to name)
                        }
                    }
                    val isConfirmed = (booking.status ?: "").lowercase() == "confirmed"
                    val price = booking.rateUSD ?: 0.0
                    for ((clientId, clientName) in participants) {
                        if (clientId == uid) continue
                        val acc = map.getOrPut(clientId) { ClientAcc(clientName, 0, 0.0, null) }
                        acc.sessions += 1
                        if (isConfirmed) acc.paid += price
                        acc.lastDate = maxOfDates(acc.lastDate, booking.startAt)
                        if (acc.name.isEmpty() || acc.name == clientId) acc.name = clientName
                    }
                }
                val entries = map.map { (id, acc) ->
                    RosterEntry(id = id, name = acc.name, totalSessions = acc.sessions, totalPaid = acc.paid, lastSessionDate = acc.lastDate)
                }.sortedByDescending { it.lastSessionDate ?: Date(0) }
                _uiState.value = _uiState.value.copy(entries = entries, bookings = bookings, isLoading = false)
                loadPhotos(entries.map { it.id })
                loadPresence(entries.map { it.id })
            }
        }
    }

    fun messageUser(otherUid: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingChat = true)
            val chatId = chatRepository.createOrGetChat(uid, otherUid)
            _uiState.value = _uiState.value.copy(
                isCreatingChat = false,
                chatToOpen = chatId.takeIf { it.isNotEmpty() }
            )
        }
    }

    fun consumeChatNavigation() {
        _uiState.value = _uiState.value.copy(chatToOpen = null)
    }

    private fun loadPhotos(ids: List<String>) {
        viewModelScope.launch {
            for (id in ids) {
                if (_uiState.value.photoUrls.containsKey(id)) continue
                val url = userDirectory.fetchUserPhotoUrl(id)
                _uiState.value = _uiState.value.copy(photoUrls = _uiState.value.photoUrls + (id to url))
            }
        }
    }

    private fun loadPresence(ids: List<String>) {
        viewModelScope.launch {
            val seen = userDirectory.fetchLastSeen(ids)
            _uiState.value = _uiState.value.copy(lastSeen = seen)
        }
    }

    private fun maxOfDates(a: Date?, b: Date?): Date? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }
}
