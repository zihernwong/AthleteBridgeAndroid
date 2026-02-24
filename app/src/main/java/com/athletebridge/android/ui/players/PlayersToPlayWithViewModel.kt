package com.athletebridge.android.ui.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.PlayerToPlayWith
import com.athletebridge.android.data.repository.PlayerRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayersToPlayWithUiState(
    val isLoading: Boolean = false,
    val players: List<PlayerToPlayWith> = emptyList(),
    val myListing: PlayerToPlayWith? = null,
    val filterSkillLevel: String = "",
    val filterAvailability: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val filteredPlayers: List<PlayerToPlayWith>
        get() = players.filter { player ->
            val skillMatch = filterSkillLevel.isBlank() || player.skillLevel == filterSkillLevel
            val availMatch = filterAvailability.isBlank() || player.availability.contains(filterAvailability)
            skillMatch && availMatch
        }
}

@HiltViewModel
class PlayersToPlayWithViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayersToPlayWithUiState())
    val uiState: StateFlow<PlayersToPlayWithUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val uid = currentUserId
            val players = playerRepo.fetchPlayers().filter { it.userId != uid }
            val myListing = playerRepo.fetchPlayerByUserId(uid)
            _uiState.update { it.copy(isLoading = false, players = players, myListing = myListing) }
        }
    }

    fun setFilterSkillLevel(value: String) = _uiState.update { it.copy(filterSkillLevel = if (it.filterSkillLevel == value) "" else value) }
    fun setFilterAvailability(value: String) = _uiState.update { it.copy(filterAvailability = if (it.filterAvailability == value) "" else value) }

    fun saveListing(name: String, photoURL: String?, skillLevel: String, city: String, availability: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val uid = currentUserId
            val player = PlayerToPlayWith(
                id = _uiState.value.myListing?.id ?: "",
                userId = uid,
                name = name,
                photoURLString = photoURL,
                skillLevel = skillLevel,
                city = city,
                availability = availability
            )
            val result = playerRepo.createOrUpdatePlayer(player)
            val myListing = playerRepo.fetchPlayerByUserId(uid)
            _uiState.update { it.copy(isSaving = false, myListing = myListing, error = result.exceptionOrNull()?.message) }
        }
    }

    fun deleteListing() {
        val id = _uiState.value.myListing?.id ?: return
        viewModelScope.launch {
            playerRepo.deletePlayer(id)
            _uiState.update { it.copy(myListing = null) }
        }
    }
}
