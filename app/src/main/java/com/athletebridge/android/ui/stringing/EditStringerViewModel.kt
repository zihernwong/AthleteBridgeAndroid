package com.athletebridge.android.ui.stringing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.BadmintonStringer
import com.athletebridge.android.data.repository.StringerRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditStringerUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val existingProfile: BadmintonStringer? = null,
    val strings: Map<String, Double> = emptyMap(),
    val laborCost: String = "",
    val meetupLocation: String = "",
    val newStringName: String = "",
    val newStringPrice: String = "",
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EditStringerViewModel @Inject constructor(
    private val stringerRepo: StringerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditStringerUiState())
    val uiState: StateFlow<EditStringerUiState> = _uiState

    fun loadExisting() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val uid = auth.currentUser?.uid ?: ""
            val profile = stringerRepo.fetchStringerByUserId(uid)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    existingProfile = profile,
                    strings = profile?.strings ?: emptyMap(),
                    laborCost = profile?.laborCost?.toString() ?: "",
                    meetupLocation = profile?.meetupLocation ?: ""
                )
            }
        }
    }

    fun setLaborCost(value: String) = _uiState.update { it.copy(laborCost = value) }
    fun setMeetupLocation(value: String) = _uiState.update { it.copy(meetupLocation = value) }
    fun setNewStringName(value: String) = _uiState.update { it.copy(newStringName = value) }
    fun setNewStringPrice(value: String) = _uiState.update { it.copy(newStringPrice = value) }

    fun addString() {
        val name = _uiState.value.newStringName.trim()
        val price = _uiState.value.newStringPrice.toDoubleOrNull() ?: return
        if (name.isBlank()) return
        _uiState.update {
            it.copy(
                strings = it.strings + (name to price),
                newStringName = "",
                newStringPrice = ""
            )
        }
    }

    fun removeString(name: String) {
        _uiState.update { it.copy(strings = it.strings - name) }
    }

    fun save(userName: String, photoURL: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val uid = auth.currentUser?.uid ?: ""
            val stringer = BadmintonStringer(
                id = _uiState.value.existingProfile?.id ?: "",
                userId = uid,
                name = userName,
                photoURLString = photoURL,
                strings = _uiState.value.strings,
                laborCost = _uiState.value.laborCost.toDoubleOrNull() ?: 0.0,
                meetupLocation = _uiState.value.meetupLocation
            )
            val result = stringerRepo.createOrUpdateStringer(stringer)
            _uiState.update {
                it.copy(isSaving = false, saveSuccess = result.isSuccess, error = result.exceptionOrNull()?.message)
            }
        }
    }
}
