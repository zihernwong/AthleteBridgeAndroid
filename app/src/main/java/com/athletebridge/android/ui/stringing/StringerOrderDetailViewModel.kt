package com.athletebridge.android.ui.stringing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.StringerOrder
import com.athletebridge.android.data.repository.StringerRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StringerOrderDetailUiState(
    val isLoading: Boolean = false,
    val order: StringerOrder? = null,
    val isUpdating: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StringerOrderDetailViewModel @Inject constructor(
    private val stringerRepo: StringerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(StringerOrderDetailUiState())
    val uiState: StateFlow<StringerOrderDetailUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun loadOrder(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val order = stringerRepo.fetchOrder(orderId)
            _uiState.update { it.copy(isLoading = false, order = order) }
        }
    }

    fun updateStatus(status: String) {
        val orderId = _uiState.value.order?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val result = stringerRepo.updateOrderStatus(orderId, status)
            val updated = stringerRepo.fetchOrder(orderId)
            _uiState.update {
                it.copy(isUpdating = false, order = updated ?: it.order, error = result.exceptionOrNull()?.message)
            }
        }
    }
}
