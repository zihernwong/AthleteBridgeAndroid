package com.athletebridge.android.ui.stringing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.BadmintonStringer
import com.athletebridge.android.data.model.StringerOrder
import com.athletebridge.android.data.repository.StringerRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StringingTabUiState(
    val isLoading: Boolean = false,
    val myStringerProfile: BadmintonStringer? = null,
    val stringers: List<BadmintonStringer> = emptyList(),
    val myOrders: List<StringerOrder> = emptyList(),
    val incomingOrders: List<StringerOrder> = emptyList(),
    val selectedTabIndex: Int = 0,
    val error: String? = null
)

@HiltViewModel
class StringingTabViewModel @Inject constructor(
    private val stringerRepo: StringerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(StringingTabUiState())
    val uiState: StateFlow<StringingTabUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val uid = currentUserId
            val profile = stringerRepo.fetchStringerByUserId(uid)
            val stringers = stringerRepo.fetchStringers()
            val myOrders = stringerRepo.fetchOrdersForBuyer(uid)
            val incoming = profile?.let { stringerRepo.fetchOrdersForStringer(it.id) } ?: emptyList()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    myStringerProfile = profile,
                    stringers = stringers,
                    myOrders = myOrders.sortedByDescending { o -> o.createdAt },
                    incomingOrders = incoming.sortedByDescending { o -> o.createdAt }
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
    }
}
