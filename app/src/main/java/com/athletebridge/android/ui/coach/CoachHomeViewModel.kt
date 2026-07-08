package com.athletebridge.android.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.AppRefreshBus
import com.athletebridge.android.data.model.StringerOrder
import com.athletebridge.android.data.repository.StringerRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachHomeUiState(
    val pendingStringerOrders: Int = 0
)

@HiltViewModel
class CoachHomeViewModel @Inject constructor(
    private val stringerRepo: StringerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachHomeUiState())
    val uiState: StateFlow<CoachHomeUiState> = _uiState

    init {
        viewModelScope.launch {
            AppRefreshBus.events.collect { event ->
                if (event == AppRefreshBus.Event.STRINGER_ORDERS || event == AppRefreshBus.Event.ALL) {
                    loadData()
                }
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val profile = stringerRepo.fetchStringerByUserId(uid)
            val pending = profile?.let {
                stringerRepo.fetchOrdersForStringer(it.id).count { o -> o.isPending }
            } ?: 0
            _uiState.update { it.copy(pendingStringerOrders = pending) }
        }
    }
}
