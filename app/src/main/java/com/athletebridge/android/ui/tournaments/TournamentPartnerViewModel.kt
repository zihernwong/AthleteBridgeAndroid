package com.athletebridge.android.ui.tournaments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.Tournament
import com.athletebridge.android.data.model.UserSummary
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.TournamentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class TournamentPartnerUiState(
    val tournaments: List<Tournament> = emptyList(),
    val userSummaries: Map<String, UserSummary> = emptyMap(),
    val selectedTournament: Tournament? = null,
    val searchText: String = "",
    val selectedGender: String = "Male",
    val selectedEvents: Set<String> = emptySet(),
    val selectedSkillLevels: Set<String> = emptySet(),
    val isParticipant: Boolean = false,
    val matchingPartners: List<UserSummary> = emptyList(),
    val filteredTournaments: List<Tournament> = emptyList(),
    val isSaving: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class TournamentPartnerViewModel @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val clientRepository: ClientRepository,
    private val coachRepository: CoachRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TournamentPartnerUiState())
    val uiState: StateFlow<TournamentPartnerUiState> = _uiState.asStateFlow()

    private val currentUid: String? get() = authRepository.currentUser?.uid

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val tournaments = tournamentRepository.fetchTournaments()
            val clients = clientRepository.fetchAllClients()
            val coaches = coachRepository.fetchAllCoaches()

            val summaries = mutableMapOf<String, UserSummary>()
            clients.forEach { c ->
                summaries[c.id] = UserSummary(id = c.id, name = c.name, tournamentSoftwareLink = c.tournamentSoftwareLink)
            }
            coaches.forEach { c ->
                summaries[c.id] = UserSummary(id = c.id, name = c.name, photoURL = c.photoURLString, tournamentSoftwareLink = c.tournamentSoftwareLink)
            }

            _uiState.value = _uiState.value.copy(
                tournaments = tournaments,
                userSummaries = summaries,
                filteredTournaments = tournaments,
                isLoading = false
            )
        }
    }

    fun updateSearch(text: String) {
        _uiState.value = _uiState.value.copy(
            searchText = text,
            filteredTournaments = if (text.isBlank()) _uiState.value.tournaments
            else _uiState.value.tournaments.filter { it.name.contains(text, ignoreCase = true) }
        )
    }

    fun selectTournament(tournament: Tournament) {
        val uid = currentUid ?: return
        val participant = tournament.participants[uid]
        val isParticipant = participant != null

        _uiState.value = _uiState.value.copy(
            selectedTournament = tournament,
            isParticipant = isParticipant,
            selectedGender = participant?.gender ?: "Male",
            selectedEvents = participant?.events?.toSet() ?: emptySet(),
            selectedSkillLevels = participant?.skillLevels?.toSet() ?: emptySet()
        )
        updateMatchingPartners()
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedTournament = null,
            matchingPartners = emptyList()
        )
    }

    fun setGender(gender: String) {
        _uiState.value = _uiState.value.copy(selectedGender = gender)
        updateMatchingPartners()
    }

    fun setEvents(events: Set<String>) {
        _uiState.value = _uiState.value.copy(selectedEvents = events)
        updateMatchingPartners()
    }

    fun setSkillLevels(skillLevels: Set<String>) {
        _uiState.value = _uiState.value.copy(selectedSkillLevels = skillLevels)
        updateMatchingPartners()
    }

    fun joinTournament() {
        val uid = currentUid ?: return
        val tournament = _uiState.value.selectedTournament ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            tournamentRepository.joinTournament(
                tournamentId = tournament.id,
                userId = uid,
                gender = _uiState.value.selectedGender,
                events = _uiState.value.selectedEvents.toList(),
                skillLevels = _uiState.value.selectedSkillLevels.toList(),
                joinerName = _uiState.value.userSummaries[uid]?.name ?: ""
            )
            // Refresh tournament
            val updated = tournamentRepository.fetchTournament(tournament.id)
            if (updated != null) {
                _uiState.value = _uiState.value.copy(
                    selectedTournament = updated,
                    isParticipant = true,
                    isSaving = false,
                    tournaments = _uiState.value.tournaments.map { if (it.id == updated.id) updated else it }
                )
                updateMatchingPartners()
            } else {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun updatePreferences() {
        joinTournament() // Same operation as join
    }

    fun leaveTournament() {
        val uid = currentUid ?: return
        val tournament = _uiState.value.selectedTournament ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            tournamentRepository.leaveTournament(tournament.id, uid)
            val updated = tournamentRepository.fetchTournament(tournament.id)
            if (updated != null) {
                _uiState.value = _uiState.value.copy(
                    selectedTournament = updated,
                    isParticipant = false,
                    isSaving = false,
                    tournaments = _uiState.value.tournaments.map { if (it.id == updated.id) updated else it }
                )
                updateMatchingPartners()
            } else {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun createTournament(name: String, startDate: Date, endDate: Date, location: String, signupLink: String?) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            tournamentRepository.createTournament(name, startDate, endDate, location, uid, signupLink)
            // Refresh tournaments list
            val tournaments = tournamentRepository.fetchTournaments()
            _uiState.value = _uiState.value.copy(
                tournaments = tournaments,
                filteredTournaments = tournaments,
                isSaving = false
            )
        }
    }

    private fun updateMatchingPartners() {
        val uid = currentUid ?: return
        val tournament = _uiState.value.selectedTournament ?: return
        val myGender = _uiState.value.selectedGender
        val myEvents = _uiState.value.selectedEvents
        val mySkillLevels = _uiState.value.selectedSkillLevels

        val partners = tournament.participants
            .filter { (participantId, _) -> participantId != uid }
            .filter { (_, info) ->
                com.athletebridge.android.data.repository.TournamentRepository
                    .partnersCompatible(myGender, myEvents, mySkillLevels, info)
            }
            .keys
            .mapNotNull { _uiState.value.userSummaries[it] }

        _uiState.value = _uiState.value.copy(matchingPartners = partners)
    }
}
