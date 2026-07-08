package com.athletebridge.android.ui.stringing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.StringerOrder
import com.athletebridge.android.data.repository.NotificationRepository
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
    private val notificationRepo: NotificationRepository,
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
        val order = _uiState.value.order ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            val result = stringerRepo.updateOrderStatus(order.id, status)
            if (result.isSuccess) {
                sendStatusNotification(order, status)
            }
            val updated = stringerRepo.fetchOrder(order.id)
            _uiState.update {
                it.copy(isUpdating = false, order = updated ?: it.order, error = result.exceptionOrNull()?.message)
            }
        }
    }

    private suspend fun sendStatusNotification(order: StringerOrder, newStatus: String) {
        val uid = currentUserId
        val isStringer = uid == order.stringerUserId || uid == order.stringerId

        // Determine who to notify and what to say
        val (recipientId, title, body) = when (newStatus.lowercase()) {
            "accepted" -> Triple(
                order.buyerId,
                "Order Accepted",
                "${order.stringerName} has accepted your stringing order for ${order.racketName.ifBlank { "your racket" }}."
            )
            "declined" -> Triple(
                order.buyerId,
                "Order Declined",
                "${order.stringerName} has declined your stringing order."
            )
            "stringing" -> Triple(
                order.buyerId,
                "Now Stringing",
                "${order.stringerName} is now stringing your racket."
            )
            "ready_for_pickup" -> Triple(
                order.buyerId,
                "Ready For Pickup",
                "Your racket is ready for pickup from ${order.stringerName}!"
            )
            "picked_up" -> {
                // Buyer picks up — notify the stringer
                val stringerUid = order.stringerUserId.ifBlank { order.stringerId }
                Triple(
                    stringerUid,
                    "Order Picked Up",
                    "${order.buyerName} has picked up their racket."
                )
            }
            else -> return
        }

        if (recipientId.isBlank()) return
        notificationRepo.sendNotification(
            recipientId = recipientId,
            title = title,
            body = body,
            type = "stringer_order",
            extraData = mapOf("stringerOrderId" to order.id)
        )
    }
}
