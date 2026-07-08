package com.athletebridge.android.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.data.repository.PlaceRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlacesToPlayUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val places: List<PlaceToPlay> = emptyList(),
    val searchQuery: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val filteredPlaces: List<PlaceToPlay>
        get() = if (searchQuery.isBlank()) places
        else places.filter { it.name.contains(searchQuery, ignoreCase = true) || it.address.contains(searchQuery, ignoreCase = true) }
}

@HiltViewModel
class PlacesToPlayViewModel @Inject constructor(
    private val placeRepo: PlaceRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlacesToPlayUiState())
    val uiState: StateFlow<PlacesToPlayUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun loadPlaces() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val places = placeRepo.fetchPlaces()
            _uiState.update { it.copy(isLoading = false, isRefreshing = false, places = places) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val places = placeRepo.fetchPlaces()
            _uiState.update { it.copy(isRefreshing = false, places = places) }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun createPlace(
        name: String,
        address: String,
        weeklySchedule: Map<String, String>,
        pricing: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = placeRepo.createPlace(name, address, weeklySchedule, pricing, currentUserId)
            if (result.isSuccess) {
                val places = placeRepo.fetchPlaces()
                _uiState.update { it.copy(isSaving = false, places = places) }
            } else {
                _uiState.update { it.copy(isSaving = false, error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            placeRepo.deletePlace(placeId)
            val places = placeRepo.fetchPlaces()
            _uiState.update { it.copy(places = places) }
        }
    }
}
