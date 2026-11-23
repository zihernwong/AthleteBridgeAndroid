package com.athletebridge.android.ui.payments

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.repository.BillingRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentsUiState(
    val unpaidBookings: List<BookingItem> = emptyList(),
    val paidBookings: List<BookingItem> = emptyList(),
    val selectedCoachPayments: Map<String, String> = emptyMap(),
    val selectedCoachName: String = "Coach",
    val isSavingPaymentHandle: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val productDetails: List<ProductDetails> = emptyList(),
    val isLoadingProducts: Boolean = false,
    val purchaseSuccess: Boolean = false
)

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val notificationRepository: NotificationRepository,
    private val coachRepository: CoachRepository,
    private val auth: FirebaseAuth,
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentsUiState())
    val uiState: StateFlow<PaymentsUiState> = _uiState.asStateFlow()

    fun loadClientBookings(clientId: String) {
        viewModelScope.launch {
            bookingRepository.observeClientBookings(clientId).collect { bookings ->
                val confirmed = bookings.filter { (it.status ?: "").lowercase() == "confirmed" }
                _uiState.value = _uiState.value.copy(
                    paidBookings = confirmed.filter { (it.paymentStatus ?: "").lowercase() == "paid" },
                    unpaidBookings = confirmed.filter { (it.paymentStatus ?: "").lowercase() != "paid" }
                )
            }
        }
    }

    fun loadCoachBookings(coachId: String) {
        viewModelScope.launch {
            bookingRepository.observeCoachBookings(coachId).collect { bookings ->
                val isPaid = { b: BookingItem -> (b.paymentStatus ?: "").lowercase() == "paid" }
                _uiState.value = _uiState.value.copy(
                    paidBookings = bookings.filter(isPaid),
                    // Sessions that were called off aren't owed
                    unpaidBookings = bookings.filter { !isPaid(it) && (it.status ?: "").lowercase() !in CLOSED_STATUSES }
                )
            }
        }
    }

    fun notifyCoachOfPayment(coachId: String, bookingId: String, clientName: String) {
        viewModelScope.launch {
            notificationRepository.sendNotification(
                recipientId = coachId,
                title = "Payment Notification",
                body = "$clientName has marked booking as paid. Please confirm",
                bookingId = bookingId,
                senderId = auth.currentUser?.uid,
                // Same type as BookingDetailViewModel.notifyCoachOfPayment, so the push opens the booking
                type = "payment_submitted"
            )
            _uiState.value = _uiState.value.copy(infoMessage = "Coach has been notified of your payment.")
        }
    }

    fun loadCoachPayments(coachId: String, coachName: String) {
        viewModelScope.launch {
            val coach = coachRepository.fetchCoach(coachId)
            _uiState.value = _uiState.value.copy(
                selectedCoachPayments = coach?.payments ?: emptyMap(),
                selectedCoachName = coachName.ifBlank { "Coach" }
            )
        }
    }

    fun savePaymentHandle(
        coachId: String,
        platform: String,
        handle: String,
        existingPayments: Map<String, String>
    ) {
        _uiState.value = _uiState.value.copy(isSavingPaymentHandle = true)
        viewModelScope.launch {
            val updated = existingPayments.toMutableMap()
            updated[platform] = handle
            val result = coachRepository.updatePayments(coachId, updated)
            _uiState.value = _uiState.value.copy(
                isSavingPaymentHandle = false,
                infoMessage = if (result.isSuccess) "Payment handle saved." else null,
                errorMessage = if (result.isFailure) "Failed to save payment handle." else null
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    // ── Google Play Billing ───────────────────────────────────────────────────

    fun loadPlayProducts() {
        _uiState.value = _uiState.value.copy(isLoadingProducts = true)
        viewModelScope.launch {
            val products = billingRepository.queryProducts()
            _uiState.value = _uiState.value.copy(
                productDetails = products,
                isLoadingProducts = false
            )
        }
    }

    fun handlePlayPurchaseResult(uid: String) {
        viewModelScope.launch {
            billingRepository.purchaseState.collect { result ->
                when (result) {
                    is BillingRepository.PurchaseResult.Success -> {
                        // Acknowledge the purchase (required within 3 days or Google refunds it)
                        billingRepository.acknowledgePurchase(result.purchaseToken)
                        // Sync the coach's tier in Firestore based on active Play subscriptions
                        billingRepository.syncSubscriptionTierFromPlay(uid)
                        _uiState.value = _uiState.value.copy(purchaseSuccess = true)
                        billingRepository.clearPurchaseState()
                    }
                    is BillingRepository.PurchaseResult.Error -> {
                        _uiState.value = _uiState.value.copy(errorMessage = result.message)
                        billingRepository.clearPurchaseState()
                    }
                    is BillingRepository.PurchaseResult.Cancelled -> {
                        billingRepository.clearPurchaseState()
                    }
                    null -> {}
                }
            }
        }
    }

    suspend fun launchPlayPurchase(activity: Activity, productDetails: ProductDetails): BillingResult {
        return billingRepository.launchPurchaseFlow(activity, productDetails)
    }

    /**
     * Subscribe to a Play product end-to-end: re-query products if they haven't loaded yet,
     * launch the Google Play purchase sheet, and surface a readable error for every failure
     * instead of silently doing nothing.
     */
    fun subscribeTo(activity: Activity, productId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingProducts = true, errorMessage = null)

            var product = _uiState.value.productDetails.firstOrNull { it.productId == productId }
            if (product == null) {
                val products = billingRepository.queryProducts()
                _uiState.value = _uiState.value.copy(productDetails = products)
                product = products.firstOrNull { it.productId == productId }
            }
            _uiState.value = _uiState.value.copy(isLoadingProducts = false)

            if (product == null) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Google Play couldn't load this subscription. " +
                        "Make sure you're signed in to the Play Store and the app was installed from Google Play, then try again."
                )
                return@launch
            }

            val result = billingRepository.launchPurchaseFlow(activity, product)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _uiState.value = _uiState.value.copy(errorMessage = billingErrorMessage(result.responseCode))
            }
        }
    }

    private fun billingErrorMessage(responseCode: Int): String = when (responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play billing isn't available on this device. Sign in to the Play Store and try again."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            "This subscription isn't available in your region yet."
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
            "You already have an active subscription. Use Manage Billing to change plans."
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
            "Couldn't reach Google Play. Check your connection and try again."
        else -> "Google Play checkout couldn't start (code $responseCode). Please try again."
    }

    fun clearPurchaseSuccess() {
        _uiState.value = _uiState.value.copy(purchaseSuccess = false)
    }

    private companion object {
        val CLOSED_STATUSES = setOf("cancelled", "canceled", "rejected", "declined", "declined_by_client")
    }
}
