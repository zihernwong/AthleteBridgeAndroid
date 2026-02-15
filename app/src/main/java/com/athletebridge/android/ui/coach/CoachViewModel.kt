package com.athletebridge.android.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachListUiState(
    val coaches: List<Coach> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val coachRepository: CoachRepository,
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachListUiState())
    val uiState: StateFlow<CoachListUiState> = _uiState.asStateFlow()

    init {
        fetchCoaches()
    }

    fun fetchCoaches() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val coaches = coachRepository.fetchAllCoaches()
            _uiState.value = CoachListUiState(coaches = coaches, isLoading = false)
        }
    }

    fun filterCoaches(
        searchQuery: String,
        goals: List<String>,
        availability: List<String>
    ): List<Coach> {
        var filtered = _uiState.value.coaches

        // Filter by name search
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase()
            filtered = filtered.filter { it.name.lowercase().contains(query) }
        }

        // Filter by goals matching specialties
        if (goals.isNotEmpty()) {
            val goalSet = goals.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            if (goalSet.isNotEmpty()) {
                filtered = filtered.filter { coach ->
                    coach.specialties.any { specialty ->
                        goalSet.any { goal -> specialty.lowercase().contains(goal) }
                    }
                }
            }
        }

        // Filter by availability
        if (availability.isNotEmpty()) {
            filtered = filtered.filter { coach ->
                coach.availability.any { it in availability }
            }
        }

        return filtered
    }
}
